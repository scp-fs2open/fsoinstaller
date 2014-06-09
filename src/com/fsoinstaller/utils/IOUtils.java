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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.common.InstallerNodeFactory;
import com.fsoinstaller.common.InstallerNodeParseException;


public class IOUtils
{
	private static final Logger logger = Logger.getLogger(IOUtils.class);
	
	public static final String ENDL = System.getProperty("line.separator");
	
	/**
	 * Prevent instantiation.
	 */
	private IOUtils()
	{
	}
	
	public static List<String> readTextFileCleanly(File inputFile)
	{
		try
		{
			return readTextFile(inputFile);
		}
		catch (FileNotFoundException fnfe)
		{
			logger.error("The file '" + inputFile.getAbsolutePath() + "' was not found!", fnfe);
			return new ArrayList<String>();
		}
		catch (IOException ioe)
		{
			logger.error("There was a problem reading the file '" + inputFile.getAbsolutePath() + "'!", ioe);
			return new ArrayList<String>();
		}
	}
	
	public static List<String> readTextFile(File inputFile) throws FileNotFoundException, IOException
	{
		return readText(new InputStreamReader(new FileInputStream(inputFile), Charset.forName("UTF-8")));
	}
	
	public static List<String> readText(Reader reader) throws IOException
	{
		List<String> lines = new ArrayList<String>();
		
		BufferedReader br = null;
		try
		{
			br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
			
			String line;
			while ((line = br.readLine()) != null)
				lines.add(line);
		}
		finally
		{
			if (br != null)
				br.close();
		}
		
		return lines;
	}
	
	public static List<InstallerNode> readInstallFile(File inputFile) throws FileNotFoundException, IOException, InstallerNodeParseException
	{
		List<InstallerNode> nodes = new ArrayList<InstallerNode>();
		
		InputStreamReader reader = null;
		try
		{
			reader = new InputStreamReader(new FileInputStream(inputFile), Charset.forName("UTF-8"));
			
			InstallerNode node;
			while ((node = InstallerNodeFactory.readNode(reader)) != null)
				nodes.add(node);
		}
		finally
		{
			if (reader != null)
				reader.close();
		}
		
		return nodes;
	}
	
	public static InputStream getResourceStream(String resource)
	{
		InputStream is = ClassLoader.getSystemResourceAsStream("resources/" + resource);
		if (is != null)
		{
			logger.info("Loading '" + resource + "' via system class loader");
			return is;
		}
		
		is = IOUtils.class.getResourceAsStream("resources/" + resource);
		if (is != null)
		{
			logger.info("Loading '" + resource + "' via IOUtils class loader");
			return is;
		}
		
		return null;
	}
	
	public static String computeHash(MessageDigest messageDigest, File file) throws FileNotFoundException, IOException
	{
		byte[] buffer = new byte[1024];
		
		FileInputStream fis = null;
		try
		{
			fis = new FileInputStream(file);
			
			int len;
			while ((len = fis.read(buffer)) != -1)
			{
				messageDigest.update(buffer, 0, len);
			}
		}
		finally
		{
			if (fis != null)
				fis.close();
		}
		
		byte[] hashedBytes = messageDigest.digest();
		
		// put the hash into a string
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < hashedBytes.length; i++)
			sb.append(Integer.toString((hashedBytes[i] & 0xff) + 0x100, 16).substring(1));
		
		return sb.toString();
	}
	
	public static void copy(File from, File to) throws IOException
	{
		FileChannel fromChannel = null;
		FileChannel toChannel = null;
		
		try
		{
			fromChannel = new FileInputStream(from).getChannel();
			toChannel = new FileOutputStream(to).getChannel();
			
			toChannel.transferFrom(fromChannel, 0, fromChannel.size());
		}
		finally
		{
			IOException closeException = null;
			
			if (fromChannel != null)
			{
				try
				{
					fromChannel.close();
				}
				catch (IOException ioe)
				{
					closeException = ioe;
				}
			}
			if (toChannel != null)
			{
				try
				{
					toChannel.close();
				}
				catch (IOException ioe)
				{
					closeException = ioe;
				}
			}
			
			if (closeException != null)
				throw closeException;
		}
	}
	
	public static boolean deleteDirectoryTree(File directory)
	{
		if (directory.isDirectory())
		{
			File[] files = directory.listFiles();
			// I/O error
			if (files == null)
				return false;
			
			for (File file: files)
			{
				if (file.isDirectory())
				{
					if (!deleteDirectoryTree(file))
						return false;
				}
				else if (!file.delete())
				{
					logger.error("Unable to delete file '" + file.getAbsolutePath() + "'");
					return false;
				}
			}
		}
		
		return directory.delete();
	}
	
	/**
	 * Evaluates whether the specified folder name should be interpreted as
	 * referring to the installation root folder. Candidate names will be either
	 * null, zero-length, a slash, or a backslash.
	 */
	public static boolean isRootFolderName(String folderName)
	{
		return (folderName == null || folderName.length() == 0 || folderName.equals("/") || folderName.equals("\\"));
	}
	
	/**
	 * Creates the given file in the given directory, or returns the file if it
	 * already exists. Since Linux distinguishes files by file case, we use a
	 * manual case-insensitive search.
	 */
	public static File newFileIgnoreCase(File directory, String fileName)
	{
		File file = getFileIgnoreCase(directory, fileName);
		if (file == null)
			file = new File(directory, fileName);
		
		return file;
	}
	
	/**
	 * Performs a case-insensitive search of the specified directory for the
	 * specified file or subdirectory.
	 * 
	 * @return the matching file/subdirectory, or null if the file/subdirectory
	 *         does not exist
	 */
	public static File getFileIgnoreCase(File directory, String fileName)
	{
		if (!directory.isDirectory())
			throw new IllegalArgumentException("Directory argument must be a directory!");
		
		// if it's the root folder, return the directory itself
		if (isRootFolderName(fileName))
			return directory;
		
		// search the files in the directory
		File[] files = directory.listFiles();
		if (files != null)
		{
			for (File file: files)
			{
				// found a file with the same name, using a case-insensitive comparison
				if (file.getName().equalsIgnoreCase(fileName))
					return file;
			}
		}
		
		return null;
	}
	
	public static List<String> getLowerCaseFiles(File directory)
	{
		if (!directory.isDirectory())
			throw new IllegalArgumentException("Directory argument must be a directory!");
		
		List<String> fileNames = new ArrayList<String>();
		
		// populate the files in the directory
		File[] files = directory.listFiles();
		if (files != null)
		{
			for (File file: files)
				fileNames.add(file.getName().toLowerCase());
		}
		
		return fileNames;
	}
}
