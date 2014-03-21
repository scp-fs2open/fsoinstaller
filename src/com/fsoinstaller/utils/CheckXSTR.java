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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Just a utility class to check that all XSTR strings have counterparts in
 * XSTR.properties and vice versa.
 * 
 * @author Goober5000
 */
public class CheckXSTR
{
	private static Logger logger = Logger.getLogger(CheckXSTR.class);
	
	public static void main(String[] args)
	{
		// get all java files
		File root = new File("src");
		List<File> javaFiles = new ArrayList<File>();
		addJavaFilesInTree(root, javaFiles);
		// get all keys from them
		Set<String> javaKeys = new HashSet<String>();
		for (File file: javaFiles)
			addKeysInJavaFile(file, javaKeys);
		
		// get all keys from XSTR
		Properties XSTR = PropertiesUtils.loadProperties("resources/XSTR.properties");
		Set<String> XSTRkeys = XSTR.stringPropertyNames();
		
		// find the differences
		Set<String> javaKeysNotInXSTR = new HashSet<String>();
		Set<String> XSTRKeysNotInJava = new HashSet<String>();
		for (String javaKey: javaKeys)
			if (!XSTRkeys.contains(javaKey))
				javaKeysNotInXSTR.add(javaKey);
		for (String XSTRkey: XSTRkeys)
			if (!javaKeys.contains(XSTRkey))
				XSTRKeysNotInJava.add(XSTRkey);
		
		if (javaKeysNotInXSTR.isEmpty() && XSTRKeysNotInJava.isEmpty())
		{
			logger.info("All keys are matched!");
		}
		else
		{
			if (!javaKeysNotInXSTR.isEmpty())
			{
				logger.info("The following keys are in .java files but not in XSTR.properties:");
				for (String key: javaKeysNotInXSTR)
					logger.info(key);
			}
			if (!XSTRKeysNotInJava.isEmpty())
			{
				logger.info("The following keys are in XSTR.properties but not in .java files:");
				for (String key: XSTRKeysNotInJava)
					logger.info(key);
			}
		}
	}
	
	private static void addJavaFilesInTree(File root, List<File> fileList)
	{
		if (root.isDirectory())
		{
			File[] files = root.listFiles();
			if (files == null)
				throw new IllegalStateException("Failed to list files in " + root.getAbsolutePath());
			
			for (File file: files)
				addJavaFilesInTree(file, fileList);
		}
		else
		{
			if (root.getName().endsWith(".java"))
				fileList.add(root);
		}
	}
	
	private static void addKeysInJavaFile(File file, Set<String> keys)
	{
		Pattern pattern = Pattern.compile("XSTR\\.getString\\(\"([^\"]*)\"\\)");
		List<String> fileLines = IOUtils.readTextFileCleanly(file);
		for (String line: fileLines)
		{
			Matcher m = pattern.matcher(line);
			while (m.find())
				keys.add(m.group(1));
		}
	}
}
