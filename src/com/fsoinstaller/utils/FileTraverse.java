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

import java.io.File;
import java.io.IOException;


/**
 * This class can be extended to perform any action that requires a traversal on
 * the file tree. It can be used as both a search mechanism and a recursive file
 * processor.
 * 
 * @author Goober5000
 */
public abstract class FileTraverse<T>
{
	private final boolean traverseBeforeFunction;
	
	public FileTraverse()
	{
		this.traverseBeforeFunction = false;
	}
	
	public FileTraverse(boolean traverseBeforeFunction)
	{
		this.traverseBeforeFunction = traverseBeforeFunction;
	}
	
	public final T on(File startingDir) throws IOException
	{
		if (startingDir == null)
			throw new NullPointerException("Starting directory cannot be null!");
		else if (!startingDir.isDirectory())
			throw new IllegalArgumentException("Starting directory must be a directory!");
		
		File[] files = startingDir.listFiles();
		if (files == null)
			throw new IOException("Failed to list files in directory: " + startingDir);
		
		if (!traverseBeforeFunction)
		{
			T result = forDirectory(startingDir);
			if (result != null)
				return result;
		}
		
		for (File file: files)
		{
			if (file.isDirectory())
			{
				on(file);
			}
			else if (file.isFile())
			{
				T result = forFile(file);
				if (result != null)
					return result;
			}
			else
				throw new IOException("File system entry '" + file + "' is neither a file nor a directory!");
		}
		
		if (traverseBeforeFunction)
		{
			T result = forDirectory(startingDir);
			if (result != null)
				return result;
		}
		
		return null;
	}
	
	/**
	 * Override as desired. Return non-null if the file traversal should
	 * short-circuit and stop immediately.
	 */
	public T forDirectory(File directory) throws IOException
	{
		return null;
	}
	
	/**
	 * Override as desired. Return non-null if the file traversal should
	 * short-circuit and stop immediately.
	 */
	public T forFile(File file) throws IOException
	{
		return null;
	}
}
