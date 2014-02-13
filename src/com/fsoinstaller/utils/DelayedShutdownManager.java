/*
 * This file is part of the FreeSpace Open Installer
 * Copyright (C) 2014 The FreeSpace 2 Source Code Project
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

import java.lang.reflect.Field;
import java.util.logging.LogManager;

import com.fsoinstaller.main.FreeSpaceOpenInstaller;


public class DelayedShutdownManager extends LogManager
{
	@Override
	public void reset()
	{
		// get the dumb private flag
		boolean shuttingDown = false;
		synchronized (this)
		{
			Field flag;
			try
			{
				flag = LogManager.class.getDeclaredField("deathImminent");
				flag.setAccessible(true);
				shuttingDown = flag.getBoolean(this);
				flag.setAccessible(false);
			}
			catch (NoSuchFieldException nsfe)
			{
				throw new Error("Implementation failure; the field does not exist!", nsfe);
			}
			catch (IllegalArgumentException iae)
			{
				throw new Error("Implementation failure; field is not applicable to class!", iae);
			}
			catch (SecurityException se)
			{
				System.err.println("Security manager prevents accessing private field of superclass!");
				se.printStackTrace();
			}
			catch (IllegalAccessException iae)
			{
				System.err.println("The superclass field is inaccessible!");
				iae.printStackTrace();
			}
		}
		
		// only block if we know the logger is actually shutting down
		if (shuttingDown)
		{
			// don't shut down logging until the installer has finished shutdown tasks
			try
			{
				FreeSpaceOpenInstaller.getInstance().awaitShutdown();
			}
			catch (InterruptedException ie)
			{
				System.err.println("Logger was interrupted while waiting for all shutdown logs to be written!");
			}
		}
		
		// perform normal superclass behavior
		super.reset();
	}
}
