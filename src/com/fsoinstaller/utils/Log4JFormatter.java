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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;


/**
 * This is a custom formatter for the Java logging API, based on
 * SimpleFormatter. The logging format is designed to look like a Log4J
 * PatternLayout with the following pattern:
 * 
 * <pre>
 * %d{ISO8601} [%t] %-5p %c - %m%n
 * </pre>
 * 
 * @author Goober5000
 */
public class Log4JFormatter extends Formatter
{
	private final Date date = new Date();
	private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
	
	@Override
	public synchronized String format(LogRecord record)
	{
		date.setTime(record.getMillis());
		
		String source;
		if (record.getSourceClassName() != null)
		{
			source = record.getSourceClassName();
			if (record.getSourceMethodName() != null)
			{
				source += " " + record.getSourceMethodName();
			}
		}
		else
		{
			source = record.getLoggerName();
		}
		
		String message = formatMessage(record);
		
		String throwable = "";
		if (record.getThrown() != null)
		{
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println();
			record.getThrown().printStackTrace(pw);
			pw.close();
			throwable = sw.toString();
		}
		
		final Level level = record.getLevel();
		String log4jLevel;
		if (level == Level.SEVERE)
			log4jLevel = "ERROR";
		else if (level == Level.WARNING)
			log4jLevel = "WARN ";
		else if (level == Level.INFO)
			log4jLevel = "INFO ";
		else if (level == Level.FINE)
			log4jLevel = "DEBUG";
		else if (level == Level.FINER)
			log4jLevel = "TRACE";
		else
			log4jLevel = "X " + level.getName().substring(0, 3);
		
		StringBuilder line = new StringBuilder(dateFormat.format(date));
		line.append(" [");
		line.append(Thread.currentThread().getName());
		line.append("] ");
		line.append(log4jLevel);
		line.append(" ");
		line.append(source);
		line.append(" - ");
		line.append(message);
		line.append(throwable);
		line.append(IOUtils.ENDL);
		return line.toString();
	}
}
