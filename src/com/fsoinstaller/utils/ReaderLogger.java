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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.Callable;
import java.util.logging.Level;


/**
 * Funnel the text from a Reader into a Logger. Since it implements Runnable
 * (and Callable for that matter), it can be started as an independent thread.
 * 
 * @author Goober5000
 */
public class ReaderLogger implements Runnable, Callable<Void>
{
	private final Reader reader;
	private final Logger logger;
	private final Level level;
	private final String preamble;
	
	public ReaderLogger(Reader reader, Logger logger, Level level)
	{
		this(reader, logger, level, null);
	}
	
	public ReaderLogger(Reader reader, Logger logger, Level level, String preamble)
	{
		this.reader = reader;
		this.logger = logger;
		this.level = level;
		this.preamble = preamble;
	}
	
	public Void call() throws IOException
	{
		BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
		
		try
		{
			// don't write the preamble unless we have other data to write as well: this prevents false positives when checking to see if any output was written
			boolean writtenPreamble = false;
			
			String line;
			while ((line = br.readLine()) != null)
			{
				if (!writtenPreamble)
				{
					if (preamble != null)
						logger.log(Level.INFO, preamble);
					writtenPreamble = true;
				}
				logger.log(level, line);
			}
		}
		finally
		{
			br.close();
		}
		
		return null;
	}
	
	public void run()
	{
		try
		{
			call();
		}
		catch (IOException ioe)
		{
			Logger.getLogger(ReaderLogger.class).error("There was a problem copying the Reader text to the Logger!", ioe);
		}
	}
}
