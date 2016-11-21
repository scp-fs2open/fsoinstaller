package org.apache.commons.compress.compressors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.itadaki.bzip2.BZip2InputStream;
import org.itadaki.bzip2.BZip2OutputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.SingleXZInputStream;
import org.tukaani.xz.XZOutputStream;

import com.fsoinstaller.utils.IOUtils;

/**
 * This is a substitute for the Apache class that uses the same API.  The JBSDiff library was released under the MIT license,
 * but it relies on Apache Commons Compress which uses the Apache 2 license. :-/
 */
public class CompressorStreamFactory
{
	public static final String ITADAKI_BZIP2 = "itadaki_bzip2";
	public static final String BZIP2 = ITADAKI_BZIP2;
	
	public static final String JAVA_GZIP = "java_gzip"; 
	
	public static final String XZ = "xz";
	
	public static final String[] SUPPORTED_TYPES = new String[] { ITADAKI_BZIP2, JAVA_GZIP, XZ };
	
	private static final byte[] BZIP2_MAGIC = new byte[] { (byte)'B', (byte)'Z' };
	
	private static final byte[] GZIP_MAGIC = new byte[] { 31, -117 };
	
	public CompressorStreamFactory()
	{
	}
	
    /**
     * Create an compressor input stream from an input stream, autodetecting
     * the compressor type from the first few bytes of the stream. The InputStream
     * must support marks, like BufferedInputStream.
     * 
     * @param in the input stream
     * @return the compressor input stream
     * @throws CompressorException if the compressor name is not known
     * @throws IllegalArgumentException if the stream is null or does not support mark
     */
	public InputStream createCompressorInputStream(InputStream is) throws CompressorException
	{
		if (is == null)
			throw new IllegalArgumentException("InputStream cannot be null");
		if (!is.markSupported())
			throw new IllegalArgumentException("InputStream must support mark()");
		
		byte[] magic = new byte[12];
		int readLength = 0;
		is.mark(magic.length);
		try
		{
			readLength = IOUtils.readAllBytes(is, magic);
			is.reset();
		}
		catch (IOException ioe)
		{
			throw new CompressorException("Could not read magic bytes from beginning of stream", ioe);
		}
		
		try
		{
			if (checkMagic(magic, readLength, BZIP2_MAGIC))
				return new BZip2InputStream(is, false);
			
			if (checkMagic(magic, readLength, GZIP_MAGIC))
				return new GZIPInputStream(is);
			
			if (checkMagic(magic, readLength, org.tukaani.xz.XZ.HEADER_MAGIC))
				return new SingleXZInputStream(is);
		}
		catch (IOException ioe)
		{
			throw new CompressorException("Could not create compressor input stream", ioe);
		}
		
		throw new CompressorException("Unable to determine compressor type from stream data (or compressor type not supported)");		
	}
	
    /**
     * Create an compressor output stream from an compressor name and an output stream.
     * 
     * @param name the compressor name,
     * i.e. {@value #GZIP}, {@value #BZIP2}, {@value #XZ},
     * {@value #PACK200} or {@value #DEFLATE} 
     * @param out the output stream
     * @return the compressor output stream
     * @throws CompressorException if the archiver name is not known
     * @throws IllegalArgumentException if the archiver name or stream is null
     */
	public OutputStream createCompressorOutputStream(String name, OutputStream os) throws CompressorException
	{
		if (name == null || os == null)
			throw new IllegalArgumentException("Arguments cannot be null");
		
		try
		{
			if (name.equalsIgnoreCase(ITADAKI_BZIP2))
				return new BZip2OutputStream(os);
			
			if (name.equalsIgnoreCase(JAVA_GZIP))
				return new GZIPOutputStream(os);
			
			if (name.equalsIgnoreCase(XZ))
				return new XZOutputStream(os, new LZMA2Options());			
		}
		catch (IOException ioe)
		{
			throw new CompressorException("Could not create compressor output stream", ioe);
		}
		
		throw new CompressorException("Unable to determine compressor type from stream data (or compressor type not supported)");
	}
	
	private boolean checkMagic(byte[] candidate, int candidateLength, byte[] magic)
	{
		if (candidateLength < magic.length)
			return false;
		
		for (int i = 0; i < magic.length; i++)
			if (candidate[i] != magic[i])
				return false;
		
		return true;
	}
}