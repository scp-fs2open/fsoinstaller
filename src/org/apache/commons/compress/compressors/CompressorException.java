package org.apache.commons.compress.compressors;

public class CompressorException extends Exception
{
	private static final long serialVersionUID = 1L;

	public CompressorException(String message)
	{
		super(message);
	}
	
	public CompressorException(String message, Throwable exception)
	{
		super(message, exception);
	}
}