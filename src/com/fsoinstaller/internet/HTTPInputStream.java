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

package com.fsoinstaller.internet;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import com.fsoinstaller.utils.Logger;


/**
 * This class mirrors InputStreamInStream but removes the IInStream
 * functionality and ties itself specifically to HTTP. It is currently unused in
 * the codebase; see the following note.
 * <p>
 * NOTE: The original intention was to use this for ZipInputStream, but the
 * ZipInputStream implementation in the JDK appears to be broken. It's not
 * thread safe even for independent files because it apparently uses shared
 * static variables.
 * 
 * @author Goober5000
 */
public class HTTPInputStream extends InputStream
{
	private static final Logger logger = Logger.getLogger(HTTPInputStream.class);
	
	private static final int defaultBufferSize = 8192;
	private static final int MAX_SEEK_TRIES = 10;
	
	protected final Connector connector;
	protected final URL sourceURL;
	protected InputStream currentInputStream;
	
	protected final byte[] buffer;
	protected final int bufferMiddle;
	protected long bufferPos;
	protected long overallPos;
	protected int bufferCount;
	protected final long overallCount;
	
	public HTTPInputStream(Connector connector, URL sourceURL, long totalBytes)
	{
		this(connector, sourceURL, totalBytes, defaultBufferSize);
	}
	
	public HTTPInputStream(Connector connector, URL sourceURL, long totalBytes, int bufferSize)
	{
		if (totalBytes >= Integer.MAX_VALUE)
			throw new IllegalArgumentException("HTTPInputStream has not been tested for sizes above Integer.MAX_VALUE (size = " + totalBytes + ")");
		
		if (connector == null)
			throw new NullPointerException("Connector must not be null!");
		if (sourceURL == null)
			throw new NullPointerException("SourceURL must not be null!");
		if (totalBytes < 0)
			throw new IllegalArgumentException("Overall size must not be negative!");
		if (bufferSize <= 1)
			throw new IllegalArgumentException("Buffer size must be greater than 1!");
		
		this.connector = connector;
		this.sourceURL = sourceURL;
		this.currentInputStream = null;
		
		this.buffer = new byte[bufferSize];
		this.bufferMiddle = buffer.length / 2;
		this.bufferCount = 0;
		this.bufferPos = 0;
		this.overallPos = 0;
		this.overallCount = totalBytes;
	}
	
	private void fillBuffer() throws IOException
	{
		// we no longer remember those bytes, so we have to restart
		if (bufferPos < 0)
		{
			// get to the correct stream position
			currentInputStream = recycleInputStream(currentInputStream, overallPos);
			
			// reset the buffer
			bufferPos = 0;
			bufferCount = 0;
		}
		// if we're at the beginning of the buffer, we don't need to adjust it
		else if (bufferPos == 0)
		{
			// do nothing
		}
		// we overran the buffer and need to catch up
		else if (bufferPos >= bufferCount)
		{
			// special case: if we're within one buffer of the end
			if (overallPos >= overallCount - buffer.length)
			{
				// calculate amount we'd need to seek
				int newBufferPos = (int) (overallPos - (overallCount - buffer.length));
				int offset = (int) (bufferPos - bufferCount) - newBufferPos;
				
				// it's possible that we've arrived in bounds already
				if (offset < 0)
				{
					// finish the buffer
					// (the buffer info will be properly adjusted below)
					readFully(buffer, -offset, buffer.length + offset);
				}
				// not there yet
				else
				{
					// seek, then read the whole buffer
					seekForward(offset, overallCount - buffer.length);
					readFully(buffer, 0, buffer.length);
				}
				
				// now set the new buffer info
				bufferPos = newBufferPos;
				bufferCount = buffer.length;
				return;
			}
			
			// get to the correct stream position
			seekForward(bufferPos - bufferCount, overallPos);
			
			// reset the buffer
			bufferPos = 0;
			bufferCount = 0;
		}
		// keep the pointer in the middle of the buffer, so that we can seek both forwards and backwards
		else if (bufferPos > bufferMiddle)
		{
			int shift = ((int) bufferPos) - bufferMiddle;
			bufferCount -= shift;
			bufferPos -= shift;
			System.arraycopy(buffer, shift, buffer, 0, bufferCount);
		}
		
		if (currentInputStream == null)
			currentInputStream = recycleInputStream(null, 0);
		
		// try to read the rest of the buffer
		int bytesRead = currentInputStream.read(buffer, bufferCount, buffer.length - bufferCount);
		if (bytesRead > 0)
			bufferCount += bytesRead;
	}
	
	private void seekForward(long offset, long absolute) throws IOException
	{
		int tries;
		
		if (offset == 0)
			return;
		else if (offset < 0)
			throw new IllegalArgumentException("This method is only for seeking forward");
		
		// use a heuristic to determine whether we should seek by using the stream's seek method or by relocating the stream
		if (offset < defaultBufferSize)
		{
			// try skip-seek
			tries = 0;
			while (offset > 0 && tries < MAX_SEEK_TRIES)
			{
				long skipped = currentInputStream.skip(offset);
				if (skipped > 0)
					offset -= skipped;
				else
					tries++;
			}
			
			// try read-seek
			tries = 0;
			while (offset > 0 && tries < MAX_SEEK_TRIES)
			{
				long read = currentInputStream.read(buffer, 0, (offset < buffer.length) ? (int) offset : buffer.length);
				if (read > 0)
					offset -= read;
				else
					tries++;
			}
			
			if (offset > 0)
				throw new IOException("Number of seek attempts exceeded MAX_SEEK_TRIES");
		}
		// reload the stream at the new position
		else
		{
			currentInputStream = recycleInputStream(currentInputStream, absolute);
		}
	}
	
	private void readFully(byte[] array, int start, int length) throws IOException
	{
		int tries;
		
		if (length == 0)
			return;
		else if (length - start > array.length)
			throw new IllegalArgumentException("Bytes from start to length must fit into the array!");
		
		// read it
		tries = 0;
		while (length > 0 && tries < MAX_SEEK_TRIES)
		{
			int read = currentInputStream.read(array, start, length);
			if (read > 0)
			{
				length -= read;
				start += read;
			}
			else
				tries++;
		}
		
		if (length > 0)
			throw new IOException("Number of read tries exceeded MAX_SEEK_TRIES");
	}
	
	protected int readInternal(byte[] data) throws IOException
	{
		if (data.length == 0)
			return 0;
		
		if (overallPos < 0)
			throw new IOException("Can't read a negative stream position!");
		else if (overallPos >= overallCount)
			return 0;
		
		// ensure buffer is available
		if (bufferPos < 0 || bufferPos >= bufferCount)
		{
			try
			{
				fillBuffer();
			}
			catch (IOException ioe)
			{
				IOException ioe2 = new IOException("Error reading input stream");
				ioe2.initCause(ioe);
				throw ioe2;
			}
			
			// unable to read more bytes
			if (bufferPos >= bufferCount)
				return 0;
		}
		
		// we want to get as many bytes as possible, but not more than we can hold
		int available = bufferCount - (int) bufferPos;
		if (available > data.length)
			available = data.length;
		
		// copy them
		System.arraycopy(buffer, (int) bufferPos, data, 0, available);
		bufferPos += available;
		overallPos += available;
		
		return available;
	}
	
	@Override
	public void close() throws IOException
	{
		if (currentInputStream != null)
		{
			currentInputStream.close();
			currentInputStream = null;
		}
	}
	
	/**
	 * Taken from the SampleInputStreamSource and Downloader implementations of
	 * InputStreamSource.
	 */
	protected InputStream recycleInputStream(InputStream oldInputStream, long position) throws IOException, IndexOutOfBoundsException
	{
		if (position < 0)
			throw new IndexOutOfBoundsException("Position cannot be negative!");
		if (position >= overallCount)
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
		// (the implementations of HttpURLConnection will cache and pool connections as needed)
		URLConnection connection = connector.openConnection(sourceURL);
		if (connection instanceof HttpURLConnection)
		{
			if (position > 0)
				connection.setRequestProperty("Range", "bytes=" + position + "-");
		}
		
		logger.debug("Opening new input stream...");
		InputStream newInputStream = connection.getInputStream();
		
		if (connection instanceof HttpURLConnection)
		{
			if (position > 0 && ((HttpURLConnection) connection).getResponseCode() != HttpURLConnection.HTTP_PARTIAL)
				throw new IOException("The site at " + sourceURL + " does not support returning partial content!  HTTP response code = " + ((HttpURLConnection) connection).getResponseCode());
		}
		else
		{
			newInputStream.skip(position);
		}
		
		return newInputStream;
	}
	
	// ---------- methods needed by InputStream ----------
	
	@Override
	public int read() throws IOException
	{
		byte[] b = new byte[1];
		
		int result = readInternal(b);
		if (result == 0)
		{
			if (overallPos >= overallCount || bufferPos >= bufferCount)
				return -1;
			throw new IOException("Read 0 bytes, but there are still more left in the stream?");
		}
		
		return b[0];
	}
	
	@Override
	public int read(byte b[], int off, int len) throws IOException
	{
		if (b == null)
			throw new NullPointerException("Can't have a null byte array!");
		else if (off < 0 || len < 0 || len > b.length - off)
			throw new IndexOutOfBoundsException("Offset " + off + " and length " + len + " not valid for byte array with size " + b.length);
		else if (len == 0)
			return 0;
		
		byte[] bInternal = new byte[len];
		
		int result = readInternal(bInternal);
		if (result == 0)
		{
			if (overallPos >= overallCount || bufferPos >= bufferCount)
				return -1;
			throw new IOException("Read 0 bytes, but there is still more left in the stream?");
		}
		
		System.arraycopy(bInternal, 0, b, off, len);
		return len;
	}
	
	@Override
	public long skip(long n) throws IOException
	{
		if (n <= 0)
			return 0;
		
		if (overallPos + n > overallCount)
			n = (overallCount - overallPos);
		
		bufferPos += n;
		overallPos += n;
		
		return n;
	}
	
	@Override
	public int available() throws IOException
	{
		return bufferCount - (int) bufferPos;
	}
}
