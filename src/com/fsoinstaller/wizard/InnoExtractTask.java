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

package com.fsoinstaller.wizard;

import java.util.concurrent.Callable;

import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.MiscUtils;
import com.fsoinstaller.utils.MiscUtils.OperatingSystem;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


/**
 * In parallel with the other Task classes in this directory, this encapsulates
 * out a bunch of stuff that would otherwise go in InstallItem.
 * 
 * @author Goober5000
 */
class InnoExtractTask implements Callable<Boolean>
{
	private static final Logger logger = Logger.getLogger(InnoExtractTask.class);
	
	private final InstallItem item;
	
	public InnoExtractTask(InstallItem item)
	{
		// for the progress bar and so forth
		this.item = item;
	}
	
	public Boolean call() throws Exception
	{
		// some operating systems are not supported
		OperatingSystem os = MiscUtils.determineOS();
		switch (os)
		{
			case WINDOWS:
				String os_name_lower = System.getProperty("os.name").toLowerCase();
				if (os_name_lower.contains("windows 95") || os_name_lower.contains("windows 98") || os_name_lower.contains("windows me"))
				{
					item.logInstallError(XSTR.getString("innoExtractRequiresXP"));
					return false;
				}
				// other versions of Windows are okay
				break;
			
			// we'll assume all versions of Linux/Unix are okay
			case UNIX:
				break;
			
			// no other OS is supported
			default:
				item.logInstallError(XSTR.getString("innoExtractNotSupported"));
				return false;
		}
		
		// TODO
		
		return Boolean.TRUE;
	}
}
