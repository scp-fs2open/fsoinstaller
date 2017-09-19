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

package com.fsoinstaller.main;

import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;

import org.apache.commons.compress.compressors.CompressorStreamFactory;

import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.common.InstallerNodeParseException;
import com.fsoinstaller.utils.IOUtils;
import com.fsoinstaller.utils.InstallerUtils;
import com.fsoinstaller.utils.KeyPair;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.OperatingSystem;
import com.fsoinstaller.utils.SwingUtils;
import com.fsoinstaller.utils.ThreadSafeJOptionPane;
import com.fsoinstaller.wizard.InstallerGUI;

import io.sigpipe.jbsdiff.Diff;
import io.sigpipe.jbsdiff.Patch;
import io.sigpipe.jbsdiff.progress.ProgressEvent;
import io.sigpipe.jbsdiff.progress.ProgressListener;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


public class FreeSpaceOpenInstaller
{
	private static Logger logger = Logger.getLogger(FreeSpaceOpenInstaller.class);
	
	/**
	 * Title used for dialogs and such.
	 */
	public static final String INSTALLER_TITLE = Configuration.getInstance().getApplicationTitle();
	
	/**
	 * Version of the Installer.
	 */
	public static final String INSTALLER_VERSION = "2.3.0";
	
	/**
	 * URL of the directories where version.txt and filenames.txt reside.
	 */
	public static final List<String> INSTALLER_HOME_URLs = Collections.unmodifiableList(Arrays.asList(new String[]
	{
		"http://www.fsoinstaller.com/files/installer/java/",
		"http://scp.indiegames.us/fsoinstaller/"
	}));
	
	/**
	 * Use the Initialization On Demand Holder idiom for thread-safe
	 * non-synchronized singletons.
	 */
	private static final class InstanceHolder
	{
		private static final FreeSpaceOpenInstaller INSTANCE = new FreeSpaceOpenInstaller();
		
		static
		{
			Thread hook = new Thread()
			{
				@Override
				public void run()
				{
					logger.info("Entered shutdown hook!");
					INSTANCE.shutDownTasks();
					
					// do this after all shutdown tasks have completed
					INSTANCE.shutdownLatch.countDown();
				}
			};
			hook.setName("FreeSpaceOpenInstaller-shutdownHook");
			hook.setPriority(Thread.NORM_PRIORITY);
			Runtime.getRuntime().addShutdownHook(hook);
		}
	}
	
	public static FreeSpaceOpenInstaller getInstance()
	{
		return InstanceHolder.INSTANCE;
	}
	
	private final ExecutorService executorService;
	private final List<KeyPair<String, Future<Void>>> submittedTasks;
	private final CountDownLatch shutdownLatch;
	
	private FreeSpaceOpenInstaller()
	{
		// create thread pool to manage long-running tasks, such as file downloads
		// IMPLEMENTATION DETAIL: since tasks are queued from the event thread, we need to use an implementation that never blocks on adding a task
		executorService = Executors.newCachedThreadPool();
		
		// keep track of all tasks that have been submitted
		submittedTasks = Collections.synchronizedList(new ArrayList<KeyPair<String, Future<Void>>>());
		
		// used to wait until the installer's hook has run
		shutdownLatch = new CountDownLatch(1);
	}
	
	public Future<Void> submitTask(String taskName, Callable<Void> task)
	{
		try
		{
			Future<Void> future = executorService.submit(task);
			submittedTasks.add(new KeyPair<String, Future<Void>>(taskName, future));
			return future;
		}
		catch (RejectedExecutionException ree)
		{
			logger.error("Could not schedule '" + taskName + "' for execution!", ree);
			return null;
		}
	}
	
	public void shutDownTasks()
	{
		// note that the ExecutorService isn't going to terminate automatically, so we need to shut it down properly in success or failure
		if (!executorService.isShutdown())
		{
			logger.debug("Asking the executor service to shut down...");
			executorService.shutdown();
		}
		
		// cancel all incomplete submitted tasks (but there shouldn't be any at this point, if everything worked right, unless we hit Cancel)
		synchronized (submittedTasks)
		{
			Iterator<KeyPair<String, Future<Void>>> ii = submittedTasks.iterator();
			while (ii.hasNext())
			{
				KeyPair<String, Future<Void>> pair = ii.next();
				String name = pair.getObject1();
				Future<Void> future = pair.getObject2();
				
				logger.debug("Task '" + name + "': " + (future.isCancelled() ? "cancelled" : (future.isDone() ? "complete" : "running")));
				if (future.isDone())
					ii.remove();
				else
				{
					logger.warn("Cancelling '" + name + "'");
					future.cancel(true);
				}
			}
		}
		
		logger.debug("All tasks should now be shut down.");
	}
	
	public void awaitShutdown() throws InterruptedException
	{
		shutdownLatch.await();
	}
	
	private void launchWizard()
	{
		// before we display the GUI, let's set the application directory
		Configuration config = Configuration.getInstance();
		if (config.getApplicationDir() == null)
		{
			logger.info("No application directory supplied in configuration");
			String applicationDir = null;
			List<String> dirList = config.getDefaultDirList();
			
			// only search for FS2 if we require it
			// (we will do a more thorough check using hashes and such later, but this should be sufficient to choose which default to use) 
			if (config.requiresFS2())
			{
				File[] roots = File.listRoots();
				
				// test all roots and all default dirs
rootLoop:		for (File root: roots)
				{
					for (String dir: dirList)
					{
						try
						{
							File fs2Dir = new File(root, dir);
							if (fs2Dir.exists() && fs2Dir.isDirectory())
							{
								// we found an existing directory, so look for root_fs2.vp
								String[] names = fs2Dir.list();
								if (names == null)
								{
									logger.warn("Could not obtain directory listing for " + dir);
								}
								else
								{
									for (String name: names)
									{
										if (name.equalsIgnoreCase("root_fs2.vp"))
										{
											applicationDir = fs2Dir.getAbsolutePath();
											logger.info("Found root_fs2.vp in " + applicationDir);
											break rootLoop;
										}
									}
								}
							}
						}
						catch (SecurityException se)
						{
							logger.error("Checking the directory " + dir + " caused a SecurityException to be thrown!", se);
						}
					}
				}
			}
			
			// if we didn't find a directory any other way, use the first item in the default dir list (at least one will exist)
			if (applicationDir == null)
				applicationDir = dirList.get(0);
				
			// log it and set it
			logger.info("Setting application directory to " + applicationDir);
			config.setApplicationDir(new File(applicationDir));
		}
		
		// build and display the GUI
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				logger.debug("Launching wizard...");
				
				// visual elements
				InstallerGUI gui = new InstallerGUI();
				gui.buildUI();
				gui.pack();
				
				// window closing behavior
				gui.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
				gui.addWindowListener(new WindowAdapter()
				{
					/**
					 * Triggered by someone hitting the close button.
					 */
					@Override
					public void windowClosing(WindowEvent e)
					{
						logger.debug("Main window is closing...");
						shutDownTasks();
						
						System.exit(0);
					}
					
					/**
					 * Triggered by the frame being disposed.
					 */
					@Override
					public void windowClosed(WindowEvent e)
					{
						logger.debug("Main window was closed...");
						shutDownTasks();
						
						System.exit(0);
					}
					
				});
				
				// display it
				SwingUtils.centerWindowOnScreen(gui);
				gui.setVisible(true);
			}
		});
	}
	
	public static void main(String[] args)
	{
		// this can sometimes happen with a borked Ubuntu configuration...
		if (GraphicsEnvironment.isHeadless())
		{
			logger.error("Sorry, this application cannot be run in a headless environment!");
			logger.error("(This means that either your system does not have a display, keyboard, and mouse installed, or your version of Java does not support one of these methods of user interaction.  For example, Ubuntu will sometimes install a version of Java without graphics libraries.  In this case, you will need to reinstall the full version.)");
			return;
		}
		
		// log app information
		logger.info(INSTALLER_TITLE);
		logger.info(INSTALLER_VERSION);
		
		// since we're logging the locale (when XSTR is invoked), let's log the system information too
		logger.info("System OS string: " + System.getProperty("os.name"));
		logger.info("System OS version: " + System.getProperty("os.version"));
		logger.info("System OS architecture: " + System.getProperty("os.arch"));
		logger.info("ProgramFiles(x86): " + (System.getenv("ProgramFiles(x86)") != null));
		logger.info("OS recognized as: " + OperatingSystem.getHostOS().name());
		
		// and why not log the Java version while we're at it
		logger.info("Java version: " + System.getProperty("java.version"));
		logger.info("Java JVM bits: " + System.getProperty("sun.arch.data.model"));
		
		// we need to set the button text for any dialogs that appear
		// (this has the side-effect of initializing XSTR before any Swing stuff, which keeps the flow conceptually untangled)
		UIManager.put("OptionPane.yesButtonText", XSTR.getString("Yes"));
		UIManager.put("OptionPane.noButtonText", XSTR.getString("No"));
		UIManager.put("OptionPane.cancelButtonText", XSTR.getString("cancelButtonName"));
		
		// Swing code goes on the event-dispatching thread...
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				logger.debug("Setting look-and-feel...");
				try
				{
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				}
				catch (ClassNotFoundException cnfe)
				{
					logger.error("Error setting look-and-feel!", cnfe);
				}
				catch (InstantiationException ie)
				{
					logger.error("Error setting look-and-feel!", ie);
				}
				catch (IllegalAccessException iae)
				{
					logger.error("Error setting look-and-feel!", iae);
				}
				catch (UnsupportedLookAndFeelException iae)
				{
					logger.error("Error setting look-and-feel!", iae);
				}
			}
		});
		
		// we might launch different utilities depending on the command-line argument
		String command = args.length == 0 ? "" : args[0];
		
		// first custom command is validating install config files
		if (command.equals("validate"))
		{
			selectAndValidateModFile(args);
		}
		// we can also generate hashes
		else if (command.equals("hash"))
		{
			selectAndHashFile(args, false);
		}
		// ditto
		else if (command.equals("hash-stdout"))
		{
			selectAndHashFile(args, true);
		}
		// test out a mod file by piggybacking an installer session on the results of validation
		else if (command.equals("test"))
		{
			if (selectAndValidateModFile(args))
			{
				FreeSpaceOpenInstaller installer = getInstance();
				installer.launchWizard();
			}
		}
		// test diffing files
		else if (command.equals("diff"))
		{
			selectAndDiffFiles(args);
		}
		// test patching files
		else if (command.equals("patch"))
		{
			selectAndPatchFiles(args);
		}
		// default command is the standard wizard installation
		else
		{
			FreeSpaceOpenInstaller installer = getInstance();
			installer.launchWizard();
		}
	}
	
	private static boolean canUse(File file)
	{
		if (!file.exists())
		{
			logger.warn("The file '" + file.getAbsolutePath() + "' does not exist!");
			return false;
		}
		else if (file.isDirectory())
		{
			logger.warn("The file '" + file.getAbsolutePath() + "' is a directory!");
			return false;
		}
		
		return true;
	}
	
	private static boolean selectAndValidateModFile(String[] args)
	{
		final Configuration config = Configuration.getInstance();
		File modFile;
		
		// see if the user supplied an argument
		if (args.length > 1)
		{
			modFile = new File(args[1]);
		}
		// if not, prompt for it
		else
		{
			modFile = SwingUtils.promptForFile(null, XSTR.getString("chooseModConfigTitle"), config.getApplicationDir(), "txt", XSTR.getString("textFilesFilter"));
			if (modFile == null)
				return false;
		}
		
		// warn if invalid
		if (!canUse(modFile))
			return false;
			
		// parse it
		try
		{
			List<InstallerNode> nodes = IOUtils.readInstallFile(modFile);
			for (InstallerNode node: nodes)
				logger.info("Successfully parsed " + node.getName());
				
			logger.info(XSTR.getString("allNodesParsedSuccessfully"));
			
			// since this was successful, save it to the configuration
			config.getSettings().put(Configuration.OVERRIDE_INSTALL_MOD_NODES_KEY, nodes);
		}
		catch (FileNotFoundException fnfe)
		{
			logger.error("The file could not be found!", fnfe);
			return false;
		}
		catch (IOException ioe)
		{
			logger.error("There was an error reading the file!", ioe);
			return false;
		}
		catch (InstallerNodeParseException inpe)
		{
			logger.warn("There was an error parsing the mod file!", inpe);
			return false;
		}
		
		return true;
	}
	
	private static void selectAndHashFile(String[] args, boolean to_stdout)
	{
		final Configuration config = Configuration.getInstance();
		String algorithm;
		
		// get the algorithm
		if (args.length > 1)
		{
			algorithm = args[1].toUpperCase();
			if (algorithm.equals("SHA1"))
				algorithm = "SHA-1";
			else if (algorithm.equals("SHA256"))
				algorithm = "SHA-256";
		}
		// if not, prompt for it
		else
		{
			String[] options = new String[] { "SHA-256", "SHA-1", "MD5" };
			
			int result = ThreadSafeJOptionPane.showOptionDialog(null, XSTR.getString("chooseHashAlgorithm"), XSTR.getString("chooseOptionTitle"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
			if (result < 0)
			{
				logger.warn("No hash option selected!");
				return;
			}
			algorithm = options[result];
		}
		
		// get the hash processor, provided by Java
		MessageDigest digest;
		try
		{
			digest = MessageDigest.getInstance(algorithm);
		}
		catch (NoSuchAlgorithmException nsae)
		{
			logger.error("Unable to compute hash; '" + algorithm + "' is not a recognized algorithm!", nsae);
			return;
		}
		
		// specified via arguments?
		if (args.length > 2)
		{
			// hash all the files listed
			for (int i = 2; i < args.length; i++)
				hashFile(digest, new File(args[i]), algorithm, to_stdout);
		}
		// if not, prompt for it
		else
		{
			File dialogDir = config.getApplicationDir();
			while (true)
			{
				// prompt until the user cancels
				File fileToHash = SwingUtils.promptForFile(null, XSTR.getString("chooseFileTitle"), dialogDir);
				if (fileToHash == null)
					break;
					
				hashFile(digest, fileToHash, algorithm, to_stdout);
				
				// update the directory where the user selects files
				if (fileToHash.exists() && !fileToHash.isDirectory())
					dialogDir = fileToHash.getParentFile();
			}
		}
	}
	
	private static void hashFile(MessageDigest digest, File fileToHash, String algorithm, boolean to_stdout)
	{
		// warn if invalid
		if (!canUse(fileToHash))
			return;
			
		// hash the file
		try
		{
			String computedHash = IOUtils.computeHash(digest, fileToHash);
			
			if (to_stdout)
			{
				System.out.println("HASH");
				System.out.println(algorithm);
				System.out.println(fileToHash.getAbsolutePath());
				System.out.println(computedHash);
			}
			else
			{
				logger.info(fileToHash.getAbsolutePath());
				logger.info(algorithm + " hash: " + computedHash);
			}
		}
		catch (IOException ioe)
		{
			logger.error("There was a problem computing the hash for '" + fileToHash + "'...", ioe);
		}
	}
	
	private static void selectAndDiffFiles(String[] args)
	{
		final Configuration config = Configuration.getInstance();
		String patchType;
		String[] options = CompressorStreamFactory.SUPPORTED_TYPES;
		
		// get the patch type
		if (args.length > 1)
		{
			patchType = args[1];
			if (!Arrays.asList(options).contains(patchType))
			{
				logger.error("Unsupported patch type '" + patchType + "'!");
				return;
			}
		}
		// if not, prompt for it
		else
		{
			int result = ThreadSafeJOptionPane.showOptionDialog(null, XSTR.getString("choosePatchType"), XSTR.getString("chooseOptionTitle"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
			if (result < 0)
			{
				logger.warn("No patch option selected!");
				return;
			}
			patchType = options[result];
		}
		
		// get the files to patch
		File sourceFile;
		File targetFile;
		if (args.length > 3)
		{
			sourceFile = new File(args[2]);
			targetFile = new File(args[3]);
		}
		// if not, prompt for them
		else
		{
			File dialogDir = config.getApplicationDir();
			
			// prompt until the user cancels
			sourceFile = SwingUtils.promptForFile(null, XSTR.getString("chooseSourceFileTitle"), dialogDir);
			if (sourceFile == null)
				return;
				
			// update the directory where the user selects files
			if (sourceFile.exists() && !sourceFile.isDirectory())
				dialogDir = sourceFile.getParentFile();
				
			targetFile = SwingUtils.promptForFile(null, XSTR.getString("chooseTargetFileTitle"), dialogDir);
			if (targetFile == null)
				return;
		}
		
		// warn if invalid
		if (!canUse(sourceFile) || !canUse(targetFile))
			return;
			
		// find a destination file
		File patchFile = new File(sourceFile.getParentFile(), "patchFile." + patchType);
		if (patchFile.exists())
			patchFile = new File(sourceFile.getParentFile(), InstallerUtils.UUID() + "." + patchType);
			
		// create patch
		try
		{
			Diff diff = new Diff();
			diff.addProgressListener(new ProgressListener()
			{
				int lastPercent = -1;
				
				public void progressMade(ProgressEvent event)
				{
					int percent = (int)(((double)event.getCurrent() / (double)event.getTotal()) * 100);
					if (percent > lastPercent)
					{
						lastPercent = percent;
						logger.debug(event.getCurrent() + " of " + event.getTotal() + " (" + percent + "%)");
					}
				}
			});
			
			logger.info("Generating patch file...");
			IOUtils.generatePatch(diff, patchType, sourceFile, targetFile, patchFile);
			
			logger.info(patchFile.getAbsolutePath());
		}
		catch (IOException ioe)
		{
			logger.error("There was a problem generating the patch...", ioe);
		}
	}
	
	private static void selectAndPatchFiles(String[] args)
	{
		final Configuration config = Configuration.getInstance();
		
		// get the files to patch
		File sourceFile;
		File patchFile;
		if (args.length > 2)
		{
			sourceFile = new File(args[1]);
			patchFile = new File(args[2]);
		}
		// if not, prompt for them
		else
		{
			File dialogDir = config.getApplicationDir();
			
			// prompt until the user cancels
			sourceFile = SwingUtils.promptForFile(null, XSTR.getString("chooseSourceFileTitle"), dialogDir);
			if (sourceFile == null)
				return;
				
			// update the directory where the user selects files
			if (sourceFile.exists() && !sourceFile.isDirectory())
				dialogDir = sourceFile.getParentFile();
				
			patchFile = SwingUtils.promptForFile(null, XSTR.getString("choosePatchFileTitle"), dialogDir);
			if (patchFile == null)
				return;
		}
		
		// warn if invalid
		if (!canUse(sourceFile) || !canUse(patchFile))
			return;
			
		// find a destination file
		File targetFile = new File(sourceFile.getParentFile(), sourceFile.getName() + ".patched");
		if (targetFile.exists())
			targetFile = new File(sourceFile.getParentFile(), sourceFile.getName() + "." + InstallerUtils.UUID());
			
		// apply patch
		try
		{
			Patch patch = new Patch();
			patch.addProgressListener(new ProgressListener()
			{
				int lastPercent = -1;
				
				public void progressMade(ProgressEvent event)
				{
					int percent = (int)(((double)event.getCurrent() / (double)event.getTotal()) * 100);
					if (percent > lastPercent)
					{
						lastPercent = percent;
						logger.debug(event.getCurrent() + " of " + event.getTotal() + " (" + percent + "%)");
					}
				}
			});
			
			logger.info("Applying patch file...");
			IOUtils.applyPatch(patch, sourceFile, patchFile, targetFile);
			
			logger.info(targetFile.getAbsolutePath());
		}
		catch (IOException ioe)
		{
			logger.error("There was a problem applying the patch...", ioe);
		}
	}
}
