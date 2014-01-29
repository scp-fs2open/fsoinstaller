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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;

import com.fsoinstaller.utils.KeyPair;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.SwingUtils;
import com.fsoinstaller.wizard.InstallerGUI;


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
	public static final String INSTALLER_VERSION = "2.0.3";
	
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
	}
	
	public static FreeSpaceOpenInstaller getInstance()
	{
		return InstanceHolder.INSTANCE;
	}
	
	private final ExecutorService executorService;
	private final List<KeyPair<String, Future<Void>>> submittedTasks;
	
	private FreeSpaceOpenInstaller()
	{
		// create thread pool to manage long-running tasks, such as file downloads
		// IMPLEMENTATION DETAIL: since tasks are queued from the event thread, we need to use an implementation that never blocks on adding a task
		executorService = Executors.newFixedThreadPool(20);
		
		// keep track of all tasks that have been submitted
		submittedTasks = Collections.synchronizedList(new ArrayList<KeyPair<String, Future<Void>>>());
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
	}
	
	private void launchWizard()
	{
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
					@Override
					public void windowClosed(WindowEvent e)
					{
						shutDownTasks();
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
		
		FreeSpaceOpenInstaller installer = getInstance();
		
		// for now, we only have one possible operation: going through the wizard
		installer.launchWizard();
		
		// later we'll evaluate the runtime args to launch the txtfile builder, etc.
	}
}
