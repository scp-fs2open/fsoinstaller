/*
 * This file is part of the FreeSpace Open Installer
 * Copyright (C) 2010 The FreeSpace 2 Source Code Project
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package com.fsoinstaller.wizard;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.common.InstallerNodeParseException;
import com.fsoinstaller.internet.Connector;
import com.fsoinstaller.internet.Downloader;
import com.fsoinstaller.internet.InvalidProxyException;
import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.utils.IOUtils;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.MiscUtils;
import com.fsoinstaller.utils.ThreadSafeJOptionPane;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


/**
 * This used to be a private static class of ConfigPage, but it's complicated
 * enough that it really deserves its own class.
 * 
 * @author Goober5000
 */
class SuperValidationTask implements Callable<Void>
{
	private static final Logger logger = Logger.getLogger(SuperValidationTask.class);
	
	private final JFrame activeFrame;
	private final String directoryText;
	private final boolean usingProxy;
	private final String hostText;
	private final String portText;
	private final Runnable runWhenReady;
	private final Runnable exitRunnable;
	
	private final Configuration configuration;
	private final Map<String, Object> settings;
	
	public SuperValidationTask(JFrame activeFrame, String directoryText, boolean usingProxy, String hostText, String portText, Runnable runWhenReady, Runnable exitRunnable)
	{
		this.activeFrame = activeFrame;
		this.directoryText = directoryText;
		this.usingProxy = usingProxy;
		this.hostText = hostText;
		this.portText = portText;
		this.runWhenReady = runWhenReady;
		this.exitRunnable = exitRunnable;
		
		// Configuration and its maps are thread-safe
		this.configuration = Configuration.getInstance();
		this.settings = configuration.getSettings();
	}
	
	/**
	 * Cleans up the validation task if there is an interrupt in the first
	 * phase.
	 */
	private Void cleanupPhaseA()
	{
		logger.info("Rolling back Phase A validation");
		
		settings.remove(Configuration.MOD_URLS_KEY);
		settings.remove(Configuration.REMOTE_VERSION_KEY);
		
		return null;
	}
	
	/**
	 * Cleans up the validation task if there is an interrupt in the second
	 * phase.
	 */
	private Void cleanupPhaseB()
	{
		logger.info("Rolling back Phase B validation");
		
		settings.remove(Configuration.MOD_NODES_KEY);
		
		return null;
	}
	
	public Void call()
	{
		logger.info("Validating user input...");
		
		// check directory
		File destinationDir = MiscUtils.validateApplicationDir(directoryText);
		if (destinationDir == null)
		{
			ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("directoryInvalid"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
			return null;
		}
		
		// create directory that doesn't exist
		if (!destinationDir.exists())
		{
			// prompt to create it
			int result = ThreadSafeJOptionPane.showConfirmDialog(activeFrame, XSTR.getString("directoryNotPresent"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.YES_NO_OPTION);
			if (result != JOptionPane.YES_OPTION)
				return null;
			
			logger.info("Attempting to create directory/ies...");
			
			// attempt to create it
			if (!destinationDir.mkdirs())
			{
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("directoryNotCreated"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
				return null;
			}
			
			logger.info("Directory creation successful.");
		}
		
		// check proxy
		Proxy proxy = null;
		String host = null;
		int port = -1;
		if (usingProxy)
		{
			logger.info("Checking proxy...");
			
			try
			{
				host = hostText;
				port = Integer.parseInt(portText);
				proxy = Connector.createProxy(host, port);
			}
			catch (NumberFormatException nfe)
			{
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("proxyPortParseFailed"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
				return null;
			}
			catch (InvalidProxyException ipe)
			{
				logger.error("Proxy could not be created!", ipe);
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("proxyInvalid"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
				return null;
			}
			
			// good to go
			settings.put(Configuration.PROXY_KEY, proxy);
		}
		
		logger.info("Validation succeeded!");
		
		// save our settings
		configuration.setApplicationDir(destinationDir);
		configuration.setProxyInfo(host, port);
		configuration.saveUserProperties();
		
		Connector connector = new Connector(proxy);
		settings.put(Configuration.CONNECTOR_KEY, connector);
		
		// only check for the installer version if we haven't checked already
		// and also skip the check if we're overriding mod nodes, since this will allow offline installations in the future
		if ( !settings.containsKey(Configuration.REMOTE_VERSION_KEY)
			&& !settings.containsKey(Configuration.OVERRIDE_INSTALL_MOD_NODES_KEY) )
		{
			logger.info("Checking installer version...");
			logger.info("This version is " + FreeSpaceOpenInstaller.INSTALLER_VERSION);
			
			File tempVersion;
			File tempFilenames;
			File tempBasicConfig;
			try
			{
				tempVersion = File.createTempFile("fsoinstaller_version", null);
				tempFilenames = File.createTempFile("fsoinstaller_filenames", null);
				tempBasicConfig = File.createTempFile("fsoinstaller_basicconfig", null);
			}
			catch (IOException ioe)
			{
				logger.error("Error creating temporary file!", ioe);
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("couldNotCreateTempFile"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
				return null;
			}
			tempVersion.deleteOnExit();
			tempFilenames.deleteOnExit();
			tempBasicConfig.deleteOnExit();
			
			String maxVersion = "0.0.0.0";
			String maxVersionURL = null;
			
			// put URLs in random order
			List<String> homeURLs = FreeSpaceOpenInstaller.INSTALLER_HOME_URLs;
			if (homeURLs.size() > 1)
			{
				List<String> tempURLs = new ArrayList<String>(homeURLs);
				Collections.shuffle(tempURLs);
				homeURLs = tempURLs;
			}
			
			// check all URLs for version and filename info
			for (String url: homeURLs)
			{
				logger.debug("Accessing version info from " + url + "...");
				
				// assemble URLs
				URL versionURL;
				URL filenameURL;
				URL basicURL;
				try
				{
					versionURL = new URL(url + "version.txt");
					filenameURL = new URL(url + "filenames.txt");
					basicURL = new URL(url + "basic_config.txt");
				}
				catch (MalformedURLException murle)
				{
					logger.error("Something went wrong with the URL!", murle);
					continue;
				}
				
				// download version information
				Downloader tempVersionDownloader = new Downloader(connector, versionURL, tempVersion);
				if (tempVersionDownloader.download())
				{
					List<String> versionLines = IOUtils.readTextFileCleanly(tempVersion);
					if (!versionLines.isEmpty())
					{
						String thisVersion = versionLines.get(0);
						logger.info("Version at this URL is " + thisVersion);
						
						// get the information from the highest version available
						if (MiscUtils.compareVersions(thisVersion, maxVersion) > 0)
						{
							// get file names
							Downloader tempFilenamesDownloader = new Downloader(connector, filenameURL, tempFilenames);
							if (tempFilenamesDownloader.download())
							{
								List<String> filenameLines = IOUtils.readTextFileCleanly(tempFilenames);
								if (!filenameLines.isEmpty())
								{
									maxVersion = thisVersion;
									maxVersionURL = versionLines.get(1);
									
									// try to get basic configuration too, but this can be optional (sort of)
									Downloader tempBasicConfigDownloader = new Downloader(connector, basicURL, tempBasicConfig);
									if (tempBasicConfigDownloader.download())
									{
										List<String> basicLines = IOUtils.readTextFileCleanly(tempBasicConfig);
										
										// strip empty/blank lines
										Iterator<String> ii = basicLines.iterator();
										while (ii.hasNext())
											if (ii.next().trim().length() == 0)
												ii.remove();
										
										if (!basicLines.isEmpty())
											settings.put(Configuration.BASIC_CONFIG_MODS_KEY, basicLines);
									}
									else if (Thread.currentThread().isInterrupted())
										return cleanupPhaseA();
									
									// save our settings... save REMOTE_VERSION_KEY last because it is tested in the if() blocks
									settings.put(Configuration.MOD_URLS_KEY, filenameLines);
									settings.put(Configuration.REMOTE_VERSION_KEY, thisVersion);
								}
							}
							else if (Thread.currentThread().isInterrupted())
								return cleanupPhaseA();
						}
					}
				}
				else if (Thread.currentThread().isInterrupted())
					return cleanupPhaseA();
			}
			
			// make sure we could access version information
			if (!settings.containsKey(Configuration.REMOTE_VERSION_KEY))
			{
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("couldNotGetRemoteVersion"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
				return null;
			}
			
			// we have a version; check if it is more recent than what we're running
			// (this prompt should only ever come up once, because once the version is known, future visits to this page will take the early exit above)
			if (MiscUtils.compareVersions(maxVersion, FreeSpaceOpenInstaller.INSTALLER_VERSION) > 0)
			{
				logger.info("Installer is out-of-date; prompting user to download new version...");
				int result = ThreadSafeJOptionPane.showConfirmDialog(activeFrame, XSTR.getString("promptToDownloadNewVersion"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.YES_NO_OPTION);
				if (result == JOptionPane.YES_OPTION)
				{
					try
					{
						if (connector.browseToURL(new URL(maxVersionURL)))
						{
							// this should close the program
							EventQueue.invokeLater(exitRunnable);
							return null;
						}
					}
					catch (MalformedURLException murle)
					{
						logger.error("Something went wrong with the URL!", murle);
					}
					ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("problemDownloading"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					return null;
				}
			}
		}
		
		// check again if thread is interrupted
		if (Thread.currentThread().isInterrupted())
			return cleanupPhaseA();
		
		// if we are overriding our mod nodes, don't download them from the repository
		if (settings.containsKey(Configuration.OVERRIDE_INSTALL_MOD_NODES_KEY))
		{
			settings.put(Configuration.MOD_NODES_KEY, settings.get(Configuration.OVERRIDE_INSTALL_MOD_NODES_KEY));
			
			// also skip over ChoicePage
			((InstallerGUI) activeFrame).skip(ChoicePage.class);
		}
		// only check for mod information if we haven't checked already
		else if (!settings.containsKey(Configuration.MOD_NODES_KEY))
		{
			logger.info("Downloading mod information...");
			
			@SuppressWarnings("unchecked")
			List<String> urls = (List<String>) settings.get(Configuration.MOD_URLS_KEY);
			if (urls == null || urls.isEmpty())
			{
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("noModsFound"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
				EventQueue.invokeLater(exitRunnable);
				return null;
			}
			
			// parse mod urls into nodes
			List<InstallerNode> modNodes = new ArrayList<InstallerNode>();
			for (String url: urls)
			{
				// create a URL
				URL modURL;
				try
				{
					modURL = new URL(url);
				}
				catch (MalformedURLException murle)
				{
					logger.error("Something went wrong with the URL!", murle);
					continue;
				}
				
				// create a temporary file
				File tempModFile;
				try
				{
					tempModFile = File.createTempFile("fsoinstaller_mod", null);
				}
				catch (IOException ioe)
				{
					logger.error("Error creating temporary file!", ioe);
					ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("couldNotCreateTempFile"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					return null;
				}
				tempModFile.deleteOnExit();
				
				// download it to the temp file
				Downloader tempModFileDownloader = new Downloader(connector, modURL, tempModFile);
				if (!tempModFileDownloader.download())
				{
					if (Thread.currentThread().isInterrupted())
						return cleanupPhaseB();
					
					logger.warn("Could not download mod information from '" + url + "'");
					continue;
				}
				
				// parse it into one or more nodes
				try
				{
					List<InstallerNode> nodes = IOUtils.readInstallFile(tempModFile);
					for (InstallerNode node: nodes)
					{
						modNodes.add(node);
						logger.info("Successfully added " + node.getName());
					}
				}
				catch (FileNotFoundException fnfe)
				{
					logger.error("This is very odd; we can't find the temp file we just created!", fnfe);
				}
				catch (IOException ioe)
				{
					logger.error("This is very odd; there was an error reading the temp file we just created!", ioe);
				}
				catch (InstallerNodeParseException inpe)
				{
					logger.warn("There was an error parsing the mod file at '" + url + "'", inpe);
				}
			}
			
			// check that we have mods
			if (modNodes.isEmpty())
			{
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("noModsFound"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
				EventQueue.invokeLater(exitRunnable);
				return null;
			}
			
			// add to settings
			settings.put(Configuration.MOD_NODES_KEY, modNodes);
		}
		
		// check again if thread is interrupted
		if (Thread.currentThread().isInterrupted())
			return cleanupPhaseB();
		
		// now that we have the mod list, check to see if there is any old version information left over from Turey's installer
		logger.info("Checking for legacy version information...");
		
		// we check this every time because the user could have changed the destination directory
		// (however, the installed versions file should be deleted after the first try, or at least the properties file should contain the keys we need)
		File oldInstallerInfoDir = new File(destinationDir, "temp");
		if (oldInstallerInfoDir.exists() && oldInstallerInfoDir.isDirectory())
		{
			File installedversions = new File(oldInstallerInfoDir, "installedversions.txt");
			if (installedversions.exists())
			{
				// read lines from the installedversions file
				List<String> lines = IOUtils.readTextFileCleanly(installedversions);
				
				// load the version for each node
				@SuppressWarnings("unchecked")
				List<InstallerNode> modNodes = (List<InstallerNode>) settings.get(Configuration.MOD_NODES_KEY);
				for (InstallerNode node: modNodes)
					loadLegacyModVersions(node, lines, configuration.getUserProperties());
				
				// save our properties
				boolean success = configuration.saveUserProperties();
				
				// delete the file, since we don't need it any more
				if (success && !installedversions.delete())
					logger.warn("Could not delete legacy file '" + installedversions.getAbsolutePath() + "'!");
			}
			
			// delete other old files and the folder
			File latest = new File(oldInstallerInfoDir, "latest.txt");
			if (latest.exists() && !latest.delete())
				logger.warn("Could not delete legacy file '" + latest.getAbsolutePath() + "'!");
			File version = new File(oldInstallerInfoDir, "version.txt");
			if (version.exists() && !version.delete())
				logger.warn("Could not delete legacy file '" + version.getAbsolutePath() + "'!");
			File[] filesLeft = oldInstallerInfoDir.listFiles();
			if (filesLeft != null && filesLeft.length == 0 && !oldInstallerInfoDir.delete())
				logger.warn("Could not delete legacy directory '" + oldInstallerInfoDir.getAbsolutePath() + "'!");
		}
		
		// final interruption check for this task
		if (Thread.currentThread().isInterrupted())
			return null;
		
		// validation completed!
		logger.info("Done with SuperValidationTask!");
		EventQueue.invokeLater(runWhenReady);
		return null;
	}
	
	private void loadLegacyModVersions(InstallerNode node, List<String> installedversions_lines, Properties properties)
	{
		// if we have a version already, we don't need to query the legacy version
		if (properties.containsKey(node.getTreePath()))
			return;
		
		// find the version corresponding to this node in the installedversions file
		String version = null;
		Iterator<String> ii = installedversions_lines.iterator();
		while (ii.hasNext())
		{
			// get name matching this node
			if (!ii.next().equalsIgnoreCase("NAME"))
				continue;
			if (!ii.hasNext())
				break;
			if (!ii.next().equals(node.getName()))
				continue;
			// ensure name hasn't provided a version
			if (version != null)
			{
				logger.warn("The installedversions file contains more than one version for the name '" + node.getName() + "'!");
				return;
			}
			
			// get version
			if (!ii.hasNext())
				break;
			if (!ii.next().equalsIgnoreCase("VERSION"))
				continue;
			if (!ii.hasNext())
				break;
			version = ii.next();
		}
		
		// now that we have a version, save it
		if (version != null)
			properties.setProperty(node.getTreePath(), version);
		
		// we need to check all the child nodes as well
		for (InstallerNode child: node.getChildren())
			loadLegacyModVersions(child, installedversions_lines, properties);
	}
}
