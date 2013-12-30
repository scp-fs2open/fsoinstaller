/*
 * This file is part of the FreeSpace Open Installer
 * Copyright (C) 2013 The FreeSpace 2 Source Code Project
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

package com.fsoinstaller.utils;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

import org.jdesktop.swingworker.SwingWorker;

import com.fsoinstaller.main.FreeSpaceOpenInstaller;


/**
 * A neat little class to run a task while displaying a progress bar indicating
 * time to completion. The task is specified as a Callable interface, and it has
 * the responsibility of updating the dialog's progress as needed.
 */
public class ProgressBarDialog
{
	private static final Logger logger = Logger.getLogger(ProgressBarDialog.class);
	
	public static final String INDETERMINATE_STRING = "Working...";
	
	private final String text;
	private final String title;
	private JProgressBar bar;
	
	public ProgressBarDialog()
	{
		this(null, null);
	}
	
	public ProgressBarDialog(String text)
	{
		this(text, null);
	}
	
	public ProgressBarDialog(String text, String title)
	{
		if (title == null)
			title = FreeSpaceOpenInstaller.INSTALLER_TITLE;
		
		this.text = text;
		this.title = title;
	}
	
	/**
	 * This method exists in case we want to configure the progress bar before
	 * actually starting the task.
	 */
	private void maybeCreateProgressBar()
	{
		// no synchronization is necessary only because we always access from a single thread
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		// already created?
		if (bar != null)
			return;
		
		// set up our progress bar
		bar = new JProgressBar(0, 100);
		bar.setIndeterminate(true);
		bar.setString(INDETERMINATE_STRING);
		bar.setStringPainted(true);
	}
	
	public void runTask(Callable<Void> task, AbnormalTerminationCallback callback)
	{
		if (task == null || callback == null)
			throw new IllegalArgumentException("Neither task nor callback can be null!");
		
		final Callable<Void> _task = task;
		final AbnormalTerminationCallback _callback = callback;
		
		// all GUI setup goes on the event-dispatch thread
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				maybeCreateProgressBar();
				
				// create a dialog to show progress
				final JDialog dialog = new JDialog(SwingUtils.getActiveFrame(), title, true);
				dialog.setResizable(false);
				
				// populate dialog
				JPanel contentPane = (JPanel) dialog.getContentPane();
				contentPane.setLayout(new BorderLayout());
				if (text != null)
					contentPane.add(new JLabel(text), BorderLayout.NORTH);
				contentPane.add(bar, BorderLayout.CENTER);
				
				// configure dialog GUI
				dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
				dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
				dialog.pack();
				SwingUtils.centerWindowOnParent(dialog, SwingUtils.getActiveFrame());
				
				// create a SwingWorker
				final SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>()
				{
					@Override
					protected Void doInBackground() throws Exception
					{
						logger.info("Running task: '" + text + "'");
						_task.call();
						return null;
					}
					
					@Override
					protected void done()
					{
						dialog.dispose();
						
						if (isCancelled())
						{
							logger.info("Task '" + text + "' was cancelled!");
							_callback.handleCancellation();
						}
						else
						{
							logger.info("Completed task: '" + text + "'");
							
							// see if there were any exceptions
							try
							{
								// this actually returns CancellationException if the task had been cancelled
								get();
							}
							catch (InterruptedException ie)
							{
								logger.warn("The task was interrupted!", ie);
								_callback.handleException(ie);
							}
							catch (ExecutionException ee)
							{
								logger.error("The task aborted due to an exception!", ee.getCause());
								_callback.handleException((Exception) ee.getCause());
							}
						}
					}
				};
				
				// add ability to cancel the task
				dialog.addWindowListener(new WindowAdapter()
				{
					@Override
					public void windowClosing(WindowEvent e)
					{
						worker.cancel(true);
					}
				});
				
				// kick it off
				worker.execute();
				dialog.setVisible(true);
			}
		});
	}
	
	public void setIndeterminate(final boolean indeterminate)
	{
		logger.debug("Setting progress bar to " + (indeterminate ? "indeterminate" : "determinate"));
		
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				maybeCreateProgressBar();
				
				bar.setIndeterminate(indeterminate);
				bar.setString(indeterminate ? INDETERMINATE_STRING : null);
			}
		});
	}
	
	public void setPercentComplete(int percent)
	{
		if (percent < 0)
			percent = 0;
		else if (percent > 100)
			percent = 100;
		logger.debug("Progress: " + percent + "%");
		
		final int _percent = percent;
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				maybeCreateProgressBar();
				
				bar.setValue(_percent);
			}
		});
	}
	
	public void setRatioComplete(double ratio)
	{
		setPercentComplete((int) (ratio * 100));
	}
	
	/**
	 * A simple callback interface in case the dialog encounters an exception or
	 * cancellation in the process of running the task. These methods will be
	 * called on the event-dispatch thread.
	 */
	public static interface AbnormalTerminationCallback
	{
		public void handleCancellation();
		
		public void handleException(Exception exception);
	}
}
