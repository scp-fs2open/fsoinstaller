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

package com.fsoinstaller.internet;

import java.awt.EventQueue;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;

import net.sf.sevenzipjbinding.ArchiveFormat;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;

import com.fsoinstaller.common.InputStreamInStream;
import com.fsoinstaller.common.InputStreamSource;
import com.fsoinstaller.common.OutputStreamSequentialOutStream;
import com.fsoinstaller.utils.IOUtils;
import com.fsoinstaller.utils.InstallerUtils;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.MiscUtils;
import com.fsoinstaller.utils.ObjectHolder;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


/**
 * A utility for downloading installation files from the Internet, either
 * directly or from within a compressed archive. Rewritten from a similar class
 * originally created by Turey.
 * <p>
 * This class should be thread-safe.
 * 
 * @author Turey
 * @author Goober5000
 */
public class Downloader
{
	private static final Logger defaultLogger = Logger.getLogger(Downloader.class);
	
	protected static final int BUFFER_SIZE = 2048;
	
	// the user can configure the number of download slots
	protected static final Semaphore downloadPermits;
	static
	{
		int num = 4;
		
		// maybe parse the user option
		try
		{
			String val = System.getProperty("maxParallelDownloads");
			if (val != null)
				num = Integer.parseInt(val);
		}
		catch (NumberFormatException nfe)
		{
			defaultLogger.error("Couldn't parse maxParallelDownloads!", nfe);
		}
		
		// sanity
		if (num < 1)
		{
			defaultLogger.warn("maxParallelDownloads must be at least 1!");
			num = 1;
		}
		
		// allocate that many permits
		defaultLogger.info("Setting maxParallelDownloads to " + num);
		downloadPermits = new Semaphore(num, true);
	}
	
	protected final List<DownloadListener> downloadListeners;
	protected final Connector connector;
	protected final URL sourceURL;
	protected final File destination;
	protected final byte[] downloadBuffer;
	
	protected final ObjectHolder<DownloadState> stateHolder;
	protected final Logger logger;
	protected Thread downloadThread;
	
	// these are kept as member variables in the event of failure during 7Zip download
	protected File extractingFile = null;
	protected OutputStreamSequentialOutStream extractingOutStream = null;
	
	public Downloader(Connector connector, URL sourceURL, File destination)
	{
		this(connector, sourceURL, destination, null);
	}
	
	public Downloader(Connector connector, URL sourceURL, File destination, String modName)
	{
		this.connector = connector;
		this.sourceURL = sourceURL;
		this.destination = destination;
		this.downloadBuffer = new byte[BUFFER_SIZE];
		
		this.stateHolder = new ObjectHolder<DownloadState>(DownloadState.INITIALIZED);
		this.logger = (MiscUtils.isEmpty(modName) ? defaultLogger : Logger.getLogger(Downloader.class, modName));
		this.downloadThread = null;
		
		// woot, CopyOnWriteArrayList is A-1 SUPAR as a listener list;
		// see http://www.ibm.com/developerworks/java/library/j-jtp07265/index.html
		this.downloadListeners = new CopyOnWriteArrayList<DownloadListener>();
	}
	
	public boolean download()
	{
		// wait for a download slot
		try
		{
			downloadPermits.acquire();
		}
		catch (InterruptedException ie)
		{
			logger.error("Thread was interrupted while waiting for a download slot!", ie);
			Thread.currentThread().interrupt();
			return false;
		}
		
		// perform the download
		try
		{
			return download0();
		}
		// release the slot when we are done, whatever happens
		finally
		{
			downloadPermits.release();
		}
	}
	
	protected boolean download0()
	{
		synchronized (stateHolder)
		{
			DownloadState state = stateHolder.get();
			if (state == DownloadState.INITIALIZED)
			{
				downloadThread = Thread.currentThread();
				stateHolder.set(DownloadState.RUNNING);
			}
			else if (state == DownloadState.CANCELLED)
			{
				// interrupt the thread, since we couldn't do that before
				downloadThread = Thread.currentThread();
				downloadThread.interrupt();
				
				logger.info("Not starting download due to prior cancellation!");
				return false;
			}
			else
			{
				logger.error("Cannot download; downloader's current state is " + state);
				return false;
			}
		}
		
		Boolean result = null;
		
		// if downloading to a directory, put the source file inside it with the same name
		// (or names, in the case of an archive)
		if (destination.isDirectory())
		{
			File destinationDirectory = destination;
			String sourceFileName = new File(sourceURL.getPath()).getName();
			
			// normalize any contracted file extension we may have
			String normalized = IOUtils.normalizeFileExtension(sourceFileName);
			
			// now grab the extension
			int periodPos = normalized.lastIndexOf('.');
			String extension = (periodPos >= 0) ? normalized.substring(periodPos + 1) : "";
			
			// make sure 7zip is ready to go
			MiscUtils.initSevenZip();
			
			// see if this file is a 7zip-supported archive, including .zip
			for (ArchiveFormat format: ArchiveFormat.values())
			{
				if (format.getMethodName().equalsIgnoreCase(extension))
				{
					// if this is a compressed .tar file, create a holder for the temporary .tar file name
					ObjectHolder<String> tarResultHolder = null;
					if (periodPos >= 0 && normalized.substring(0, periodPos).toLowerCase().endsWith(".tar"))
						tarResultHolder = new ObjectHolder<String>();
					
					result = downloadFromArchive(sourceURL, destinationDirectory, format, tarResultHolder);
					
					// if we ended up with a tar archive, extract that too
					if (result.booleanValue() && tarResultHolder != null && tarResultHolder.get() != null)
					{
						File tarFile = new File(destinationDirectory, tarResultHolder.get());
						if (tarFile.exists())
						{
							try
							{
								result = downloadFromArchive(tarFile.toURI().toURL(), destinationDirectory, ArchiveFormat.TAR, null);
								
								if (result.booleanValue() && !tarFile.delete())
									logger.warn("TAR file was not deleted...");
							}
							catch (MalformedURLException murle)
							{
								logger.error("Could not extract from '" + tarFile.getName() + "'!");
							}
						}
					}
					
					// this extension matched: no need to check any other extensions
					break;
				}
			}
			
			// not an archive (or an archive that 7zip knows how to extract), so download as a standard file
			if (result == null)
				result = downloadFile(sourceURL, new File(destinationDirectory, sourceFileName));
		}
		// if downloading to a file, copy the source file and use the destination name
		else
		{
			result = downloadFile(sourceURL, destination);
		}
		
		// we are done, so set the state
		synchronized (stateHolder)
		{
			// it's possible that we were cancelled in the middle of all this
			if (stateHolder.get() != DownloadState.CANCELLED)
				stateHolder.set(DownloadState.COMPLETED);
		}
		
		return result.booleanValue();
	}
	
	public void cancel()
	{
		synchronized (stateHolder)
		{
			DownloadState state = stateHolder.get();
			if (state == DownloadState.CANCELLED || state == DownloadState.COMPLETED)
			{
				// don't re-do a cancellation, and don't fail something that's already complete
				return;
			}
			else
			{
				logger.warn("Cancelling download!");
				stateHolder.set(DownloadState.CANCELLED);
				
				// maybe interrupt the thread				
				if (downloadThread != null)
				{
					logger.debug("Interrupting thread '" + downloadThread.getName() + "'");
					downloadThread.interrupt();
				}
				
				fireDownloadCancelled(sourceURL.getFile(), -1, -1, null);
			}
		}
	}
	
	protected boolean downloadFile(URL sourceURL, File destinationFile)
	{
		logger.info("Downloading from " + sourceURL + " to local file " + destinationFile);
		
		long totalBytes = 0;
		long lastModified = -1;
		InputStream inputStream = null;
		OutputStream outputStream = null;
		try
		{
			totalBytes = connector.getContentLength(sourceURL);
			lastModified = connector.getLastModified(sourceURL);
			
			logger.debug("Opening connection to file...");
			URLConnection connection = connector.openConnection(sourceURL);
			
			logger.debug("Checking if the file is up to date...");
			if (uptodate(destinationFile, totalBytes))
			{
				fireNoDownloadNecessary(destinationFile.getName(), 0, totalBytes);
				return true;
			}
			
			logger.debug("Opening input and output streams...");
			inputStream = connection.getInputStream();
			outputStream = openOutputStream(destinationFile);
			
			downloadUsingStreams(inputStream, outputStream, destinationFile.getName(), totalBytes);
			
			logger.debug("Closing output stream...");
			outputStream.close();
			outputStream = null;
			if (lastModified > 0 && !destinationFile.setLastModified(lastModified))
				logger.warn("Could not set file modification time for '" + destinationFile.getAbsolutePath() + "'!");
			
			logger.debug("Closing input stream...");
			inputStream.close();
			inputStream = null;
			
			return true;
		}
		catch (IOException ioe)
		{
			logger.error("An exception was thrown during download!", ioe);
			fireDownloadFailed(destinationFile.getName(), 0, totalBytes, ioe);
			
			return false;
		}
		catch (InterruptedException ie)
		{
			logger.warn("The download was interrupted!", ie);
			fireDownloadCancelled(destinationFile.getName(), 0, totalBytes, ie);
			
			// try to delete incomplete file
			cleanup(inputStream, outputStream);
			inputStream = null;
			outputStream = null;
			if (!destinationFile.delete())
				logger.warn("Could not delete incompletely downloaded file '" + destinationFile.getAbsolutePath() + "'!");
			
			// restore interrupt and exit
			Thread.currentThread().interrupt();
			return false;
		}
		finally
		{
			cleanup(inputStream, outputStream);
		}
	}
	
	protected boolean downloadFromArchive(URL sourceURL, File destinationDirectory, ArchiveFormat format, ObjectHolder<String> tarResultHolder)
	{
		logger.info("Downloading and extracting from " + sourceURL + " to local directory " + destinationDirectory);
		String sourceFileName = new File(sourceURL.getPath()).getName();
		
		String currentEntry = "";
		long totalBytes = 0;
		IInArchive archive = null;
		InputStreamInStream inStream = null;
		IArchiveExtractCallback callback = null;
		try
		{
			totalBytes = connector.getContentLength(sourceURL);
			
			logger.debug("Opening connection to archive...");
			inStream = new InputStreamInStream(getInputStreamSource(connector, sourceURL, totalBytes), totalBytes);
			archive = SevenZip.openInArchive(format, inStream);
			int numItems = archive.getNumberOfItems();
			
			List<Integer> extractionIndexes = new ArrayList<Integer>();
			String[] archiveEntries = new String[numItems];
			long[] archiveSizes = new long[numItems];
			long[] archiveModifiedTimes = new long[numItems];
			
			for (int item = 0; item < numItems; item++)
			{
				String pathProp = archive.getStringProperty(item, PropID.PATH);
				// this can happen in formats that only zip a single file
				if (pathProp == null || pathProp.equals(""))
				{
					logger.debug("Path property of item " + item + " is empty!");
					
					// if we're expecting a .tar, create a temporary file name and use that
					if (tarResultHolder != null)
					{
						pathProp = InstallerUtils.UUID() + ".tar";
						tarResultHolder.set(pathProp);
					}
					// we're not expecting a .tar, so this is (e.g.) a gzipped single file
					else
					{
						// use the source URL's filename as the name of the extracted file...
						// (we'll need to normalize the extension again)
						String normalized = IOUtils.normalizeFileExtension(sourceFileName);
						// ...but chop off the extension (we know we have an extension since this was recognized as an archive)
						pathProp = normalized.substring(0, normalized.lastIndexOf('.'));
					}
					
					logger.debug("Using '" + pathProp + "' as path property");
				}
				currentEntry = archiveEntries[item] = pathProp;
				
				Long sizeProp = (Long) archive.getProperty(item, PropID.SIZE);
				// this can happen in formats that only zip a single file
				if (sizeProp == null)
					sizeProp = totalBytes;
				totalBytes = archiveSizes[item] = sizeProp;
				
				Date dateProp = (Date) archive.getProperty(item, PropID.LAST_MODIFICATION_TIME);
				archiveModifiedTimes[item] = dateProp == null ? -1 : dateProp.getTime();
				
				logger.debug("Checking entry '" + currentEntry + "'");
				Boolean folderProp = (Boolean) archive.getProperty(item, PropID.IS_FOLDER);
				if (folderProp != null && folderProp.booleanValue())
				{
					continue;
				}
				
				logger.debug("Checking if the file is up to date...");
				File destinationFile = IOUtils.syncFileLetterCase(new File(destinationDirectory, currentEntry));
				if (uptodate(destinationFile, totalBytes))
				{
					fireNoDownloadNecessary(destinationFile.getName(), 0, totalBytes);
					continue;
				}
				
				// we will extract this item
				extractionIndexes.add(item);
			}
			
			if (extractionIndexes.size() > 0)
			{
				logger.debug("Opening extractor...");
				callback = getExtractCallback(destinationDirectory, archiveEntries, archiveSizes, archiveModifiedTimes);
				
				// extract them all at once
				int[] items = new int[extractionIndexes.size()];
				for (int i = 0; i < extractionIndexes.size(); i++)
					items[i] = extractionIndexes.get(i);
				
				// do the extraction and watch for InterruptedException since the 7Zip-JBinding API doesn't explicitly declare it
				try
				{
					archive.extract(items, false, callback);
				}
				catch (SevenZipException sze)
				{
					if (sze.getCause() instanceof InterruptedException)
						throw (InterruptedException) sze.getCause();
					else if (sze.getCause() != null && sze.getCause().getCause() instanceof InterruptedException)
						throw (InterruptedException) sze.getCause().getCause();
					else
						throw sze;
				}
			}
			
			logger.debug("Closing archive...");
			archive.close();
			archive = null;
			
			logger.debug("Closing input stream...");
			inStream.close();
			inStream = null;
			
			return true;
		}
		catch (SevenZipException sze)
		{
			if (extractingFile != null)
				currentEntry = extractingFile.getName();
			else if (sourceFileName != null)
				currentEntry = sourceFileName;
			else
				currentEntry = "";
			
			logger.error("An exception was thrown during download!", sze);
			fireDownloadFailed(currentEntry, 0, totalBytes, sze);
			
			return false;
		}
		catch (IOException ioe)
		{
			if (extractingFile != null)
				currentEntry = extractingFile.getName();
			else if (sourceFileName != null)
				currentEntry = sourceFileName;
			else
				currentEntry = "";
			
			logger.error("An exception was thrown during download!", ioe);
			fireDownloadFailed(currentEntry, 0, totalBytes, ioe);
			
			return false;
		}
		catch (InterruptedException ie)
		{
			currentEntry = (extractingFile != null) ? extractingFile.getName() : "";
			
			logger.warn("The download was interrupted!", ie);
			fireDownloadCancelled(currentEntry, 0, totalBytes, ie);
			
			// try to delete incomplete file
			cleanup(archive, inStream);
			archive = null;
			inStream = null;
			cleanup(extractingOutStream);
			extractingOutStream = null;
			if (extractingFile != null && !extractingFile.delete())
				logger.warn("Could not delete incompletely downloaded file '" + extractingFile.getAbsolutePath() + "'!");
			
			// restore interrupt and exit
			Thread.currentThread().interrupt();
			return false;
		}
		finally
		{
			cleanup(archive, inStream);
			cleanup(extractingOutStream);
		}
	}
	
	protected OutputStream openOutputStream(File file) throws IOException
	{
		logger.debug("output file: " + file.getAbsolutePath());
		
		if (!file.getParentFile().exists())
		{
			logger.debug("parent directory not found; creating it");
			if (!file.getParentFile().mkdirs())
				throw new IOException("Failed to create parent directory for '" + file.getAbsolutePath() + "'");
		}
		
		if (!file.exists())
		{
			logger.debug("local file not found; creating it");
			if (!file.createNewFile())
				throw new IOException("Failed to create new file '" + file.getAbsolutePath() + "'!");
		}
		
		return new BufferedOutputStream(new FileOutputStream(file));
	}
	
	protected InputStreamSource getInputStreamSource(Connector connector, URL sourceURL, long totalBytes)
	{
		final Connector _connector = connector;
		final URL _sourceURL = sourceURL;
		final long _totalBytes = totalBytes;
		
		return new InputStreamSource()
		{
			private static final int MAX_SKIP_TRIES = 10;
			
			public InputStream recycleInputStream(InputStream oldInputStream, long position) throws IOException, IndexOutOfBoundsException
			{
				if (position < 0)
					throw new IndexOutOfBoundsException("Position cannot be negative!");
				if (position >= _totalBytes)
					throw new IndexOutOfBoundsException("Position is beyond the end of the stream!");
				
				if (oldInputStream != null)
				{
					try
					{
						logger.debug("Closing old input stream...");
						oldInputStream.close();
					}
					catch (IOException ioe)
					{
						logger.warn("Could not close download stream!", ioe);
					}
				}
				
				// open a new stream at the correct position
				// (the implementations of URLConnection will cache and pool connections as needed)
				URLConnection connection = _connector.openConnection(_sourceURL);
				if (connection instanceof HttpURLConnection)
				{
					if (position > 0)
						connection.setRequestProperty("Range", "bytes=" + position + "-");
				}
				
				logger.debug("Opening new input stream...");
				InputStream newInputStream = connection.getInputStream();
				
				// see if we got to the position we wanted to
				if (connection instanceof HttpURLConnection)
				{
					if (position > 0 && ((HttpURLConnection) connection).getResponseCode() != HttpURLConnection.HTTP_PARTIAL)
						throw new IOException("The site at " + _sourceURL + " does not support returning partial content!  HTTP response code = " + ((HttpURLConnection) connection).getResponseCode());
				}
				// we couldn't open the stream right at the place we wanted, but let's see if we can jump to it
				else
				{
					long offset = position;
					
					// try skip-seek
					int tries = 0;
					while (offset > 0 && tries < MAX_SKIP_TRIES)
					{
						long skipped = newInputStream.skip(offset);
						if (skipped > 0)
							offset -= skipped;
						else
							tries++;
					}
				}
				
				return newInputStream;
			}
		};
	}
	
	protected IArchiveExtractCallback getExtractCallback(File destinationDirectory, String[] archiveEntries, long[] archiveSizes, long[] archiveModifiedTimes)
	{
		final File _destinationDirectory = destinationDirectory;
		final String[] _archiveEntries = archiveEntries;
		//final long[] _archiveSizes = archiveSizes;
		final long[] _archiveModifiedTimes = archiveModifiedTimes;
		
		return new IArchiveExtractCallback()
		{
			private long archiveCompletionValue = 0;
			private long archiveTotalValue = 0;
			
			private int currentIndex = -1;
			private ExtractAskMode currentExtractMode;
			
			public ISequentialOutStream getStream(int index, ExtractAskMode extractAskMode) throws SevenZipException
			{
				// check for thread interruption
				if (Thread.interrupted())
					throw new SevenZipException(new InterruptedException("Thread was interrupted during 7Zip extraction"));
				
				currentIndex = index;
				currentExtractMode = extractAskMode;
				
				switch (extractAskMode)
				{
					case EXTRACT:
						try
						{
							logger.debug("Opening output stream...");
							extractingFile = IOUtils.syncFileLetterCase(new File(_destinationDirectory, _archiveEntries[index]));
							extractingOutStream = new OutputStreamSequentialOutStream(openOutputStream(extractingFile));
						}
						catch (IOException ioe)
						{
							throw new SevenZipException("Error opening output stream", ioe);
						}
						break;
					
					case TEST:
						throw new UnsupportedOperationException("Testing of archives not supported");
					
					case SKIP:
						extractingFile = null;
						extractingOutStream = null;
						break;
					
					default:
						throw new IllegalArgumentException("Unknown ask mode");
				}
				
				return extractingOutStream;
			}
			
			public void prepareOperation(ExtractAskMode extractAskMode) throws SevenZipException
			{
				// check for thread interruption
				if (Thread.interrupted())
					throw new SevenZipException(new InterruptedException("Thread was interrupted during 7Zip extraction"));
				
				if (extractAskMode == ExtractAskMode.EXTRACT)
				{
					logger.debug("Downloading...");
					fireAboutToStart(_archiveEntries[currentIndex], archiveCompletionValue, archiveTotalValue);
				}
				else if (extractAskMode == ExtractAskMode.SKIP)
				{
					logger.debug("Skipping...");
				}
			}
			
			public void setOperationResult(ExtractOperationResult extractOperationResult) throws SevenZipException
			{
				// check for thread interruption
				if (Thread.interrupted())
					throw new SevenZipException(new InterruptedException("Thread was interrupted during 7Zip extraction"));
				
				// if an entry actually produced an error, we should throw an exception
				SevenZipException exception = null;
				
				switch (extractOperationResult)
				{
					case OK:
						if (currentExtractMode == ExtractAskMode.EXTRACT)
						{
							logger.debug("Download complete");
							fireDownloadComplete(_archiveEntries[currentIndex], archiveCompletionValue, archiveTotalValue);
						}
						break;
					
					case UNSUPPORTEDMETHOD:
						logger.warn("Extraction failed due to unknown compression method!");
						exception = new SevenZipException("Unknown compression method");
						if (currentIndex >= 0)
							fireDownloadFailed(_archiveEntries[currentIndex], archiveCompletionValue, archiveTotalValue, exception);
						break;
					
					case DATAERROR:
						logger.warn("Extraction failed due to data error!");
						exception = new SevenZipException("Data error");
						if (currentIndex >= 0)
							fireDownloadFailed(_archiveEntries[currentIndex], archiveCompletionValue, archiveTotalValue, exception);
						break;
					
					case CRCERROR:
						logger.warn("Extraction failed due to CRC error!");
						exception = new SevenZipException("CRC error");
						if (currentIndex >= 0)
							fireDownloadFailed(_archiveEntries[currentIndex], archiveCompletionValue, archiveTotalValue, exception);
						break;
					
					default:
						exception = new SevenZipException("Unknown operation result: " + extractOperationResult.name());
				}
				
				if (extractingOutStream != null)
				{
					try
					{
						logger.debug("Closing output stream...");
						extractingOutStream.close();
						if (_archiveModifiedTimes[currentIndex] > 0 && !extractingFile.setLastModified(_archiveModifiedTimes[currentIndex]))
							logger.warn("Could not set file modification time for '" + extractingFile.getAbsolutePath() + "'!");
					}
					catch (IOException ioe)
					{
						logger.warn("Could not close file stream!", ioe);
					}
					finally
					{
						extractingFile = null;
						extractingOutStream = null;
					}
				}
				
				if (exception != null)
					throw exception;
			}
			
			public void setCompleted(long completeValue) throws SevenZipException
			{
				// check for thread interruption
				if (Thread.interrupted())
					throw new SevenZipException(new InterruptedException("Thread was interrupted during 7Zip extraction"));
				
				archiveCompletionValue = completeValue;
				if (currentExtractMode == ExtractAskMode.EXTRACT)
					fireProgressReport(_archiveEntries[currentIndex], archiveCompletionValue, archiveTotalValue);
				else
					fireProgressReport(XSTR.getString("progressBarWorking2"), archiveCompletionValue, archiveTotalValue);
			}
			
			public void setTotal(long total) throws SevenZipException
			{
				// check for thread interruption
				if (Thread.interrupted())
					throw new SevenZipException(new InterruptedException("Thread was interrupted during 7Zip extraction"));
				
				archiveTotalValue = total;
			}
		};
	}
	
	protected void downloadUsingStreams(InputStream inputStream, OutputStream outputStream, String downloadName, long downloadTotalSize) throws IOException, InterruptedException
	{
		long totalBytesWritten = 0;
		
		logger.debug("Downloading...");
		fireAboutToStart(downloadName, totalBytesWritten, downloadTotalSize);
		
		int bytesRead = 0;
		while ((bytesRead = inputStream.read(downloadBuffer)) != -1)
		{
			// check for thread interruption
			if (Thread.interrupted())
				throw new InterruptedException("Thread was interrupted during stream reading");
			
			outputStream.write(downloadBuffer, 0, bytesRead);
			totalBytesWritten += bytesRead;
			
			// check for thread interruption
			if (Thread.interrupted())
				throw new InterruptedException("Thread was interrupted during stream writing");
			
			fireProgressReport(downloadName, totalBytesWritten, downloadTotalSize);
		}
		
		logger.debug("Download complete");
		fireDownloadComplete(downloadName, totalBytesWritten, downloadTotalSize);
	}
	
	protected boolean uptodate(File destinationFile, long totalBytes)
	{
		return destinationFile.exists() && (totalBytes > 0) && (destinationFile.length() == totalBytes);
	}
	
	protected void cleanup(InputStream inputStream, OutputStream outputStream)
	{
		if (outputStream != null)
		{
			try
			{
				outputStream.close();
			}
			catch (IOException ioe)
			{
				logger.warn("Could not close file stream!", ioe);
			}
		}
		
		if (inputStream != null)
		{
			try
			{
				inputStream.close();
			}
			catch (IOException ioe)
			{
				logger.warn("Could not close download stream!", ioe);
			}
		}
	}
	
	protected void cleanup(IInArchive archive, InputStreamInStream inStream)
	{
		if (archive != null)
		{
			try
			{
				archive.close();
			}
			catch (SevenZipException sze)
			{
				logger.warn("Could not close archive!", sze);
			}
		}
		
		if (inStream != null)
		{
			try
			{
				inStream.close();
			}
			catch (IOException ioe)
			{
				logger.warn("Could not close download stream!", ioe);
			}
		}
	}
	
	protected void cleanup(OutputStreamSequentialOutStream outStream)
	{
		if (outStream != null)
		{
			try
			{
				outStream.close();
			}
			catch (IOException ioe)
			{
				logger.warn("Could not close output stream!", ioe);
			}
		}
	}
	
	public void addDownloadListener(DownloadListener listener)
	{
		downloadListeners.add(listener);
	}
	
	public void removeDownloadListener(DownloadListener listener)
	{
		downloadListeners.remove(listener);
	}
	
	protected void fireNoDownloadNecessary(final String downloadName, final long downloadedBytes, final long totalBytes)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				DownloadEvent event = null;
				for (DownloadListener listener: downloadListeners)
				{
					// lazy instantiation of the event
					if (event == null)
						event = new DownloadEvent(this, downloadName, downloadedBytes, totalBytes);
					
					// fire it
					listener.downloadNotNecessary(event);
				}
			}
		});
	}
	
	protected void fireAboutToStart(final String downloadName, final long downloadedBytes, final long totalBytes)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				DownloadEvent event = null;
				for (DownloadListener listener: downloadListeners)
				{
					// lazy instantiation of the event
					if (event == null)
						event = new DownloadEvent(this, downloadName, downloadedBytes, totalBytes);
					
					// fire it
					listener.downloadAboutToStart(event);
				}
			}
		});
	}
	
	protected void fireProgressReport(final String downloadName, final long downloadedBytes, final long totalBytes)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				DownloadEvent event = null;
				for (DownloadListener listener: downloadListeners)
				{
					// lazy instantiation of the event
					if (event == null)
						event = new DownloadEvent(this, downloadName, downloadedBytes, totalBytes);
					
					// fire it
					listener.downloadProgressReport(event);
				}
			}
		});
	}
	
	protected void fireDownloadComplete(final String downloadName, final long downloadedBytes, final long totalBytes)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				DownloadEvent event = null;
				for (DownloadListener listener: downloadListeners)
				{
					// lazy instantiation of the event
					if (event == null)
						event = new DownloadEvent(this, downloadName, downloadedBytes, totalBytes);
					
					// fire it
					listener.downloadComplete(event);
				}
			}
		});
	}
	
	protected void fireDownloadFailed(final String downloadName, final long downloadedBytes, final long totalBytes, final Exception exception)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				DownloadEvent event = null;
				for (DownloadListener listener: downloadListeners)
				{
					// lazy instantiation of the event
					if (event == null)
						event = new DownloadEvent(this, downloadName, downloadedBytes, totalBytes, exception);
					
					// fire it
					listener.downloadFailed(event);
				}
			}
		});
	}
	
	protected void fireDownloadCancelled(final String downloadName, final long downloadedBytes, final long totalBytes, final Exception exception)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				DownloadEvent event = null;
				for (DownloadListener listener: downloadListeners)
				{
					// lazy instantiation of the event
					if (event == null)
						event = new DownloadEvent(this, downloadName, downloadedBytes, totalBytes);
					
					// fire it
					listener.downloadCancelled(event);
				}
			}
		});
	}
	
	protected static enum DownloadState
	{
		INITIALIZED,
		RUNNING,
		COMPLETED,
		CANCELLED
	}
}
