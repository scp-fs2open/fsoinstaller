
package io.sigpipe.jbsdiff.progress;

import java.util.EventObject;


public class ProgressEvent extends EventObject
{
	private static final long serialVersionUID = 1L;
	
	private final int current;
	private final int total;
	
	public ProgressEvent(Object source, int current, int total)
	{
		super(source);
		this.current = current;
		this.total = total;
	}
	
	public int getCurrent()
	{
		return current;
	}
	
	public int getTotal()
	{
		return total;
	}
}
