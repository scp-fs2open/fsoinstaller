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

package com.fsoinstaller.wizard;

import java.awt.EventQueue;

import com.fsoinstaller.internet.DownloadEvent;
import com.fsoinstaller.internet.DownloadListener;
import com.fsoinstaller.internet.Downloader;
import com.fsoinstaller.utils.MiscUtils;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


public class DownloadPanel extends InstallTaskPanel implements DownloadListener
{
	protected Downloader downloader = null;
	
	public DownloadPanel()
	{
		// set up the initial GUI display without actually having a downloader yet
		super();
	}
	
	@Override
	public void cancel()
	{
		if (downloader != null)
			downloader.cancel();
	}
	
	@Override
	public void preempt()
	{
		if (downloader != null)
			throw new IllegalStateException("The downloader should not have been assigned yet");
		downloadCancelled(null);
	}
	
	public Downloader getDownloader()
	{
		return downloader;
	}
	
	public void setDownloader(Downloader downloader)
	{
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be called on the event-dispatch thread!");
		
		if (this.downloader != null)
			this.downloader.removeDownloadListener(this);
		
		this.downloader = downloader;
		
		if (this.downloader != null)
			this.downloader.addDownloadListener(this);
	}
	
	public void downloadAboutToStart(DownloadEvent event)
	{
		progressBar.setString(String.format(XSTR.getString("progressBarStatus"), event.getDownloadName(),
				MiscUtils.humanReadableByteCount(0, true), MiscUtils.humanReadableByteCount(event.getTotalBytes(), true)));
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
		
		stoplightPanel.setPending();
	}
	
	public void downloadProgressReport(DownloadEvent event)
	{
		super.setTaskProgress(event.getDownloadName(),  event.getDownloadedBytes(), event.getTotalBytes());
	}
	
	public void downloadNotNecessary(DownloadEvent event)
	{
		super.setTaskNotNecessary(event.getDownloadName());
	}
	
	public void downloadComplete(DownloadEvent event)
	{
		super.setTaskComplete(event.getDownloadName());
	}
	
	public void downloadFailed(DownloadEvent event)
	{
		super.setTaskFailed(event.getDownloadName());
	}
	
	public void downloadCancelled(DownloadEvent event)
	{
		super.setTaskCancelled(event.getDownloadName());
	}
}
