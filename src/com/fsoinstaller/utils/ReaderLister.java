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

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fsoinstaller.common.PayloadEvent;
import com.fsoinstaller.common.PayloadListener;


/**
 * Funnel the text from a Reader into a list of Strings. Since it implements
 * Runnable (and Callable for that matter), it can be started as an independent
 * thread. Additionally, events are fired so that Strings can be monitored as
 * they are added to the list.
 * 
 * @author Goober5000
 */
public class ReaderLister implements Runnable, Callable<Void>
{
	protected final List<PayloadListener> payloadListeners;
	
	protected final Reader reader;
	protected final List<String> list;
	protected final List<String> preamble;
	
	public ReaderLister(Reader reader)
	{
		this(reader, null);
	}
	
	public ReaderLister(Reader reader, List<String> preamble)
	{
		this.reader = reader;
		this.list = Collections.synchronizedList(new ArrayList<String>());
		this.preamble = preamble;
		
		this.payloadListeners = new CopyOnWriteArrayList<PayloadListener>();
	}
	
	public List<String> getList()
	{
		// since we maintain a Collections.synchronizedList internally, avoid the
		// need for external synchronization
		List<String> dest = new ArrayList<String>();
		synchronized (list)
		{
			dest.addAll(list);
		}
		return dest;
	}
	
	public void addPayloadListener(PayloadListener listener)
	{
		payloadListeners.add(listener);
	}
	
	public void removePayloadListener(PayloadListener listener)
	{
		payloadListeners.remove(listener);
	}
	
	protected void firePayloadEvent(final String line)
	{
		EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				PayloadEvent event = null;
				for (PayloadListener listener: payloadListeners)
				{
					// lazy instantiation of the event
					if (event == null)
						event = new PayloadEvent(this, line);
					
					// fire it
					listener.payloadReceived(event);
				}
			}
		});
	}
	
	public Void call() throws IOException
	{
		BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
		
		try
		{
			// don't write the preamble unless we have other data to write as well: this prevents false positives when checking to see if any output was written
			boolean writtenPreamble = false;
			
			String line;
			while ((line = br.readLine()) != null)
			{
				if (!writtenPreamble)
				{
					if (preamble != null)
						list.addAll(preamble);
					writtenPreamble = true;
				}
				list.add(line);
				firePayloadEvent(line);
			}
		}
		finally
		{
			br.close();
		}
		
		return null;
	}
	
	public void run()
	{
		try
		{
			call();
		}
		catch (IOException ioe)
		{
			Logger.getLogger(ReaderLogger.class).error("There was a problem copying the Reader text to the List!", ioe);
		}
	}
}
