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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.utils.IOUtils;
import com.fsoinstaller.utils.InstallerUtils;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.MiscUtils;
import com.fsoinstaller.utils.OperatingSystem;
import com.fsoinstaller.utils.SwingUtils;
import com.fsoinstaller.utils.ThreadSafeJOptionPane;
import com.l2fprod.common.swing.JDirectoryChooser;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


/**
 * This used to be a private static class of ConfigPage, but it's complicated
 * enough that it really deserves its own class.
 * 
 * @author Goober5000
 */
class DirectoryTask implements Callable<Void>
{
	private static final Logger logger = Logger.getLogger(DirectoryTask.class);
	
	private final JFrame activeFrame;
	private final String directoryText;
	private final Runnable runWhenReady;
	@SuppressWarnings("unused")
	private final Runnable exitRunnable;
	
	private final Configuration configuration;
	private final Map<String, Object> settings;
	
	public DirectoryTask(JFrame activeFrame, String directoryText, Runnable runWhenReady, Runnable exitRunnable)
	{
		this.activeFrame = activeFrame;
		this.directoryText = directoryText;
		this.runWhenReady = runWhenReady;
		this.exitRunnable = exitRunnable;
		
		// Configuration and its maps are thread-safe
		this.configuration = Configuration.getInstance();
		this.settings = configuration.getSettings();
	}
	
	public Void call()
	{
		// things that we'll need to save after this task
		boolean installOpenAL = false;
		File gogInstallPackage = null;
		File steamInstallLocation = null;
		String rootVPHash = null;
		
		final File destinationDir = configuration.getApplicationDir();
		
		logger.info("Checking for read access...");
		
		// check that we can read from this directory: contents will be null if an I/O error occurred
		File[] contents = destinationDir.listFiles();
		if (contents == null)
		{
			ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("readCheckFailed"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
			return null;
		}
		
		logger.info("Checking for write and delete access...");
		
		// check that we can write to this directory
		String unique = "installer_" + InstallerUtils.UUID() + ".tmp";
		File writingTest = new File(destinationDir, unique);
		try
		{
			writingTest.createNewFile();
		}
		catch (IOException ioe)
		{
			logger.error("Creating a temporary file '" + unique + "' failed", ioe);
			ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("writeCheckFailed"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
			return null;
		}
		if (!writingTest.delete())
		{
			logger.error("Deleting a temporary file '" + unique + "' failed");
			ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("deleteCheckFailed"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
			return null;
		}
		
		// see if OpenAL needs to be installed
		if (!MiscUtils.loadOpenAL())
		{
			// we can install it on Windows, but not other OSes
			if (OperatingSystem.getHostOS() == OperatingSystem.WINDOWS)
			{
				int result = ThreadSafeJOptionPane.showConfirmDialog(activeFrame, XSTR.getString("promptToInstallOpenAL"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				if (result == JOptionPane.YES_OPTION)
					installOpenAL = true;
			}
			else
			{
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, XSTR.getString("openALIsNeeded"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
			}
		}
		
		// if we need FS2 installed, make sure that it is (or that user has been warned)
		if (configuration.requiresFS2())
		{
			logger.info("Checking for root_fs2.vp...");
			
			// the best way to do this is probably to check for the presence of root_fs2
			boolean exists = false;
			for (File file: contents)
			{
				if (file.isDirectory())
					continue;
				
				String name = file.getName();
				if (name.equalsIgnoreCase("root_fs2.vp"))
				{
					// let's hash it, so that we can use the hash for the v1.2 check later
					MessageDigest md5Digest;
					try
					{
						md5Digest = MessageDigest.getInstance("MD5");
						rootVPHash = IOUtils.computeHash(md5Digest, file);
					}
					catch (NoSuchAlgorithmException nsae)
					{
						logger.error("Impossible error: The MD5 hash should exist in every Java installation!", nsae);
					}
					catch (FileNotFoundException fnfe)
					{
						logger.error("Impossible error: we just checked for the existence of root_fs2.vp!", fnfe);
					}
					catch (IOException ioe)
					{
						logger.warn("There was an error computing the hash of root_fs2.vp!", ioe);
					}
					
					// we found root_fs2.vp
					exists = true;
					logger.debug("MD5 hash for root_fs2.vp: " + rootVPHash);
					break;
				}
			}
			
			// if it doesn't exist, we need to do something about that
			if (!exists)
			{
				logger.debug("Showing retailFS2NotFound prompt");
				int result = ThreadSafeJOptionPane.showCustomOptionDialog(activeFrame, XSTR.getString("retailFS2NotFound"), 0, XSTR.getString("optionInstallGOG"), XSTR.getString("optionWrongDirectory"), XSTR.getString("optionContinueAnyway"));
				logger.debug("User selected " + result);
				
				// find out what was decided
				if (result < 0 || result == 1)
				{
					// this is basically a cancel; go back and change the dir
					return null;
				}
				// add the GOG install "mod"
				else if (result == 0)
				{
					// figure out where we're installing from (we will add the actual "mod" in InstallPage)
					gogInstallPackage = SwingUtils.promptForFile(activeFrame, XSTR.getString("chooseGOGPackageTitle"), configuration.getApplicationDir(), "exe", XSTR.getString("exeFilesFilter"));
					logger.debug("GOG install package: " + gogInstallPackage.getAbsolutePath());
				}
				// add the Steam copy "mod"
				// (this is now disabled)
				else if (result == 1000)
				{
					// figure out where we're copying from (we will add the actual "mod" in InstallPage)
					final AtomicReference<File> fileResult = new AtomicReference<File>(null);
promptForSteamFS2:	while (true)
					{
						SwingUtils.invokeAndWait(new Runnable()
						{
							public void run()
							{
								// create a file chooser
								JDirectoryChooser chooser = new JDirectoryChooser();
								chooser.setCurrentDirectory(destinationDir);
								chooser.setDialogTitle(XSTR.getString("chooseDirTitle"));
								chooser.setShowingCreateDirectory(false);
								
								// display it
								int result = chooser.showDialog(activeFrame, XSTR.getString("OK"));
								if (result == JDirectoryChooser.APPROVE_OPTION)
									fileResult.set(chooser.getSelectedFile());
							}
						});
						steamInstallLocation = fileResult.get();
						if (steamInstallLocation == null)
							break promptForSteamFS2;
						
						// check that we can read from this directory: steamContents will be null if an I/O error occurred
						File[] steamContents = steamInstallLocation.listFiles();
						if (steamContents == null)
						{
							// we need to wait here because the directory chooser uses invokeAndWait
							SwingUtils.invokeAndWait(new Runnable()
							{
								public void run()
								{
									JOptionPane.showMessageDialog(activeFrame, XSTR.getString("readCheckFailed2"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
								}
							});
						}
						else
						{
							for (File file: steamContents)
							{
								if (file.isDirectory())
									continue;
								
								String name = file.getName();
								if (name.equalsIgnoreCase("root_fs2.vp"))
									break promptForSteamFS2;
							}
							
							// we need to wait here because the directory chooser uses invokeAndWait
							SwingUtils.invokeAndWait(new Runnable()
							{
								public void run()
								{
									JOptionPane.showMessageDialog(activeFrame, XSTR.getString("copyFS2NotFound"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
								}
							});
						}
						
						// FS2 not found in this location, so loop again
						fileResult.set(null);
						steamInstallLocation = null;
					}
				}
				// continue anyway = no special treatment
			}
		}
		
		// interruption check
		if (Thread.currentThread().isInterrupted())
			return null;
		
		logger.info("Checking for extra VPs in the directory");
		
		// check for spurious VPs
		// (note: allowed VPs are in lowercase)
		List<String> allowedVPs = configuration.getAllowedVPs();
		List<String> extraVPs = new ArrayList<String>();
		for (File file: contents)
		{
			if (file.isDirectory())
				continue;
			
			String name = file.getName();
			if (!name.endsWith(".vp"))
				continue;
			
			if (allowedVPs.contains(name.toLowerCase()))
				logger.debug("ALLOWED: " + name);
			else
			{
				extraVPs.add(name);
				logger.debug("DISALLOWED: " + name);
			}
		}
		
		if (!extraVPs.isEmpty())
		{
			StringBuilder message = new StringBuilder(XSTR.getString("foundExtraVPs1"));
			message.append("\n\n");
			for (String name: extraVPs)
			{
				message.append(name);
				message.append("\n");
			}
			message.append("\n");
			message.append(XSTR.getString("foundExtraVPs2"));
			
			// prompt to continue
			int result = ThreadSafeJOptionPane.showConfirmDialog(activeFrame, message, FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.YES_NO_OPTION);
			if (result != JOptionPane.YES_OPTION)
				return null;
		}
		
		// final interruption check for this task
		if (Thread.currentThread().isInterrupted())
			return null;
		
		final boolean _installOpenAL = installOpenAL;
		final File _gogInstallPackage = gogInstallPackage;
		final File _steamInstallLocation = steamInstallLocation;
		final String _rootVPHash = rootVPHash;
		
		// directory is good to go, so save our settings
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				@SuppressWarnings("unchecked")
				Set<String> checked = (Set<String>) settings.get(Configuration.CHECKED_DIRECTORIES_KEY);
				checked.add(directoryText);
				
				if (_installOpenAL)
					settings.put(Configuration.ADD_OPENAL_INSTALL_KEY, Boolean.TRUE);
				
				if (_gogInstallPackage != null)
					settings.put(Configuration.GOG_INSTALL_PACKAGE_KEY, _gogInstallPackage);
				
				if (_steamInstallLocation != null)
					settings.put(Configuration.STEAM_INSTALL_LOCATION_KEY, _steamInstallLocation);
				
				if (_rootVPHash != null)
					settings.put(Configuration.ROOT_FS2_VP_HASH_KEY, _rootVPHash);
			}
		});
		
		// checking completed!
		logger.info("Done with DirectoryTask!");
		EventQueue.invokeLater(runWhenReady);
		return null;
	}
}
