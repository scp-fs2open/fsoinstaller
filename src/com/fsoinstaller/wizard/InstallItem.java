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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
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
import com.fsoinstaller.common.InstallerNode.FilePair;
import com.fsoinstaller.common.InstallerNode.HashTriple;
import com.fsoinstaller.common.InstallerNode.InstallUnit;
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
	
	private final List<InstallItem> childItems;
	private int remainingChildren;
	private List<String> installNotes;
	private List<String> installErrors;
	private Future<Void> overallInstallTask;
	private InstallItemState state;
	
	private final Logger modLogger;
	
	public InstallItem(InstallerNode node, Set<String> selectedMods)
	{
		super();
		this.node = node;
		this.listenerList = new CopyOnWriteArrayList<ChangeListener>();
		
		modLogger = Logger.getLogger(InstallItem.class, node.getName());
		
		setBorder(BorderFactory.createEmptyBorder(SMALL_MARGIN, SMALL_MARGIN, SMALL_MARGIN, SMALL_MARGIN));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		overallBar = new JProgressBar(0, 100);
		overallBar.setIndeterminate(true);
		overallBar.setString("Waiting to begin...");
		overallBar.setStringPainted(true);
		
		stoplightPanel = new StoplightPanel((int) overallBar.getPreferredSize().getHeight());
		
		// these variables will only ever be accessed from the event thread, so they are thread safe
		childItems = new ArrayList<InstallItem>();
		remainingChildren = 0;
		installNotes = new ArrayList<String>();
		installErrors = new ArrayList<String>();
		overallInstallTask = null;
		state = InstallItemState.INITIALIZED;
		
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
		
		// we probably have some install units or children...
		JPanel installPanel = new JPanel();
		installPanel.setLayout(new BoxLayout(installPanel, BoxLayout.Y_AXIS));
		
		// add as many panels as we have download items for
		Map<KeyPair<InstallUnit, String>, DownloadPanel> tempMap = new HashMap<KeyPair<InstallUnit, String>, DownloadPanel>();
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
				installPanel.add(panel);
				tempMap.put(key, panel);
			}
		}
		downloadPanelMap = Collections.unmodifiableMap(tempMap);
		
		// add as many children as we have
		for (InstallerNode childNode: node.getChildren())
		{
			// only add a node if it was selected
			if (!selectedMods.contains(childNode.getName()))
				continue;
			
			// add item's GUI (and all its children) to the panel
			final InstallItem childItem = new InstallItem(childNode, selectedMods);
			installPanel.add(childItem);
			
			// keep track of children that have completed (whether success or failure)
			remainingChildren++;
			childItem.addCompletionListener(new ChangeListener()
			{
				public void stateChanged(ChangeEvent e)
				{
					// add any feedback to the output
					installNotes.addAll(childItem.getInstallNotes());
					installErrors.addAll(childItem.getInstallErrors());
					
					remainingChildren--;
					
					// if ALL of the children have completed, this whole item is complete
					if (remainingChildren == 0)
						fireCompletion();
				}
			});
			
			childItems.add(childItem);
		}
		
		JPanel contentsPanel;
		if (installPanel.getComponentCount() > 0)
		{
			// put children under the main progress bar
			contentsPanel = new CollapsiblePanel(headerPanel, installPanel);
			((CollapsiblePanel) contentsPanel).setCollapsed(false);
		}
		// really?  then just add the progress bar by itself
		else
		{
			contentsPanel = headerPanel;
		}
		
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
		modLogger.info("Processing is complete; alerting listeners...");
		
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
		
		// make sure we're okay to start
		if (state == InstallItemState.INITIALIZED)
		{
			modLogger.info("Starting task!");
			state = InstallItemState.RUNNING;
		}
		else if (state == InstallItemState.CANCELLED)
		{
			modLogger.info("Not starting task that was cancelled...");
			return;
		}
		else if (state == InstallItemState.COMPLETED)
		{
			modLogger.info("Not starting task that was completed...");
			return;
		}
		else
		{
			modLogger.error("Cannot install; item's current state is " + state);
			return;
		}
		
		// and now we queue up our big task that handles this whole node
		overallInstallTask = FreeSpaceOpenInstaller.getInstance().submitTask("Install " + node.getName(), new Callable<Void>()
		{
			public Void call()
			{
				try
				{
					File modFolder;
					
					// handle create, delete, rename, and copy
					try
					{
						modFolder = performSetupTasks();
					}
					catch (SecurityException se)
					{
						modLogger.error("Encountered a security exception when processing setup tasks", se);
						logInstallError(node.getName() + ": A Java security exception prevented setup from running.  Check the log file for more details.");
						modFolder = null;
					}
					
					// if setup didn't work, can't continue
					if (modFolder == null || Thread.currentThread().isInterrupted())
					{
						failInstallTree();
						return null;
					}
					modLogger.info("Installing to path: " + modFolder.getAbsolutePath());
					
					// prepare progress bar for tracking installation units
					setPercentComplete(0);
					setIndeterminate(false);
					
					// now we are about to download stuff
					boolean success = performInstallTasks(modFolder);
					if (!success || Thread.currentThread().isInterrupted())
					{
						failInstallTree();
						return null;
					}
					
					// maybe change the progress bar look again
					if (!node.getHashList().isEmpty())
						setIndeterminate(true);
					
					// now hash the files we installed
					success = performHashTasks(modFolder);
					if (!success || Thread.currentThread().isInterrupted())
					{
						failInstallTree();
						return null;
					}
					
					// success: save the version we just installed
					// (the synchronization here is only so that we don't try to write to the file in multiple threads simultaneously)
					String propertyName = node.buildTreeName();
					Configuration configuration = Configuration.getInstance();
					synchronized (configuration)
					{
						configuration.getUserProperties().setProperty(propertyName, node.getVersion());
						configuration.saveUserProperties();
					}
					
					// save any post-installation notes
					if (node.getNote() != null)
						logInstallNote(node.getName() + ": " + node.getNote());
					
					// update GUI
					setSuccess(true);
					setText("Done!");
					setPercentComplete(100);
					
					// kick off next nodes
					startChildren();
					return null;
				}
				catch (RuntimeException re)
				{
					modLogger.error("Unhandled runtime exception!", re);
					logInstallError(node.getName() + ": An unexpected error occurred.  Please check the log file for more details.");
					return null;
				}
			}
		});
	}
	
	public void cancel()
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		InstallItemState oldState = state;
		
		// make sure we're okay to cancel, and make sure we cancel the right way
		if (state == InstallItemState.INITIALIZED)
		{
			modLogger.info("Cancelling task that hasn't started yet!");
			state = InstallItemState.CANCELLED;
			
			// set GUI
			setSuccess(false);
			setIndeterminate(false);
			setText("Cancelled");
		}
		else if (state == InstallItemState.RUNNING)
		{
			modLogger.info("Cancelling running task!");
			state = InstallItemState.CANCELLED;
			
			// set GUI
			setSuccess(false);
			setIndeterminate(false);
			setText("Cancelled");
			
			logInstallError(node.getName() + ": Cancelled by user request.");
			
			// interrupt the running task
			overallInstallTask.cancel(true);
			
			// cancel all downloads
			for (DownloadPanel panel: downloadPanelMap.values())
			{
				Downloader d = panel.getDownloader();
				if (d != null)
					d.cancel();
			}
		}
		else if (state == InstallItemState.CANCELLED)
		{
			modLogger.info("Not cancelling task that was already cancelled...");
		}
		else if (state == InstallItemState.COMPLETED)
		{
			modLogger.info("Not cancelling task that was already completed...");
		}
		else
		{
			modLogger.error("Cannot cancel; item's current state is " + state);
			return;
		}
		
		// if we cancelled a task that was running, failInstallTree will take care of all the children and the completion event;
		// otherwise we will need to continue cancelling the children
		if (oldState != InstallItemState.RUNNING)
		{
			for (InstallItem child: childItems)
				child.cancel();
			
			// don't  fire a redundant completion event
			if (oldState != InstallItemState.CANCELLED && oldState != InstallItemState.COMPLETED)
			{
				// if no children, we are done
				if (childItems.isEmpty())
					fireCompletion();
			}
		}
	}
	
	/**
	 * This should only be called from the main install processing task.
	 */
	private void startChildren()
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				state = InstallItemState.COMPLETED;
				
				for (InstallItem child: childItems)
					child.start();
				
				// if no children, we are done
				if (childItems.isEmpty())
					fireCompletion();
			}
		});
	}
	
	/**
	 * This should only be called from the main install processing task. It's a
	 * program-initiated cancellation rather than a user-initiated cancellation.
	 */
	private void failInstallTree()
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				failInstallTree0(true);
			}
		});
	}
	
	private void failInstallTree0(boolean isTopLevelFailedItem)
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		// set state
		state = InstallItemState.CANCELLED;
		
		// set GUI
		// (don't set text for the top level item)
		setSuccess(false);
		setIndeterminate(false);
		
		if (!isTopLevelFailedItem)
		{
			setText("Parent not installed");
			
			modLogger.info("Parent mod could not be installed; this mod will be skipped!");
			logInstallError(node.getName() + ": Skipped because parent mod was not installed.");
			
			for (DownloadPanel panel: downloadPanelMap.values())
				panel.downloadCancelled(null);
		}
		
		// fail child items
		// (note that at this point, none of the children have started yet)
		for (InstallItem child: childItems)
			child.failInstallTree0(false);
		
		// if no children, we are done
		if (childItems.isEmpty())
			fireCompletion();
	}
	
	/**
	 * Perform the preliminary installation tasks for this node (create, delete,
	 * rename, copy).
	 * 
	 * @throws SecurityException if e.g. we are not allowed to create a folder
	 *         or download files to it
	 */
	private File performSetupTasks() throws SecurityException
	{
		File installDir = Configuration.getInstance().getApplicationDir();
		
		modLogger.info("Starting processing");
		setText("Setting up the mod...");
		
		// create the folder for this mod, if it has one
		File folder;
		String folderName = node.getFolder();
		if (folderName == null || folderName.length() == 0 || folderName.equals("/") || folderName.equals("\\"))
		{
			modLogger.debug("This node has no folder; using application folder instead");
			folder = installDir;
		}
		else
		{
			folder = IOUtils.newFileIgnoreCase(installDir, folderName);
			if (folder.exists())
				modLogger.info("Using folder '" + folderName + "'");
			else
			{
				modLogger.info("Creating folder '" + folderName + "'");
				if (!folder.mkdirs())
				{
					modLogger.error("Unable to create the folder '" + folderName + "'!");
					logInstallError(node.getName() + ": The folder '" + folderName + "' could not be created.");
					return null;
				}
			}
		}
		
		if (!node.getDeleteList().isEmpty())
		{
			modLogger.info("Processing DELETE items");
			setText("Deleting old files...");
			
			// delete what we need to
			for (String delete: node.getDeleteList())
			{
				modLogger.debug("Deleting '" + delete + "'");
				File file = IOUtils.newFileIgnoreCase(folder, delete);
				if (!file.exists())
					modLogger.debug("Cannot delete '" + delete + "'; it does not exist");
				else if (file.isDirectory())
				{
					if (!IOUtils.deleteDirectoryTree(file))
					{
						modLogger.error("Unable to delete the directory '" + delete + "'!");
						logInstallError(node.getName() + ": The directory '" + delete + "' could not be deleted.");
						return null;
					}
				}
				else if (!file.delete())
				{
					modLogger.error("Unable to delete the file '" + delete + "'!");
					logInstallError(node.getName() + ": The file '" + delete + "' could not be deleted.");
					return null;
				}
			}
		}
		
		if (!node.getRenameList().isEmpty())
		{
			modLogger.info("Processing RENAME items");
			setText("Renaming files...");
			
			// rename what we need to
			for (FilePair rename: node.getRenameList())
			{
				modLogger.debug("Renaming '" + rename.getFrom() + "' to '" + rename.getTo() + "'");
				File from = IOUtils.newFileIgnoreCase(folder, rename.getFrom());
				File to = IOUtils.newFileIgnoreCase(folder, rename.getTo());
				if (!from.exists())
					modLogger.debug("Cannot rename '" + rename.getFrom() + "'; it does not exist");
				else if (to.exists())
					modLogger.debug("Cannot rename '" + rename.getFrom() + "' to '" + rename.getTo() + "'; the latter already exists");
				else if (!from.renameTo(to))
				{
					modLogger.error("Unable to rename '" + rename.getFrom() + "' to '" + rename.getTo() + "'!");
					logInstallError(node.getName() + ": The file '" + rename.getFrom() + "' could not be renamed to '" + rename.getTo() + "'.");
					return null;
				}
			}
		}
		
		if (!node.getCopyList().isEmpty())
		{
			modLogger.info("Processing COPY items");
			setText("Copying files...");
			
			// copy what we need to
			for (FilePair copy: node.getCopyList())
			{
				modLogger.debug("Copying '" + copy.getFrom() + "' to '" + copy.getTo() + "'");
				File from = IOUtils.newFileIgnoreCase(folder, copy.getFrom());
				File to = IOUtils.newFileIgnoreCase(folder, copy.getTo());
				if (!from.exists())
					modLogger.debug("Cannot copy '" + copy.getFrom() + "'; it does not exist");
				else if (to.exists())
					modLogger.debug("Cannot copy '" + copy.getFrom() + "' to '" + copy.getTo() + "'; the latter already exists");
				else
				{
					try
					{
						// make sure the destination parent folder exists
						if (!to.getParentFile().exists())
							if (!to.getParentFile().mkdirs())
								throw new IOException("Could not create destination parent directory!");
						
						IOUtils.copy(from, to);
					}
					catch (IOException ioe)
					{
						modLogger.error("Unable to copy '" + copy.getFrom() + "' to '" + copy.getTo() + "'!", ioe);
						logInstallError(node.getName() + ": The file '" + copy.getFrom() + "' could not be copied to '" + copy.getTo() + "'.");
						return null;
					}
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
		if (!node.getInstallList().isEmpty())
		{
			modLogger.info("Processing INSTALL items");
			setText("Installing files...");
			
			final Connector connector = (Connector) Configuration.getInstance().getSettings().get(Configuration.CONNECTOR_KEY);
			
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
					modLogger.debug("Submitting download task for '" + file + "'");
					
					final DownloadPanel downloadPanel = downloadPanelMap.get(new KeyPair<InstallUnit, String>(install, file));
					
					// submit a task for this file
					FreeSpaceOpenInstaller.getInstance().submitTask("Download " + file, new Callable<Void>()
					{
						public Void call()
						{
							// this technique will attempt to perform the installation, record whether it is successful,
							// and then signal its completion via the countdown latch
							try
							{
								// first do the installation
								boolean success = installOne(connector, modFolder, urls, file, downloadPanel);
								if (success)
									successes.incrementAndGet();
								// don't mislead the user if we cancelled the file
								else if (!Thread.currentThread().isInterrupted())
									logInstallError(node.getName() + ": The file '" + file + "' could not be downloaded.");
								int complete = completions.incrementAndGet();
								
								// next update the progress bar
								setRatioComplete(complete / ((double) totalTasks));
								
								modLogger.info("File '" + file + "' was " + (success ? "successful" : "unsuccessful"));
								modLogger.info("This marks " + complete + ((complete == 1) ? " file out of " : " files out of ") + totalTasks);
							}
							catch (RuntimeException re)
							{
								modLogger.error("Unhandled runtime exception!", re);
								logInstallError(node.getName() + ": An unexpected error occurred.  Please check the log file for more details.");
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
			modLogger.debug("Waiting for all files to complete...");
			try
			{
				latch.await();
			}
			catch (InterruptedException ie)
			{
				modLogger.error("Thread was interrupted while waiting for files to complete!", ie);
				Thread.currentThread().interrupt();
				return false;
			}
			modLogger.info("All files have completed!");
			modLogger.info("This marks " + successes.get() + " successful out of " + totalTasks);
			
			// check success or failure
			return (successes.get() == totalTasks);
		}
		// nothing to install, so obviously we were successful
		else
		{
			modLogger.info("There was nothing to install!");
			return true;
		}
	}
	
	/**
	 * Perform hash validation for this node.
	 */
	private boolean performHashTasks(File modFolder)
	{
		if (!node.getHashList().isEmpty())
		{
			modLogger.info("Processing HASH items");
			setText("Computing hash values...");
			
			int badHashes = 0;
			
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
					modLogger.error("Unable to compute hash; '" + algorithm + "' is not a recognized algorithm!", nsae);
					logInstallError(node.getName() + ": The installer cannot compute a hash using the '" + algorithm + "' algorithm.");
					continue;
				}
				
				// find the file to hash
				File fileToHash = new File(modFolder, hash.getFilename());
				if (!fileToHash.exists())
				{
					modLogger.debug("Cannot compute hash for '" + hash.getFilename() + "'; it does not exist");
					continue;
				}
				
				// hash it
				modLogger.info("Computing a " + algorithm + " hash for '" + hash.getFilename() + "'");
				String computedHash;
				try
				{
					computedHash = IOUtils.computeHash(digest, fileToHash);
				}
				catch (IOException ioe)
				{
					modLogger.error("There was a problem computing the hash...", ioe);
					continue;
				}
				
				// compare it
				if (!hash.getHash().equalsIgnoreCase(computedHash))
				{
					modLogger.error("Computed hash value of " + computedHash + " does not match required hash value of " + hash.getHash() + " for file '" + hash.getFilename() + "'!");
					
					// we can't keep a bad file
					boolean baleeted = false;
					try
					{
						baleeted = fileToHash.delete();
					}
					catch (SecurityException se)
					{
						modLogger.error("Encountered a SecurityException when trying to delete '" + hash.getFilename() + "'!", se);
					}
					
					// notify the user
					String result = node.getName() + ": The hash value for '" + hash.getFilename() + "' did not agree with the expected value.  This could indicate a corrupted download.  ";
					if (baleeted)
					{
						result += "The file has been deleted.";
						modLogger.error("File deleted!");
					}
					else
					{
						result += "Additionally, the installer was unable to delete the file.  Please delete the file yourself and do not open it.";
						modLogger.error("Unable to delete the file!");
					}
					logInstallError(result);
					
					// fail
					badHashes++;
				}
			}
			
			if (badHashes == 0)
			{
				modLogger.info("There were no invalid hashes.");
				return true;
			}
			else
			{
				modLogger.info("There were " + badHashes + " invalid hashes.");
				return false;
			}
		}
		// nothing to hash, so obviously we were successful
		else
		{
			modLogger.info("There was nothing to hash!");
			return true;
		}
	}
	
	private boolean installOne(Connector connector, File modFolder, List<BaseURL> baseURLList, String file, final DownloadPanel downloadPanel)
	{
		modLogger.info("Installing '" + file + "'");
		
		// try all URLs supplied
		for (BaseURL baseURL: baseURLList)
		{
			modLogger.debug("Obtaining URL");
			URL url;
			try
			{
				url = baseURL.toURL(file);
			}
			catch (MalformedURLException murle)
			{
				modLogger.error("Bad URL '" + baseURL.toString() + file + "'", murle);
				continue;
			}
			
			// make a downloader for our panel
			final Downloader downloader = new Downloader(connector, url, modFolder);
			EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					downloadPanel.setDownloader(downloader);
				}
			});
			
			try
			{
				// perform the download
				modLogger.debug("Beginning download from '" + baseURL.toString() + file + "'");
				boolean success = downloader.download();
				
				// did it work?
				if (success)
					return true;
			}
			catch (RuntimeException re)
			{
				logger.error("Unexpected runtime exception while downloading!", re);
			}
		}
		
		return false;
	}
	
	private void logInstallNote(final String message)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				installNotes.add(message);
			}
		});
	}
	
	private void logInstallError(final String message)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				installErrors.add(message);
			}
		});
	}
	
	public List<String> getInstallNotes()
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		return installNotes;
	}
	
	public List<String> getInstallErrors()
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		return installErrors;
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
	
	protected static enum InstallItemState
	{
		INITIALIZED,
		RUNNING,
		COMPLETED,
		CANCELLED
	}
}
