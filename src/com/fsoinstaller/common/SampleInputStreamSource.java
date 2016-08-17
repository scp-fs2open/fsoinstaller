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

package com.fsoinstaller.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.fsoinstaller.utils.Logger;


public class SampleInputStreamSource
{
	private static final Logger logger = Logger.getLogger(SampleInputStreamSource.class);
	
	private final URL sourceURL;
	
	public SampleInputStreamSource(URL sourceURL)
	{
		this.sourceURL = sourceURL;
	}
	
	public InputStream recycleInputStream(InputStream oldInputStream, long position) throws IOException, IndexOutOfBoundsException
	{
		if (position < 0)
			throw new IndexOutOfBoundsException("Position cannot be negative!");
		
		if (oldInputStream != null)
		{
			try
			{
				oldInputStream.close();
			}
			catch (IOException ioe)
			{
				logger.warn("Could not close download stream!", ioe);
			}
		}
		
		// open a new stream at the correct position
		// (the implementations of HttpURLConnection will cache and pool connections as needed)
		HttpURLConnection connection = (HttpURLConnection) sourceURL.openConnection();
		if (position > 0)
			connection.setRequestProperty("Range", "bytes=" + position + "-");
		
		logger.debug("Opening new input stream...");
		InputStream newInputStream = connection.getInputStream();
		
		if (position > 0 && connection.getResponseCode() != HttpURLConnection.HTTP_PARTIAL)
			throw new IOException("The site at " + sourceURL + " does not support returning partial content!  HTTP response code = " + connection.getResponseCode());
		
		return newInputStream;
	}
}
