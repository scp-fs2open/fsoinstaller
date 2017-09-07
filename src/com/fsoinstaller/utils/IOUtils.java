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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.compressors.CompressorException;

import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.common.InstallerNodeFactory;
import com.fsoinstaller.common.InstallerNodeParseException;

import io.sigpipe.jbsdiff.DefaultDiffSettings;
import io.sigpipe.jbsdiff.Diff;
import io.sigpipe.jbsdiff.DiffSettings;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.Patch;


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
	
	public static int readAllBytes(InputStream inputStream, byte[] byteArray) throws IOException
	{
		return readAllBytes(inputStream, byteArray, 0, byteArray.length);
	}
	
	public static int readAllBytes(InputStream inputStream, byte[] byteArray, int offset, int length) throws IOException
	{
		int originalOffset = offset;
		
		while (length > 0)
		{
			int numRead = inputStream.read(byteArray, offset, length);
			if (numRead <= 0)
				break;
				
			offset += numRead;
			length -= numRead;
		}
		
		return offset - originalOffset;
	}
	
	public static byte[] readBytes(File file) throws FileNotFoundException, IOException
	{
		if (file.length() >= (long) Integer.MAX_VALUE)
			throw new IOException("File is too long to read!");
			
		FileInputStream fis = new FileInputStream(file);
		byte[] byteArray = new byte[(int) file.length()];
		try
		{
			int count = readAllBytes(fis, byteArray);
			if (count != file.length())
				throw new IOException("Failed to read entire file!");
		}
		finally
		{
			fis.close();
		}
		
		return byteArray;
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
		if (!file.exists() || file.isDirectory())
			throw new IllegalArgumentException("File must exist and not be a directory!");
			
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
	
	public static void generatePatch(Diff diff, String patchType, File sourceFile, File targetFile, File patchFile) throws IOException
	{
		if (!sourceFile.exists() || sourceFile.isDirectory())
			throw new IllegalArgumentException("Source file must exist and must not be a directory!");
		if (!targetFile.exists() || targetFile.isDirectory())
			throw new IllegalArgumentException("Target file must exist and must not be a directory!");
		if (patchFile.exists())
			throw new IllegalArgumentException("Patch file must not exist!");
			
		DiffSettings settings = new DefaultDiffSettings(patchType);
		
		logger.debug("Reading source file...");
		byte[] sourceBytes = IOUtils.readBytes(sourceFile);
		logger.debug("Reading target file...");
		byte[] targetBytes = IOUtils.readBytes(targetFile);
		
		FileOutputStream fos = new FileOutputStream(patchFile);
		try
		{
			logger.debug("Performing file diff...");
			diff.diff(sourceBytes, targetBytes, fos, settings);
		}
		catch (CompressorException ce)
		{
			IOException ioe = new IOException("There was a problem creating the compressor");
			ioe.initCause(ce);
			throw ioe;
		}
		catch (InvalidHeaderException ihe)
		{
			IOException ioe = new IOException("Invalid header in patch file");
			ioe.initCause(ihe);
			throw ioe;
		}
		finally
		{
			fos.close();
		}
	}
	
	public static void applyPatch(Patch patch, File sourceFile, File patchFile, File targetFile) throws IOException
	{
		if (!sourceFile.exists() || sourceFile.isDirectory())
			throw new IllegalArgumentException("Source file must exist and must not be a directory!");
		if (!patchFile.exists() || patchFile.isDirectory())
			throw new IllegalArgumentException("Patch file must exist and must not be a directory!");
		if (targetFile.exists())
			throw new IllegalArgumentException("Target file must not exist!");
			
		logger.debug("Reading source file...");
		byte[] sourceBytes = IOUtils.readBytes(sourceFile);
		logger.debug("Reading patch file...");
		byte[] patchBytes = IOUtils.readBytes(patchFile);
		
		FileOutputStream fos = new FileOutputStream(targetFile);
		try
		{
			logger.debug("Performing file patch...");
			patch.patch(sourceBytes, patchBytes, fos);
		}
		catch (CompressorException ce)
		{
			IOException ioe = new IOException("There was a problem creating the compressor");
			ioe.initCause(ce);
			throw ioe;
		}
		catch (InvalidHeaderException ihe)
		{
			IOException ioe = new IOException("Invalid header in patch file");
			ioe.initCause(ihe);
			throw ioe;
		}
		finally
		{
			fos.close();
		}
	}
	
	public static void copy(File from, File to) throws IOException
	{
		FileInputStream fromStream = null;
		FileOutputStream toStream = null;
		
		FileChannel fromChannel = null;
		FileChannel toChannel = null;
		
		try
		{
			fromStream = new FileInputStream(from);
			toStream = new FileOutputStream(to);
			
			fromChannel = fromStream.getChannel();
			toChannel = toStream.getChannel();
			
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
			if (fromStream != null)
			{
				try
				{
					fromStream.close();
				}
				catch (IOException ioe)
				{
					closeException = ioe;
				}
			}
			if (toStream != null)
			{
				try
				{
					toStream.close();
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
		try
		{
			(new FileTraverse<Void>(true)
			{
				@Override
				public Void forDirectory(File dir) throws IOException
				{
					if (!dir.delete())
						throw new IOException("Unable to delete directory '" + dir.getAbsolutePath() + "'");
					return null;
				}
				
				@Override
				public Void forFile(File file) throws IOException
				{
					if (!file.delete())
						throw new IOException("Unable to delete file '" + file.getAbsolutePath() + "'");
					return null;
				}
			}).on(directory);
		}
		catch (IOException ioe)
		{
			logger.error("Directory tree could not be deleted!", ioe);
			return false;
		}
		
		return true;
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
	
	/**
	 * Retrieve a directory listing of all files in the directory, but with the
	 * file names converted to lower-case.
	 */
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
	
	/**
	 * Adjust the complete path of the specified file to include any path
	 * elements (folders or filename) which may differ from the provided file
	 * only by letter case.
	 */
	public static File syncFileLetterCase(File file)
	{
		// Windows is already case-insensitive 
		if (OperatingSystem.getHostOS() == OperatingSystem.WINDOWS)
			return file;
			
		// get all the path elements up to the root
		LinkedList<String> pathElements = new LinkedList<String>();
		do {
			pathElements.addFirst(file.getName());
			file = file.getParentFile();
		} while (file.getParentFile() != null);
		
		// we are now at the root, so reconstruct the path using case-insensitive elements
		for (String pathElement: pathElements)
		{
			// we can only check to see if alternatives exist if we have an existing path
			if (file.exists())
			{
				// it should be a directory because we haven't added the last path element yet
				if (file.isDirectory())
				{
					String[] children = file.list();
					if (children != null)
					{
						// if we have an existing version of this element, ignoring case, use that instead
						for (String child: children)
						{
							if (pathElement.equalsIgnoreCase(child))
							{
								pathElement = child;
								break;
							}
						}
					}
					else
						logger.error("There was an error getting the file listing for '" + file.getAbsolutePath() + "!");
				}
				else
					logger.error("Expected '" + file.getAbsolutePath() + "' to be a directory!");
			}
			
			// keep reconstructing the path
			file = new File(file, pathElement);
		}
		
		return file;
	}
	
	private static final Map<String, String> extensionMap;
	
	static
	{
		Map<String, String> map = new HashMap<String, String>();
		map.put(".tgz", ".tar.gzip");
		map.put(".gz", ".gzip");
		map.put(".tbz", ".tar.bzip2");
		map.put(".tbz2", ".tar.bzip2");
		map.put(".tb2", ".tar.bzip2");
		map.put(".bz2", ".bzip2");
		map.put(".taz", ".tar.z");
		map.put(".tz", ".tar.z");
		map.put(".tlz", ".tar.lzma");
		map.put(".lz", ".lzma");
		map.put(".txz", ".tar.xz");
		extensionMap = Collections.unmodifiableMap(map);
	}
	
	/**
	 * Normalizes certain contracted file extensions to their full form so that
	 * they can be recognized by the 7Zip algorithm and by the extractor.
	 * 
	 * @return the filename with the new extension, e.g. file.tgz =>
	 *         file.tar.gzip
	 */
	public static String normalizeFileExtension(String fileName)
	{
		// see if we even have an extension
		int periodPos = fileName.lastIndexOf('.');
		if (periodPos < 0)
			return fileName;
			
		// see if that extension is one we should replace
		String extension = fileName.substring(periodPos).toLowerCase();
		if (!extensionMap.containsKey(extension))
			return fileName;
			
		// replace it with the new extension in the map
		return fileName.substring(0, periodPos) + extensionMap.get(extension);
	}
}
