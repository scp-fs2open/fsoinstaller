/*
 * This file is part of the FreeSpace Open Installer
 * Copyright (C) 2016 The FreeSpace 2 Source Code Project
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

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import com.fsoinstaller.utils.MiscUtils;


public class InstallTaskPanel extends JPanel
{
	protected final JProgressBar progressBar;
	protected final StoplightPanel stoplightPanel;
	
	public InstallTaskPanel()
	{
		super();
		
		this.progressBar = new JProgressBar(0, GUIConstants.BAR_MAXIMUM);
		progressBar.setStringPainted(true);
		
		this.stoplightPanel = new StoplightPanel((int) progressBar.getPreferredSize().getHeight());
		
		setBorder(BorderFactory.createEmptyBorder(GUIConstants.SMALL_MARGIN, GUIConstants.SMALL_MARGIN, GUIConstants.SMALL_MARGIN, 0));
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(progressBar);
		add(Box.createHorizontalStrut(GUIConstants.SMALL_MARGIN));
		add(stoplightPanel);
		
		// waiting to begin
		setPending();
	}
	
	/**
	 * Configures the GUI to await the beginning of a task (illustrate a "waiting" state).
	 */
	public void setPending()
	{
		progressBar.setString(XSTR.getString("progressBarWaiting"));
		progressBar.setIndeterminate(true);
		progressBar.setValue(0);
		
		stoplightPanel.setPending();
	}
	
	/**
	 * Tells the panel that the task in progress has been aborted.
	 */
	public void cancel()
	{
	}
	
	/**
	 * Tells the panel that the task has been overcome by events and will not be
	 * performed.
	 */
	public void preempt()
	{
	}
	
	public void setTaskProgress(String taskName, long currentBytes, long totalBytes)
	{
		progressBar.setString(String.format(XSTR.getString("progressBarStatus"), taskName,
				MiscUtils.humanReadableByteCount(currentBytes, true),
				MiscUtils.humanReadableByteCount(totalBytes, true)));

		progressBar.setIndeterminate(false);
		progressBar.setValue((int) ((double) currentBytes / totalBytes * GUIConstants.BAR_MAXIMUM));
	}
	
	public void setTaskNotNecessary(String taskName)
	{
		progressBar.setString((MiscUtils.isEmpty(taskName) ? "" : (taskName + ": ")) + XSTR.getString("progressBarUpToDate"));
		progressBar.setIndeterminate(false);
		progressBar.setValue(GUIConstants.BAR_MAXIMUM);
		
		stoplightPanel.setSuccess();
	}
	
	public void setTaskComplete(String taskName)
	{
		progressBar.setString((MiscUtils.isEmpty(taskName) ? "" : (taskName + ": ")) + XSTR.getString("progressBarComplete"));
		progressBar.setIndeterminate(false);
		progressBar.setValue(GUIConstants.BAR_MAXIMUM);
		
		stoplightPanel.setSuccess();
	}
	
	public void setTaskFailed(String taskName)
	{
		progressBar.setString((MiscUtils.isEmpty(taskName) ? "" : (taskName + ": ")) + XSTR.getString("progressBarFailed"));
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
		
		stoplightPanel.setFailure();
	}
	
	public void setTaskCancelled(String taskName)
	{
		progressBar.setString((MiscUtils.isEmpty(taskName) ? "" : (taskName + ": ")) + XSTR.getString("progressBarCancelled"));
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
		
		stoplightPanel.setFailure();
	}
}
