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

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextPane;
import javax.swing.filechooser.FileFilter;

import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.wizard.GUIConstants;


/**
 * Useful methods for working with Swing.
 * 
 * @author Goober5000
 */
public class SwingUtils
{
	private static Logger logger = Logger.getLogger(SwingUtils.class);
	
	/**
	 * Prevent instantiation.
	 */
	private SwingUtils()
	{
	}
	
	/**
	 * Does what it says on the tin. Must be called on the event dispatching
	 * thread.
	 */
	public static void centerWindowOnScreen(Window window)
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		if (window == null)
		{
			logger.warn("Window is null!");
			return;
		}
		
		// calculate the coordinates to center the window
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int x = (int) ((screenSize.getWidth() - window.getWidth()) / 2.0 + 0.5);
		int y = (int) ((screenSize.getHeight() - window.getHeight()) / 2.0 + 0.5);
		
		// make sure it isn't off the top-left of the screen
		if (x < 0)
			x = 0;
		if (y < 0)
			y = 0;
		
		// center it
		window.setLocation(x, y);
	}
	
	/**
	 * Does what it says on the tin. Must be called on the event dispatching
	 * thread.
	 */
	public static void centerWindowOnParent(Window window, Window parent)
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		if (parent == null)
		{
			logger.warn("Centering " + ((window == null) ? null : window.getName()) + " on null parent!");
			centerWindowOnScreen(window);
			return;
		}
		
		// calculate the coordinates to center the window
		int x = (int) (parent.getX() + ((parent.getWidth() - window.getWidth()) / 2.0 + 0.5));
		int y = (int) (parent.getY() + ((parent.getHeight() - window.getHeight()) / 2.0 + 0.5));
		
		// make sure it isn't off the top-left of the screen
		if (x < 0)
			x = 0;
		if (y < 0)
			y = 0;
		
		// center it
		window.setLocation(x, y);
	}
	
	/**
	 * A thread-safe method for calling the given Runnable and waiting for it to
	 * complete. Can be safely called from any thread, including the event
	 * dispatching thread.
	 */
	public static void invokeAndWait(Runnable runnable)
	{
		try
		{
			// make sure we don't hang up the event thread
			if (EventQueue.isDispatchThread())
				runnable.run();
			else
				EventQueue.invokeAndWait(runnable);
		}
		catch (InvocationTargetException ite)
		{
			logger.error("The invocation threw an exception!", ite);
			if (ite.getCause() instanceof Error)
				throw (Error) ite.getCause();
			else if (ite.getCause() instanceof RuntimeException)
				throw (RuntimeException) ite.getCause();
			else
				throw new IllegalStateException("Unrecognized invocation exception!", ite.getCause());
		}
		catch (InterruptedException ie)
		{
			logger.error("The invocation was interrupted!", ie);
			Thread.currentThread().interrupt();
		}
	}
	
	/**
	 * Displays a file chooser dialog with a custom prompt and file filter
	 * information. The <tt>filterInfo</tt> parameter should contain 0 or more
	 * pairs of extensions and descriptions. Can be safely called from any
	 * thread, including the event dispatching thread.
	 */
	public static File promptForFile(final JFrame parentFrame, final String dialogTitle, final File applicationDir, String ... filterInfo)
	{
		if (filterInfo.length % 2 != 0)
			throw new IllegalArgumentException("The filterInfo parameter must be divisible by two!");
		
		// the varargs parameter repeats extension and description for as many filters as we need
		List<FileFilter> filterList = new ArrayList<FileFilter>();
		for (int i = 0; i < filterInfo.length; i += 2)
		{
			final String filterExtension = filterInfo[i];
			final String filterDescription = filterInfo[i + 1];
			
			FileFilter filter = new FileFilter()
			{
				@Override
				public boolean accept(File path)
				{
					if (path.isDirectory())
						return true;
					String name = path.getName();
					int pos = name.lastIndexOf('.');
					if (pos < 0)
						return false;
					return name.substring(pos + 1).equalsIgnoreCase(filterExtension);
				}
				
				@Override
				public String getDescription()
				{
					return filterDescription;
				}
			};
			filterList.add(filter);
		}
		
		final List<FileFilter> filters = Collections.unmodifiableList(filterList);
		final AtomicReference<File> fileHolder = new AtomicReference<File>();
		
		// must go on the event thread, ugh...
		SwingUtils.invokeAndWait(new Runnable()
		{
			public void run()
			{
				// create a file chooser
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle(dialogTitle);
				chooser.setCurrentDirectory(applicationDir);
				
				// set filters
				boolean firstFilter = true;
				for (FileFilter filter: filters)
				{
					chooser.addChoosableFileFilter(filter);
					
					if (firstFilter)
					{
						chooser.setFileFilter(filter);
						firstFilter = false;
					}
				}
				
				// display it
				int result = chooser.showDialog(parentFrame, XSTR.getString("OK"));
				if (result == JFileChooser.APPROVE_OPTION)
					fileHolder.set(chooser.getSelectedFile());
			}
		});
		
		return fileHolder.get();
	}
	
	public static int showCustomOptionDialog(JFrame parentFrame, String prompt, int defaultOption, String ... options)
	{
		return showCustomOptionDialog(parentFrame, prompt, defaultOption, Arrays.asList(options));
	}
	
	/**
	 * Constructs a dialog with a list of radio buttons representing options a
	 * user can select. This must be called from the event dispatching thread;
	 * for a thread-safe version, see ThreadSafeJOptionPane.
	 */
	public static int showCustomOptionDialog(JFrame parentFrame, String prompt, int defaultOption, List<String> options)
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		if (defaultOption < 0 || defaultOption >= options.size())
			throw new IllegalArgumentException("Default option " + defaultOption + " must be in the range [0, " + (options.size() - 1) + "]!");
		
		List<JRadioButton> optionButtons = new ArrayList<JRadioButton>();
		ButtonGroup group = new ButtonGroup();
		
		// create options and group them
		for (String option: options)
		{
			JRadioButton button = new JRadioButton(option);
			button.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			
			optionButtons.add(button);
			group.add(button);
		}
		
		// mark the default option
		optionButtons.get(defaultOption).setSelected(true);
		
		// put the whole thing in a panel
		JPanel optionDialogPanel = new JPanel();
		optionDialogPanel.setLayout(new BoxLayout(optionDialogPanel, BoxLayout.Y_AXIS));
		
		// we might not actually need the prompt
		if (prompt != null)
		{
			// put the prompt in a JTextPane that looks like a JLabel
			JTextPane promptPane = new JTextPane();
			promptPane.setBackground(null);
			promptPane.setEditable(false);
			promptPane.setBorder(null);
			promptPane.setText(prompt);
			promptPane.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			
			JComponent strut = (JComponent) Box.createVerticalStrut(GUIConstants.DEFAULT_MARGIN);
			strut.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			
			optionDialogPanel.add(promptPane);
			optionDialogPanel.add(strut);
		}
		// but we do need the buttons
		for (JRadioButton button: optionButtons)
			optionDialogPanel.add(button);
		
		// prompt the user
		int result = JOptionPane.showOptionDialog(parentFrame, optionDialogPanel, FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, null, null);
		
		// hitting the Close button should be interpreted as a Cancel
		if (result == JOptionPane.CLOSED_OPTION)
			return -1;
		
		// return the option the user selected
		for (int i = 0; i < optionButtons.size(); i++)
			if (optionButtons.get(i).isSelected())
				return i;
		
		logger.error("How did the user manage to hit OK with nothing being selected?");
		return defaultOption;
	}
}
