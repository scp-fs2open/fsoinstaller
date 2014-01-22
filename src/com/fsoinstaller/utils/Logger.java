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

package com.fsoinstaller.utils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;


/**
 * This is a wrapper over the Java logging API that provides an API similar to
 * log4j. It's no longer a 1-1 wrapper though; it provides separate logging
 * files for separate mods.
 * 
 * @author Goober5000
 */
public final class Logger
{
	private static final String standardFile = "fsoinstaller.log";
	
	private static final Map<String, FileHandler> fileHandlerMap = new HashMap<String, FileHandler>();
	private static final Map<KeyPair<Class<?>, String>, Logger> loggerMap = new HashMap<KeyPair<Class<?>, String>, Logger>();
	
	private static final Formatter formatter = new Log4JFormatter();
	
	// do logging setup
	static
	{
		// configure root logger
		java.util.logging.Logger.getLogger("").setLevel(Level.ALL);
		for (Handler handler: java.util.logging.Logger.getLogger("").getHandlers())
			handler.setFormatter(formatter);
	}
	
	private static FileHandler createFileHandler(String fileName)
	{
		String logfile = fileName;
		
		File file = new File("logs");
		if (file.exists())
		{
			// already exists, yay
			logfile = "logs" + File.separator + fileName;
		}
		else
		{
			// doesn't exist; try to create it
			try
			{
				if (file.mkdir())
					logfile = "logs" + File.separator + fileName;
			}
			catch (SecurityException se)
			{
				java.util.logging.Logger.getLogger("global").log(Level.SEVERE, "Could not create the logs folder for logging!", se);
			}
		}
		
		FileHandler handler = null;
		try
		{
			handler = new FileHandler(logfile, true);
		}
		catch (SecurityException se)
		{
			java.util.logging.Logger.getLogger("global").log(Level.SEVERE, "Could not create FileHandler for " + logfile + "!", se);
		}
		catch (IOException ioe)
		{
			java.util.logging.Logger.getLogger("global").log(Level.SEVERE, "Could not create FileHandler for " + logfile + "!", ioe);
		}
		
		if (handler != null)
			handler.setFormatter(formatter);
		return handler;
	}
	
	public static Logger getLogger(Class<?> clazz)
	{
		return getLogger(clazz, null);
	}
	
	public static Logger getLogger(Class<?> clazz, String modName)
	{
		String fileName;
		if (modName == null)
			fileName = standardFile;
		else
			fileName = MiscUtils.createValidFileName(modName);
		if (!fileName.toLowerCase().endsWith(".log"))
			fileName += ".log";
		KeyPair<Class<?>, String> key = new KeyPair<Class<?>, String>(clazz, fileName);
		
		synchronized (loggerMap)
		{
			// return logger if map already contains it
			if (loggerMap.containsKey(key))
				return loggerMap.get(key);
			
			// okay, so we need to retrieve the appropriate file handler, and create *that* if it doesn't exist
			FileHandler fileHandler;
			if (fileHandlerMap.containsKey(fileName))
				fileHandler = fileHandlerMap.get(fileName);
			else
			{
				fileHandler = createFileHandler(fileName);
				fileHandlerMap.put(fileName, fileHandler);
			}
			
			// create the Java logger
			java.util.logging.Logger wrapped_logger = java.util.logging.Logger.getLogger(key.toString());
			if (fileHandler != null)
				wrapped_logger.addHandler(fileHandler);
			
			// wrap it, store it, and return it			
			Logger logger = new Logger(clazz, wrapped_logger);
			loggerMap.put(key, logger);
			return logger;
		}
	}
	
	private final java.util.logging.Logger logger;
	private final String className;
	
	private Logger(Class<?> clazz, java.util.logging.Logger logger)
	{
		this.className = clazz.getName();
		this.logger = logger;
	}
	
	private void log(Level level, Object message)
	{
		logger.logp(level, className, "", message == null ? "null" : message.toString());
	}
	
	private void log(Level level, Object message, Throwable throwable)
	{
		logger.logp(level, className, "", message == null ? "null" : message.toString(), throwable);
	}
	
	public void fatal(Object message)
	{
		log(Level.SEVERE, message);
	}
	
	public void fatal(Object message, Throwable throwable)
	{
		log(Level.SEVERE, message, throwable);
	}
	
	public void error(Object message)
	{
		log(Level.SEVERE, message);
	}
	
	public void error(Object message, Throwable throwable)
	{
		log(Level.SEVERE, message, throwable);
	}
	
	public void warn(Object message)
	{
		log(Level.WARNING, message);
	}
	
	public void warn(Object message, Throwable throwable)
	{
		log(Level.WARNING, message, throwable);
	}
	
	public void info(Object message)
	{
		log(Level.INFO, message);
	}
	
	public void info(Object message, Throwable throwable)
	{
		log(Level.INFO, message, throwable);
	}
	
	public void debug(Object message)
	{
		log(Level.FINE, message);
	}
	
	public void debug(Object message, Throwable throwable)
	{
		log(Level.FINE, message, throwable);
	}
	
	public void trace(Object message)
	{
		log(Level.FINER, message);
	}
	
	public void trace(Object message, Throwable throwable)
	{
		log(Level.FINER, message, throwable);
	}
	
	public boolean isInfoEnabled()
	{
		return logger.isLoggable(Level.INFO);
	}
	
	public boolean isDebugEnabled()
	{
		return logger.isLoggable(Level.FINE);
	}
	
	public boolean isTraceEnabled()
	{
		return logger.isLoggable(Level.FINER);
	}
}
