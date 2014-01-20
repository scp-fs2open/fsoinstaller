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

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.fsoinstaller.common.BaseURL;
import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.common.InstallerNode.HashTriple;
import com.fsoinstaller.common.InstallerNode.InstallUnit;
import com.fsoinstaller.common.InstallerNode.RenamePair;
import com.fsoinstaller.internet.Connector;
import com.fsoinstaller.internet.Downloader;
import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.utils.CollapsiblePanel;
import com.fsoinstaller.utils.IOUtils;
import com.fsoinstaller.utils.KeyPair;
import com.fsoinstaller.utils.Logger;

import static com.fsoinstaller.wizard.GUIConstants.*;


public class InstallItem extends JPanel
{
	private static final Logger logger = Logger.getLogger(InstallItem.class);
	
	private final InstallerNode node;
	private final List<ChangeListener> listenerList;
	
	private final JProgressBar overallBar;
	private final StoplightPanel stoplightPanel;
	private final Map<KeyPair<InstallUnit, String>, DownloadPanel> downloadPanelMap;
	
	private Future<Void> overallInstallTask;
	private List<String> installResults;
	
	public InstallItem(InstallerNode node)
	{
		super();
		this.node = node;
		this.listenerList = new CopyOnWriteArrayList<ChangeListener>();
		
		setBorder(BorderFactory.createEmptyBorder(SMALL_MARGIN, SMALL_MARGIN, SMALL_MARGIN, SMALL_MARGIN));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		overallBar = new JProgressBar(0, 100);
		overallBar.setIndeterminate(true);
		overallBar.setString("Installing...");
		overallBar.setStringPainted(true);
		
		stoplightPanel = new StoplightPanel((int) overallBar.getPreferredSize().getHeight());
		
		// these variables will only ever be accessed from the event thread, so they are thread safe
		overallInstallTask = null;
		installResults = new ArrayList<String>();
		
		JPanel progressPanel = new JPanel();
		progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.X_AXIS));
		progressPanel.add(overallBar);
		progressPanel.add(Box.createHorizontalStrut(GUIConstants.SMALL_MARGIN));
		//progressPanel.add(cancelButton);
		//progressPanel.add(Box.createHorizontalStrut(GUIConstants.SMALL_MARGIN));
		progressPanel.add(stoplightPanel);
		
		JPanel headerPanel = new JPanel(new BorderLayout(0, GUIConstants.SMALL_MARGIN));
		headerPanel.add(new JLabel(node.getName()), BorderLayout.NORTH);
		headerPanel.add(progressPanel, BorderLayout.CENTER);
		
		JPanel contentsPanel;
		Map<KeyPair<InstallUnit, String>, DownloadPanel> tempMap = new HashMap<KeyPair<InstallUnit, String>, DownloadPanel>();
		
		// we probably have some install units...
		if (!node.getInstallList().isEmpty())
		{
			JPanel downloadsPanel = new JPanel();
			downloadsPanel.setLayout(new BoxLayout(downloadsPanel, BoxLayout.Y_AXIS));
			
			// add as many panels as we have download items for
			for (InstallUnit install: node.getInstallList())
			{
				for (String file: install.getFileList())
				{
					KeyPair<InstallUnit, String> key = new KeyPair<InstallUnit, String>(install, file);
					if (tempMap.containsKey(key))
					{
						logger.error("Duplicate key found for mod '" + node.getName() + "', file '" + file + "'!");
						continue;
					}
					
					DownloadPanel panel = new DownloadPanel();
					downloadsPanel.add(panel);
					tempMap.put(key, panel);
				}
			}
			
			// put them as children under the main progress bar
			contentsPanel = new CollapsiblePanel(headerPanel, downloadsPanel);
			((CollapsiblePanel) contentsPanel).setCollapsed(true);
		}
		// no install units, so just add the panel without any children
		else
		{
			contentsPanel = headerPanel;
		}
		
		downloadPanelMap = Collections.unmodifiableMap(tempMap);
		
		add(contentsPanel);
		add(Box.createGlue());
	}
	
	public void addCompletionListener(ChangeListener listener)
	{
		listenerList.add(listener);
	}
	
	public void removeCompletionListener(ChangeListener listener)
	{
		listenerList.remove(listener);
	}
	
	protected void fireCompletion()
	{
		setIndeterminate(false);
		setPercentComplete(100);
		
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				ChangeEvent event = null;
				
				for (ChangeListener listener: listenerList)
				{
					if (event == null)
						event = new ChangeEvent(node);
					
					listener.stateChanged(event);
				}
			}
		});
	}
	
	public void start()
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		// and now we queue up our big task that handles this whole node
		overallInstallTask = FreeSpaceOpenInstaller.getInstance().getExecutorService().submit(new Callable<Void>()
		{
			public Void call()
			{
				File modFolder;
				
				// handle create, delete, and rename
				try
				{
					modFolder = performSetupTasks();
				}
				catch (SecurityException se)
				{
					logger.error(node.getName() + "Encountered a security exception when processing setup tasks", se);
					modFolder = null;
				}
				
				// if setup didn't work, can't continue
				if (modFolder == null)
				{
					logResult(node.getName() + ": Mod setup failed.");
					
					setSuccess(false);
					fireCompletion();
					return null;
				}
				
				// prepare progress bar for tracking installation units
				setPercentComplete(0);
				setIndeterminate(false);
				
				// now we are about to download stuff
				boolean success = performInstallTasks(modFolder);
				if (!success || Thread.currentThread().isInterrupted())
				{
					logResult(node.getName() + ": Installation failed.");
					
					setSuccess(false);
					fireCompletion();
					return null;
				}
				
				// maybe change the progress bar look agaain
				if (!node.getHashList().isEmpty())
					setIndeterminate(true);
				
				// now hash the files we installed
				success = performHashTasks(modFolder);
				if (!success || Thread.currentThread().isInterrupted())
				{
					logResult(node.getName() + ": Hash verification failed.");
					
					setSuccess(false);
					fireCompletion();
					return null;
				}
				
				setText("Done!");
				
				setSuccess(true);
				fireCompletion();
				return null;
			}
		});
	}
	
	public void cancel()
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		// cancel all downloads
		for (DownloadPanel panel: downloadPanelMap.values())
		{
			Downloader d = panel.getDownloader();
			if (d != null)
				d.cancel();
		}
		
		// cancel the current task
		overallInstallTask.cancel(true);
	}
	
	/**
	 * Perform the preliminary installation tasks for this node (create, delete,
	 * rename).
	 * 
	 * @throws SecurityException if e.g. we are not allowed to create a folder
	 *         or download files to it
	 */
	private File performSetupTasks() throws SecurityException
	{
		File installDir = Configuration.getInstance().getApplicationDir();
		String nodeName = node.getName();
		
		logger.info(nodeName + ": Starting processing");
		setText("Setting up the mod...");
		
		// create the folder for this mod, if it has one
		File folder;
		String folderName = node.getFolder();
		if (folderName == null || folderName.length() == 0 || folderName.equals("/") || folderName.equals("\\"))
		{
			logger.debug(nodeName + ": This node has no folder; using application folder instead");
			folder = installDir;
		}
		else
		{
			logger.info(nodeName + ": Creating folder '" + folderName + "'");
			folder = new File(installDir, folderName);
			if (!folder.exists() && !folder.mkdir())
			{
				logger.error(nodeName + ": Unable to create the '" + folderName + "' folder!");
				return null;
			}
		}
		
		if (!node.getDeleteList().isEmpty())
		{
			logger.info(nodeName + ": Processing DELETE items");
			setText("Deleting old files...");
			
			// delete what we need to
			for (String delete: node.getDeleteList())
			{
				logger.debug(nodeName + ": Deleting '" + delete + "'");
				File file = new File(installDir, delete);
				if (file.exists())
				{
					if (file.isDirectory())
						logger.debug(nodeName + ": Cannot delete '" + delete + "'; deleting directories is not supported at this time");
					else if (!file.delete())
					{
						logger.error(nodeName + ": Unable to delete '" + delete + "'!");
						return null;
					}
				}
			}
		}
		
		if (!node.getRenameList().isEmpty())
		{
			logger.info(nodeName + ": Processing RENAME items");
			setText("Renaming files...");
			
			// rename what we need to
			for (RenamePair rename: node.getRenameList())
			{
				logger.debug(nodeName + ": Renaming '" + rename.getFrom() + "' to '" + rename.getTo() + "'");
				File from = new File(installDir, rename.getFrom());
				File to = new File(installDir, rename.getTo());
				if (!from.exists())
					logger.debug(nodeName + ": Cannot rename '" + rename.getFrom() + "'; it does not exist");
				else if (to.exists())
					logger.debug(nodeName + ": Cannot rename '" + rename.getFrom() + "' to '" + rename.getTo() + "'; the latter already exists");
				else if (!from.renameTo(to))
				{
					logger.error(nodeName + ": Unable to rename '" + rename.getFrom() + "' to '" + rename.getTo() + "'!");
					return null;
				}
			}
		}
		
		return folder;
	}
	
	/**
	 * Perform the main installation tasks for this node.
	 */
	private boolean performInstallTasks(final File modFolder)
	{
		final String nodeName = node.getName();
		
		if (!node.getInstallList().isEmpty())
		{
			logger.info(nodeName + ": Processing INSTALL items");
			setText("Installing files...");
			
			final Connector connector = (Connector) Configuration.getInstance().getSettings().get(Configuration.CONNECTOR_KEY);
			ExecutorService service = FreeSpaceOpenInstaller.getInstance().getExecutorService();
			
			final int totalTasks = downloadPanelMap.keySet().size();
			final AtomicInteger successes = new AtomicInteger(0);
			final AtomicInteger completions = new AtomicInteger(0);
			final CountDownLatch latch = new CountDownLatch(totalTasks);
			
			// these could be files to download, or they could later be files to extract
			for (InstallUnit install: node.getInstallList())
			{
				// try mirrors in random order
				List<BaseURL> tempURLs = install.getBaseURLList();
				if (tempURLs.size() > 1)
				{
					tempURLs = new ArrayList<BaseURL>(tempURLs);
					Collections.shuffle(tempURLs);
				}
				final List<BaseURL> urls = tempURLs;
				
				// install all files for the unit
				for (final String file: install.getFileList())
				{
					final DownloadPanel downloadPanel = downloadPanelMap.get(new KeyPair<InstallUnit, String>(install, file));
					
					// submit a task for this file
					service.submit(new Callable<Void>()
					{
						public Void call()
						{
							// this technique will attempt to perform the installation, record whether it is successful,
							// and then signal its completion via the countdown latch
							try
							{
								// first do the installation
								boolean success = installOne(nodeName, connector, modFolder, urls, file, downloadPanel);
								
								// next update the progress bar
								setRatioComplete(completions.incrementAndGet() / ((double) totalTasks));
								
								// record whether this one worked
								if (success)
								{
									successes.incrementAndGet();
									logger.info(nodeName + ": Downloaded '" + file + "'");
								}
							}
							finally
							{
								latch.countDown();
							}
							return null;
						}
					});
				}
			}
			
			// wait until all tasks have finished
			try
			{
				latch.await();
			}
			catch (InterruptedException ie)
			{
				logger.error("Thread was interrupted while waiting for downloads to complete!", ie);
				Thread.currentThread().interrupt();
				return false;
			}
			
			// check success or failure
			return (successes.get() == totalTasks);
		}
		// nothing to install, so obviously we were successful
		else
		{
			return true;
		}
	}
	
	/**
	 * Perform hash validation for this node.
	 */
	private boolean performHashTasks(File modFolder)
	{
		String nodeName = node.getName();
		
		if (!node.getHashList().isEmpty())
		{
			logger.info(nodeName + ": Processing HASH items");
			setText("Computing hash values...");
			
			for (HashTriple hash: node.getHashList())
			{
				String algorithm = hash.getType().toUpperCase();
				if (algorithm.equals("SHA1"))
					algorithm = "SHA-1";
				else if (algorithm.equals("SHA256"))
					algorithm = "SHA-256";
				
				// get the hash processor, provided by Java
				MessageDigest digest;
				try
				{
					digest = MessageDigest.getInstance(algorithm);
				}
				catch (NoSuchAlgorithmException nsae)
				{
					logger.error(nodeName + ": Unable to compute hash; '" + algorithm + "' is not a recognized algorithm!", nsae);
					continue;
				}
				
				// find the file to hash
				File fileToHash = new File(modFolder, hash.getFilename());
				if (!fileToHash.exists())
				{
					logger.debug(nodeName + ": Cannot compute hash for '" + hash.getFilename() + "'; it does not exist");
					continue;
				}
				
				// hash it
				String computedHash;
				try
				{
					computedHash = IOUtils.computeHash(digest, fileToHash);
				}
				catch (IOException ioe)
				{
					logger.error(nodeName + ": There was a problem computing the hash...", ioe);
					continue;
				}
				
				// compare it
				if (!hash.getHash().equalsIgnoreCase(computedHash))
				{
					logger.error(nodeName + ": Computed hash value of '" + computedHash + "' does not match required hash value of '" + hash.getHash() + "'!");
					return false;
				}
			}
		}
		
		return true;
	}
	
	private boolean installOne(String nodeName, Connector connector, File modFolder, List<BaseURL> baseURLList, String file, DownloadPanel downloadPanel)
	{
		logger.info(nodeName + ": installing '" + file + "'");
		
		// try all URLs supplied
		for (BaseURL baseURL: baseURLList)
		{
			logger.debug(nodeName + ": Obtaining URL");
			URL url;
			try
			{
				url = baseURL.toURL(file);
			}
			catch (MalformedURLException murle)
			{
				logger.error(nodeName + ": Bad URL '" + baseURL.toString() + file + "'", murle);
				continue;
			}
			
			logger.debug(nodeName + ": Beginning download of '" + file + "'");
			Downloader downloader = new Downloader(connector, url, modFolder);
			downloadPanel.setDownloader(downloader);
			boolean success = downloader.download();
			
			// did it work?
			if (success)
			{
				logger.debug(nodeName + ": Completed download of '" + file + "'");
				return true;
			}
		}
		
		logger.debug(nodeName + ": All mirror sites for '" + file + "' failed!");
		return false;
	}
	
	private void logResult(final String message)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				installResults.add(message);
			}
		});
	}
	
	public List<String> getInstallResults()
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		return installResults;
	}
	
	public void setIndeterminate(final boolean indeterminate)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				overallBar.setIndeterminate(indeterminate);
			}
		});
	}
	
	public void setSuccess(final boolean success)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				if (success)
					stoplightPanel.setSuccess();
				else
					stoplightPanel.setFailure();
			}
		});
	}
	
	public void setPercentComplete(int percent)
	{
		if (percent < 0)
			percent = 0;
		if (percent > 100)
			percent = 100;
		
		final int _percent = percent;
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				overallBar.setValue(_percent);
			}
		});
	}
	
	public void setRatioComplete(double ratio)
	{
		setPercentComplete((int) (ratio * 100.0));
	}
	
	public void setText(final String text)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				overallBar.setString(text);
			}
		});
	}
}
