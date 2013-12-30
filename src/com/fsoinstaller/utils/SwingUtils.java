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

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;


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
	
	public static void centerWindowOnScreen(Window window)
	{
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
	
	public static void centerWindowOnParent(Window window, Window parent)
	{
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
	
	public static Frame getActiveFrame()
	{
		for (Frame frame: Frame.getFrames())
		{
			// the first visible frame ought to be the active one
			if (frame.isVisible())
				return frame;
		}
		
		return null;
	}
	
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
}
