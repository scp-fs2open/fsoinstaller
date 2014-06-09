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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;

import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.utils.IOUtils;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.MiscUtils;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


/**
 * In parallel with the other Task classes in this directory, this encapsulates
 * out a bunch of stuff that would otherwise go in InstallItem. It just copies
 * all the FS2 program files from a Steam installation to the installation
 * directory.
 * 
 * @author Goober5000
 */
class CopyInstallationTask implements Callable<Boolean>
{
	private static final Logger logger = Logger.getLogger(CopyInstallationTask.class);
	
	private final InstallItem item;
	private final File steamInstallLocation;
	
	public CopyInstallationTask(InstallItem item)
	{
		// for the progress bar and so forth
		this.item = item;
		
		this.steamInstallLocation = (File) Configuration.getInstance().getSettings().get(Configuration.STEAM_INSTALL_LOCATION_KEY);
	}
	
	public Boolean call()
	{
		if (!steamInstallLocation.exists())
			throw new IllegalStateException("The source installation location must exist!");
		
		// the first thing we do is obtain a list of the files that need to be copied
		item.setIndeterminate(true);
		item.setText(XSTR.getString("copyInstallationRetrievingList"));
		List<String> fileList;
		try
		{
			// read list of files that we'll copy
			InputStream is = IOUtils.getResourceStream("freespace2_files.txt");
			InputStreamReader reader = new InputStreamReader(is, Charset.forName("UTF-8"));
			fileList = IOUtils.readText(reader);
		}
		catch (IOException ioe)
		{
			logger.error("Failed to retrieve the list of files to copy!", ioe);
			item.logInstallError(XSTR.getString("copyInstallationRetrievingListFailed"));
			return false;
		}
		
		// replace slashes with path separators
		ListIterator<String> ii = fileList.listIterator();
		while (ii.hasNext())
		{
			String file = ii.next();
			ii.set(MiscUtils.standardizeSlashes(file));
		}
		
		// copy the files and report progress
		item.setIndeterminate(false);
		item.setText(XSTR.getString("copyInstallationCopyingFiles"));
		try
		{
			copyFiles(fileList);
		}
		catch (IOException ioe)
		{
			logger.error("Could not copy files to installation directory!", ioe);
			item.logInstallError(XSTR.getString("copyInstallationCopyingFilesFailed"));
			return false;
		}
		
		// we are done!
		return true;
	}
	
	private void copyFiles(List<String> fileList) throws IOException
	{
		// copy all the files from the Steam location to the game location
		File installDir = Configuration.getInstance().getApplicationDir();
		
		int totalFiles = fileList.size();
		logger.info("Copying " + totalFiles + " files from '" + steamInstallLocation.getAbsolutePath() + "' to '" + installDir.getAbsolutePath() + "'...");
		
		int numFiles = 0;
		item.setPercentComplete(0);
		for (String fileName: fileList)
		{
			// set up files to copy (root_fs2 shouldn't be capitalized at its destination)
			File from = new File(steamInstallLocation, fileName);
			File to = new File(installDir, fileName.equalsIgnoreCase("root_fs2.vp") ? fileName.toLowerCase() : fileName);
			
			if (!from.exists())
			{
				logger.warn("Source file '" + from.getAbsolutePath() + "' does not exist!");
				
				if (to.getName().toLowerCase().endsWith(".vp"))
					throw new IOException("A required .vp file '" + to.getName() + "' was not found!");
			}
			else if (from.isDirectory())
			{
				if (!to.exists() && !to.mkdirs())
					throw new IOException("Destination directory '" + to.getAbsolutePath() + "' could not be created!");
			}
			// no need to copy if the destination already exists
			else if (!to.exists())
			{
				// make sure destination parent folders exist
				if (!to.getParentFile().exists() && !to.getParentFile().mkdirs())
					throw new IOException("Unable to copy '" + from.getAbsolutePath() + "' to '" + to.getAbsolutePath() + "': destination file tree could not be created!");
				
				// do the copying
				logger.debug(fileName);
				IOUtils.copy(from, to);
			}
			
			// update progress as the copy proceeds
			numFiles++;
			item.setPercentComplete(numFiles * 100 / totalFiles);
		}
		
		// copying was successful!
		logger.info("...done");
	}
}
