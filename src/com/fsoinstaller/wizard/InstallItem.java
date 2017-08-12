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
import com.fsoinstaller.common.InstallerNode.PatchTriple;
import com.fsoinstaller.internet.Connector;
import com.fsoinstaller.internet.Downloader;
import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.utils.CollapsiblePanel;
import com.fsoinstaller.utils.IOUtils;
import com.fsoinstaller.utils.InstallerUtils;
import com.fsoinstaller.utils.KeyPair;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.MiscUtils;

import io.sigpipe.jbsdiff.Patch;
import io.sigpipe.jbsdiff.progress.ProgressEvent;
import io.sigpipe.jbsdiff.progress.ProgressListener;

import static com.fsoinstaller.wizard.GUIConstants.*;
import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


public class InstallItem extends JPanel
{
	private static final Logger logger = Logger.getLogger(InstallItem.class);
	
	private final InstallerNode node;
	private final List<ChangeListener> listenerList;
	
	private final JProgressBar overallBar;
	private final StoplightPanel stoplightPanel;
	
	private final Map<KeyPair<InstallUnit, PatchTriple>, Integer> patchTaskIndexes;
	private final Map<KeyPair<InstallUnit, String>, Integer> downloadTaskIndexes;
	private final List<InstallTaskPanel> installTaskPanelList;
	
	private final List<InstallItem> childItems;
	private int remainingChildren;
	private List<String> installNotes;
	private List<String> installErrors;
	private Future<Void> overallInstallTask;
	private InstallItemState state;
	
	private final Configuration configuration;
	private final boolean installNotNeeded;
	private final Logger modLogger;
	
	public InstallItem(InstallerNode node, Set<String> selectedMods)
	{
		super();
		this.node = node;
		this.listenerList = new CopyOnWriteArrayList<ChangeListener>();
		
		configuration = Configuration.getInstance();
		modLogger = Logger.getLogger(InstallItem.class, node.getTreePath());
		
		// if node has a parent, don't add a margin on the right side because the enclosing parent already has one
		setBorder(BorderFactory.createEmptyBorder(SMALL_MARGIN, SMALL_MARGIN, SMALL_MARGIN, node.getParent() != null ? 0 : SMALL_MARGIN));
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		
		overallBar = new JProgressBar(0, 100);
		overallBar.setIndeterminate(true);
		overallBar.setString(XSTR.getString("progressBarWaiting2"));
		overallBar.setStringPainted(true);
		
		stoplightPanel = new StoplightPanel((int) overallBar.getPreferredSize().getHeight());
		
		// these variables will only ever be accessed from the event thread, so they are thread safe
		childItems = new ArrayList<InstallItem>();
		remainingChildren = 0;
		installNotes = new ArrayList<String>();
		installErrors = new ArrayList<String>();
		overallInstallTask = null;
		state = InstallItemState.INITIALIZED;
		
		// if the "re-run installation" checkbox was checked, then we don't short-circuit via installNotNeeded
		if ((Boolean) configuration.getSettings().get(Configuration.DONT_SHORT_CIRCUIT_INSTALLATION_KEY))
		{
			installNotNeeded = false;
		}
		// possibly short-circuit
		else
		{
			// only perform the installation if the stored version is different from the version available online
			String storedVersion = configuration.getUserProperties().getProperty(node.getTreePath());
			// note: if a node was successfully installed but had no version, it will save a version of "null" to the properties
			installNotNeeded = (storedVersion != null && storedVersion.equals(node.getVersion() == null ? "null" : node.getVersion()));
		}
		
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
		
		// add as many panels as we have install tasks for
		Map<KeyPair<InstallUnit, PatchTriple>, Integer> tempPatchMap = new HashMap<KeyPair<InstallUnit, PatchTriple>, Integer>();
		Map<KeyPair<InstallUnit, String>, Integer> tempDownloadMap = new HashMap<KeyPair<InstallUnit, String>, Integer>();
		List<InstallTaskPanel> tempPanelList = new ArrayList<InstallTaskPanel>();
		if (!installNotNeeded)
		{
			for (InstallUnit install: node.getInstallList())
			{
				for (PatchTriple patch: install.getPatchList())
				{
					KeyPair<InstallUnit, PatchTriple> key = new KeyPair<InstallUnit, PatchTriple>(install, patch);
					if (tempPatchMap.containsKey(key))
					{
						logger.error("Duplicate patch key found for mod '" + node.getTreePath() + "', patchTriple " + install.getPatchList().indexOf(patch));
						continue;
					}
					
					DownloadPanel panel = new DownloadPanel();
					installPanel.add(panel);
					
					tempPatchMap.put(key, tempPanelList.size());
					tempPanelList.add(panel);
				}
			}
			for (InstallUnit install: node.getInstallList())
			{
				for (String file: install.getFileList())
				{
					KeyPair<InstallUnit, String> key = new KeyPair<InstallUnit, String>(install, file);
					if (tempDownloadMap.containsKey(key))
					{
						logger.error("Duplicate key found for mod '" + node.getTreePath() + "', file '" + file + "'!");
						continue;
					}
					
					DownloadPanel panel = new DownloadPanel();
					installPanel.add(panel);
					
					tempDownloadMap.put(key, tempPanelList.size());
					tempPanelList.add(panel);
				}
			}
		}
		patchTaskIndexes = Collections.unmodifiableMap(tempPatchMap);
		downloadTaskIndexes = Collections.unmodifiableMap(tempDownloadMap);
		installTaskPanelList = Collections.unmodifiableList(tempPanelList);
		
		// add as many children as we have
		for (InstallerNode childNode: node.getChildren())
		{
			// only add a node if it was selected
			if (!selectedMods.contains(childNode.getTreePath()))
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
	
	public InstallerNode getInstallerNode()
	{
		return node;
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
		overallInstallTask = FreeSpaceOpenInstaller.getInstance().submitTask(XSTR.getString("installTitle") + " " + node.getTreePath(), new Callable<Void>()
		{
			public Void call()
			{
				try
				{
					// only install if we have to
					if (!installNotNeeded)
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
							logInstallError(XSTR.getString("installResultSecurityExceptionInSetup"));
							modFolder = null;
						}
						
						// if setup didn't work, can't continue
						if (modFolder == null || Thread.currentThread().isInterrupted())
						{
							failInstallTree();
							return null;
						}
						modLogger.info("Installing to path: " + modFolder.getAbsolutePath());
						
						// prepare progress bar for tracking progress
						setPercentComplete(0);
						setIndeterminate(false);
												
						// before we download anything, let's see if we can patch it
						boolean success = performPatchTasks(modFolder);
						if (!success || Thread.currentThread().isInterrupted())
						{
							failInstallTree();
							return null;
						}
						
						// now we are about to download stuff
						success = performInstallTasks(modFolder);
						if (!success || Thread.currentThread().isInterrupted())
						{
							failInstallTree();
							return null;
						}
						
						// now hash the files we installed
						success = performHashTasks(modFolder);
						if (!success || Thread.currentThread().isInterrupted())
						{
							failInstallTree();
							return null;
						}
						
						// run any system commands
						success = performExecTasks(modFolder);
						if (!success || Thread.currentThread().isInterrupted())
						{
							failInstallTree();
							return null;
						}
						
						// ***** HACK: here are some special cases *****
						
						// if this is the OpenAL node, re-test for OpenAL
						if (node.getName().equals(XSTR.getString("installOpenALName")))
						{
							modLogger.info("Re-testing for OpenAL");
							
							if (!MiscUtils.loadOpenAL())
							{
								logInstallError(XSTR.getString("openALError"));
								failInstallTree();
								return null;
							}
						}
						// if this is the GOG node, run the GOG task
						else if (node.getName().equals(XSTR.getString("installGOGName")))
						{
							modLogger.info("Launching InnoExtractTask");
							
							InnoExtractTask task = new InnoExtractTask(InstallItem.this);
							try
							{
								success = task.call();
							}
							catch (Exception e)
							{
								// if this is InterruptedException, restore the interrupt
								if (e instanceof InterruptedException)
									Thread.currentThread().interrupt();
								
								modLogger.error("Unhandled exception running innoextract!", e);
								success = false;
							}
							
							if (!success || Thread.currentThread().isInterrupted())
							{
								failInstallTree();
								return null;
							}
						}
						// if this is the Steam copy node, run the copy task
						else if (node.getName().equals(XSTR.getString("copyInstallationName")))
						{
							modLogger.info("Launching CopyInstallationTask");
							
							CopyInstallationTask task = new CopyInstallationTask(InstallItem.this);
							try
							{
								success = task.call();
							}
							catch (Exception e)
							{
								// if this is InterruptedException, restore the interrupt
								if (e instanceof InterruptedException)
									Thread.currentThread().interrupt();
								
								modLogger.error("Unhandled exception copying installation!", e);
								success = false;
							}
							
							if (!success || Thread.currentThread().isInterrupted())
							{
								failInstallTree();
								return null;
							}
						}
						
						// *********************************************
						
						// success: save the version we just installed
						// (the synchronization here is only so that we don't try to write to the file in multiple threads simultaneously)
						configuration.getUserProperties().setProperty(node.getTreePath(), node.getVersion() == null ? "null" : node.getVersion());
						synchronized (configuration)
						{
							configuration.saveUserProperties();
						}
						
						// save any post-installation notes
						if (node.getNote() != null)
							logInstallNote(node.getNote());
						
						// update GUI
						setSuccess(true);
						setText(XSTR.getString("installStatusDone"));
						setPercentComplete(100);
					}
					else
					{
						// update GUI slightly differently
						setSuccess(true);
						setText(XSTR.getString("installStatusUpToDate"));
						setPercentComplete(100);
						setIndeterminate(false);
					}
					
					// kick off next nodes
					startChildren();
					return null;
				}
				catch (RuntimeException re)
				{
					modLogger.error("Unhandled runtime exception!", re);
					logInstallError(XSTR.getString("installResultUnexpectedRuntimeException"));
					
					// fail the tree so we don't get stuck with child nodes that are waiting to start
					// (but watch out for errors while we're doing this!)
					try
					{
						failInstallTree();
					}
					catch (RuntimeException tree_re)
					{
						modLogger.error("Well, now we're really in a bind!", tree_re);
					}
					
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
			setText(XSTR.getString("installStatusCancelled"));
		}
		else if (state == InstallItemState.RUNNING)
		{
			modLogger.info("Cancelling running task!");
			state = InstallItemState.CANCELLED;
			
			// set GUI
			setSuccess(false);
			setIndeterminate(false);
			setText(XSTR.getString("installStatusCancelled"));
			
			logInstallError(XSTR.getString("installResultCancelledByRequest"));
			
			// interrupt the running task
			overallInstallTask.cancel(true);
			
			// cancel all tasks
			for (InstallTaskPanel panel: installTaskPanelList)
				panel.cancel();
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
			
			// don't fire a redundant completion event
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
			setText(XSTR.getString("installStatusParentNotInstalled"));
			
			modLogger.info("Parent mod could not be installed; this mod will be skipped!");
			logInstallError(XSTR.getString("installResultSkipped"));
			
			for (InstallTaskPanel panel: installTaskPanelList)
				panel.preempt();
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
		File installDir = configuration.getApplicationDir();
		
		modLogger.info("Starting processing");
		setText(XSTR.getString("progressBarSettingUpMod"));
		
		// create the folder for this mod, if it has one
		File folder;
		String folderName = node.getFolder();
		if (IOUtils.isRootFolderName(folderName))
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
					logInstallError(String.format(XSTR.getString("installResultFolderNotCreated"), folderName));
					return null;
				}
			}
		}
		
		if (!node.getDeleteList().isEmpty())
		{
			modLogger.info("Processing DELETE items");
			setText(XSTR.getString("progressBarDeleting"));
			
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
						logInstallError(String.format(XSTR.getString("installResultDirectoryNotDeleted"), file));
						return null;
					}
				}
				else if (!file.delete())
				{
					modLogger.error("Unable to delete the file '" + delete + "'!");
					logInstallError(String.format(XSTR.getString("installResultFileNotDeleted"), file));
					return null;
				}
			}
		}
		
		if (!node.getRenameList().isEmpty())
		{
			modLogger.info("Processing RENAME items");
			setText(XSTR.getString("progressBarRenaming"));
			
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
				else
				{
					// make sure the parent directory exists
					if (to.getParentFile() != null && !to.getParentFile().exists() && !to.getParentFile().mkdirs())
					{
						modLogger.error("Unable to rename '" + rename.getFrom() + "' to '" + rename.getTo() + "': destination file tree could not be created!");
						logInstallError(String.format(XSTR.getString("installResultFileNotRenamed"), rename.getFrom(), rename.getTo()));
						return null;
					}
					
					// try to rename it
					if (!from.renameTo(to))
					{
						modLogger.error("Unable to rename '" + rename.getFrom() + "' to '" + rename.getTo() + "'!");
						logInstallError(String.format(XSTR.getString("installResultFileNotRenamed"), rename.getFrom(), rename.getTo()));
						return null;
					}
				}
			}
		}
		
		if (!node.getCopyList().isEmpty())
		{
			modLogger.info("Processing COPY items");
			setText(XSTR.getString("progressBarCopying"));
			
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
						logInstallError(String.format(XSTR.getString("installResultFileNotCopied"), copy.getFrom(), copy.getTo()));
						return null;
					}
				}
			}
		}
		
		return folder;
	}
	
	/**
	 * Perform patching for this node, if needed.
	 */
	private boolean performPatchTasks(final File modFolder)
	{
		// count task items first
		int patchItems = 0;
		for (InstallUnit unit: node.getInstallList())
			patchItems += unit.getPatchList().size();
		
		if (patchItems > 0)
		{
			modLogger.info("Processing PATCH items");
			setText(XSTR.getString("progressBarPatching"));
			
			final Connector connector = (Connector) configuration.getSettings().get(Configuration.CONNECTOR_KEY);
			
			final int totalTasks = patchItems;
			final AtomicInteger completions = new AtomicInteger(0);
			final CountDownLatch latch = new CountDownLatch(totalTasks);
			
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
				
				// perform all patches for the unit
				for (final PatchTriple triple: install.getPatchList())
				{
					modLogger.debug("Submitting patch task for " + triple.getPrePatch().getFilename());
					
					int patchTaskIndex = patchTaskIndexes.get(new KeyPair<InstallUnit, PatchTriple>(install, triple));
					final DownloadPanel patchPanel = (DownloadPanel) installTaskPanelList.get(patchTaskIndex);
					
					// submit a task for this patch
					FreeSpaceOpenInstaller.getInstance().submitTask(XSTR.getString("patchTitle") + " " + triple.getPrePatch().getFilename(), new Callable<Void>()
					{
						public Void call()
						{
							// this technique will attempt to perform the patch,
							// and then signal its completion via the countdown latch
							try
							{
								// first do the patch
								patchOne(connector, modFolder, urls, triple, patchPanel);
								int complete = completions.incrementAndGet();
								
								// next update the progress bar
								setRatioComplete(complete / ((double) totalTasks));
							}
							catch (RuntimeException re)
							{
								modLogger.error("Unhandled runtime exception!", re);
								logInstallError(XSTR.getString("installResultUnexpectedRuntimeException"));
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
			modLogger.debug("Waiting for all patch tasks to complete...");
			try
			{
				latch.await();
			}
			catch (InterruptedException ie)
			{
				modLogger.error("Thread was interrupted while waiting for patches to complete!", ie);
				Thread.currentThread().interrupt();
				return false;
			}
			modLogger.info("All patch tasks have completed!");
		}
		
		// always return true (we don't stop the installation if one of the PATCH items failed)
		return true;
	}
	
	/**
	 * Perform the main installation tasks for this node.
	 */
	private boolean performInstallTasks(final File modFolder)
	{
		// count task items first
		int downloadItems = 0;
		for (InstallUnit unit: node.getInstallList())
			downloadItems += unit.getFileList().size();
				
		if (downloadItems > 0)
		{
			modLogger.info("Processing INSTALL items");
			setText(XSTR.getString("progressBarInstalling"));
			
			final Connector connector = (Connector) configuration.getSettings().get(Configuration.CONNECTOR_KEY);
			
			final int totalTasks = downloadItems;
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
					
					int downloadTaskIndex = downloadTaskIndexes.get(new KeyPair<InstallUnit, String>(install, file));
					final DownloadPanel downloadPanel = (DownloadPanel) installTaskPanelList.get(downloadTaskIndex);
					
					// submit a task for this file
					FreeSpaceOpenInstaller.getInstance().submitTask(XSTR.getString("downloadTitle") + " " + file, new Callable<Void>()
					{
						public Void call()
						{
							// this technique will attempt to perform the installation, record whether it is successful,
							// and then signal its completion via the countdown latch
							try
							{
								// first do the installation
								boolean success = downloadOne(connector, modFolder, urls, file, downloadPanel);
								if (success)
									successes.incrementAndGet();
								// don't mislead the user if we cancelled the file
								else if (!Thread.currentThread().isInterrupted())
									logInstallError(String.format(XSTR.getString("installResultFileNotDownloaded"), file));
								int complete = completions.incrementAndGet();
								
								// next update the progress bar
								setRatioComplete(complete / ((double) totalTasks));
								
								modLogger.info("File '" + file + "' was " + (success ? "successful" : "unsuccessful"));
								modLogger.info("This marks " + complete + ((complete == 1) ? " file out of " : " files out of ") + totalTasks);
							}
							catch (RuntimeException re)
							{
								modLogger.error("Unhandled runtime exception!", re);
								logInstallError(XSTR.getString("installResultUnexpectedRuntimeException"));
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
	
	private String computeHash(File modFolder, HashTriple hash)
	{
		String algorithm = hash.getAlgorithm().toUpperCase();
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
			logInstallError(String.format(XSTR.getString("installResultHashNotComputed"), algorithm));
			return null;
		}
		
		// find the file to hash
		File fileToHash = IOUtils.newFileIgnoreCase(modFolder, hash.getFilename());
		if (!fileToHash.exists())
		{
			modLogger.warn("Cannot compute hash for '" + hash.getFilename() + "'; it does not exist");
			return null;
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
			return null;
		}
		
		// success, hopefully
		return computedHash;
	}
	
	/**
	 * Perform hash validation for this node.
	 */
	private boolean performHashTasks(File modFolder)
	{
		if (!node.getHashList().isEmpty())
		{
			modLogger.info("Processing HASH items");
			setText(XSTR.getString("progressBarHashing"));
			
			int badHashes = 0;
			
			for (HashTriple hash: node.getHashList())
			{
				String computedHash = computeHash(modFolder, hash);
				if (computedHash == null)
					continue;
				
				// compare it
				if (!hash.getHash().equalsIgnoreCase(computedHash))
				{
					modLogger.error("Computed hash value of " + computedHash + " does not match required hash value of " + hash.getHash() + " for file '" + hash.getFilename() + "'!");
					
					// we can't keep a bad file
					boolean baleeted = false;
					try
					{
						baleeted = IOUtils.newFileIgnoreCase(modFolder, hash.getFilename()).delete();
					}
					catch (SecurityException se)
					{
						modLogger.error("Encountered a SecurityException when trying to delete '" + hash.getFilename() + "'!", se);
					}
					
					// notify the user
					if (baleeted)
					{
						logInstallError(String.format(XSTR.getString("installResultHashMismatch1"), hash.getFilename()));
						modLogger.error("File deleted!");
					}
					else
					{
						logInstallError(String.format(XSTR.getString("installResultHashMismatch2"), hash.getFilename()));
						modLogger.error("Unable to delete the file!");
					}
					
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
			return true;
		}
	}
	
	/**
	 * Perform system commands for this node.
	 */
	private boolean performExecTasks(File modFolder)
	{
		if (!node.getExecCmdList().isEmpty())
		{
			modLogger.info("Processing EXEC items");
			setText(XSTR.getString("progressBarRunningExec"));
			
			for (String cmd: node.getExecCmdList())
			{
				int exitVal;
				try
				{
					exitVal = MiscUtils.runExecCommand(modFolder, cmd);
				}
				catch (InterruptedException ie)
				{
					modLogger.error("Thread was interrupted while waiting for exec command to complete!", ie);
					Thread.currentThread().interrupt();
					return false;
				}
				catch (IOException ie)
				{
					modLogger.error("The command '" + cmd + "' failed due to an exception!", ie);
					logInstallError(String.format(XSTR.getString("installResultExecCmdError"), cmd));
					return false;
				}
				
				// process completed "successfully" but returned an error code
				if (exitVal != 0)
				{
					modLogger.error("The command '" + cmd + "' exited with error code " + exitVal + "!");
					logInstallError(String.format(XSTR.getString("installResultExecCmdError"), cmd));
					return false;
				}
			}
		}
		
		return true;
	}
	
	private void patchOne(Connector connector, File modFolder, List<BaseURL> baseURLList, PatchTriple triple, final DownloadPanel downloadPanel)
	{
		modLogger.info("Patching " + triple.getPrePatch().getFilename());
		
		// see if the file exists
		final File prePatchFile = IOUtils.getFileIgnoreCase(modFolder, triple.getPrePatch().getFilename());
		if (prePatchFile == null)
		{
			modLogger.info("File does not exist; cannot patch it");
			return;
		}
		
		// see if the hash is what we expect
		String computedHash = computeHash(modFolder, triple.getPrePatch());
		if (computedHash == null)
		{
			// warning message was already displayed in computeHash()
			return;
		}
		else if (!triple.getPrePatch().getHash().equalsIgnoreCase(computedHash))
		{
			modLogger.info("Cannot proceed with patch; computed hash value of " + computedHash + " does not match required hash value of " + triple.getPrePatch().getHash());
			return;
		}
		
		// we can patch this file, so download the patch!
		if (!downloadOne(connector, modFolder, baseURLList, triple.getPatch().getFilename(), downloadPanel))
		{
			modLogger.warn("Unable to download the patch for '" + triple.getPrePatch().getFilename() + "'!");
			return;
		}
		
		// hash the patch itself
		computedHash = computeHash(modFolder, triple.getPatch());
		if (computedHash == null)
		{
			// warning message was already displayed in computeHash()
			return;
		}
		else if (!triple.getPatch().getHash().equalsIgnoreCase(computedHash))
		{
			modLogger.warn("Cannot proceed with patch; computed hash value of " + computedHash + " does not match required hash value of " + triple.getPatch().getHash());
			
			// delete bad patch file
			boolean baleeted = false;
			try
			{
				baleeted = IOUtils.newFileIgnoreCase(modFolder, triple.getPatch().getFilename()).delete();
			}
			catch (SecurityException se)
			{
				modLogger.error("Encountered a SecurityException when trying to delete '" + triple.getPatch().getFilename() + "'!", se);
			}
			
			// log it
			if (baleeted)
				modLogger.error("Patch file deleted!");
			else
				modLogger.error("Unable to delete the patch file!");						
			
			return;
		}
		File patchFile = IOUtils.newFileIgnoreCase(modFolder, triple.getPatch().getFilename());

		// create a temporary file as the patch destination
		String unique = InstallerUtils.UUID() + ".patched";
		File targetFile = new File(modFolder, unique);
		
		// we have good files, so perform the patching!
		Patch patch = new Patch();
		patch.addProgressListener(new ProgressListener()
		{
			public void progressMade(ProgressEvent event)
			{
				// in the same way we report progress on downloaded files, do so on patched files 
				downloadPanel.setTaskProgress(prePatchFile.getName(), event.getCurrent(), event.getTotal());
			}
		});
		try
		{
			IOUtils.applyPatch(patch, prePatchFile, patchFile, targetFile);
		}
		catch (IOException ioe)
		{
			downloadPanel.setTaskFailed(prePatchFile.getName());
			modLogger.warn("Unable to patch " + prePatchFile.getName(), ioe);
			
			return;
		}
		
		// now hash the resulting file
		// (we use a temporary HashTriple here because the target file hasn't yet been replaced by the result of the patch)
		computedHash = computeHash(modFolder, new InstallerNode.HashTriple(triple.getPostPatch().getAlgorithm(), targetFile.getName(), triple.getPostPatch().getHash()));
		if (computedHash == null || !triple.getPostPatch().getHash().equalsIgnoreCase(computedHash))
		{
			if (computedHash != null)
				modLogger.warn("Patch was unsuccessful; computed hash value of " + computedHash + " does not match required hash value of " + triple.getPostPatch().getHash());
			
			// delete bad patched file
			boolean baleeted = false;
			try
			{
				baleeted = targetFile.delete();
			}
			catch (SecurityException se)
			{
				modLogger.error("Encountered a SecurityException when trying to delete '" + targetFile.getName() + "'!", se);
			}
			
			// log it
			if (baleeted)
				modLogger.error("Unsuccessfully-patched file deleted!");
			else
				modLogger.error("Unable to delete the unsuccessfully-patched file!");
			
			return;
		}
		
		// finally, replace the original file with the result		
		File postPatchFile = IOUtils.getFileIgnoreCase(modFolder, triple.getPostPatch().getFilename());
		if (postPatchFile.exists())
		{
			try
			{
				if (!postPatchFile.delete())
					modLogger.error("Unable to delete the pre-existing post-patch file!");
			}
			catch (SecurityException se)
			{
				modLogger.error("Encountered a SecurityException when trying to delete '" + postPatchFile.getName() + "'!", se);
			}
		}
		
		// at this point postPatchFile should not exist
		if (!targetFile.renameTo(postPatchFile))
		{
			modLogger.error("Unable to rename '" + targetFile.getName() + "' to '" + postPatchFile.getName() + "'!");
		}
	}
	
	private boolean downloadOne(Connector connector, File modFolder, List<BaseURL> baseURLList, String file, final DownloadPanel downloadPanel)
	{
		modLogger.info("Downloading '" + file + "'");
		
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
			final Downloader downloader = new Downloader(connector, url, modFolder, node.getTreePath());
			EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					downloadPanel.setPending();
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
				
				// are we interrupted?
				if (Thread.currentThread().isInterrupted())
					return false;
			}
			catch (RuntimeException re)
			{
				modLogger.error("Unexpected runtime exception while downloading!", re);
			}
		}
		
		return false;
	}
	
	public void logInstallNote(final String message)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				installNotes.add(node.getName() + ": " + message);
			}
		});
	}
	
	public void logInstallError(final String message)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				installErrors.add(node.getName() + ": " + message);
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
