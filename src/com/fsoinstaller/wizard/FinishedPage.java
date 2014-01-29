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

package com.fsoinstaller.wizard;

import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;

import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.SwingUtils;


public class FinishedPage extends WizardPage
{
	private static final Logger logger = Logger.getLogger(FinishedPage.class);
	
	private final JTextPane textPane;
	
	public FinishedPage()
	{
		super("finished");
		
		textPane = new JTextPane();
	}
	
	@Override
	public JPanel createCenterPanel()
	{
		JLabel header = new JLabel("Installation complete.  Check below for any errors.");
		header.setAlignmentX(LEFT_ALIGNMENT);
		
		JScrollPane scroll = new JScrollPane(textPane);
		scroll.setAlignmentX(LEFT_ALIGNMENT);
		
		JComponent strut1 = (JComponent) Box.createVerticalStrut(GUIConstants.DEFAULT_MARGIN);
		strut1.setAlignmentX(LEFT_ALIGNMENT);
		JComponent strut2 = (JComponent) Box.createVerticalStrut(GUIConstants.DEFAULT_MARGIN * 2);
		strut2.setAlignmentX(LEFT_ALIGNMENT);
		
		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createEmptyBorder(GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(header);
		panel.add(strut1);
		panel.add(scroll);
		panel.add(strut2);
		
		return panel;
	}
	
	@Override
	public void prepareForDisplay()
	{
		backButton.setVisible(false);
		cancelButton.setEnabled(false);
		nextButton.setEnabled(true);
		
		nextButton.setAction(new FinishAction());
		
		Map<String, Object> settings = configuration.getSettings();
		@SuppressWarnings("unchecked")
		List<String> installResults = (List<String>) settings.get(Configuration.INSTALL_RESULTS_KEY);
		
		if (installResults.isEmpty())
		{
			textPane.setText("All mods installed successfully!");
		}
		else
		{
			StringBuilder text = new StringBuilder();
			if (installResults.size() == 1)
				text.append("The following error was encountered:");
			else
				text.append("The following errors were encountered:");
			text.append("\n\n");
			
			for (String line: installResults)
			{
				text.append(line);
				text.append("\n");
			}
			textPane.setText(text.toString());
		}
	}
	
	@Override
	public void prepareToLeavePage(Runnable runWhenReady)
	{
	}
	
	private final class FinishAction extends AbstractAction
	{
		public FinishAction()
		{
			putValue(Action.NAME, "Finish");
			putValue(Action.SHORT_DESCRIPTION, "Finish installation");
		}
		
		public void actionPerformed(ActionEvent e)
		{
			// ---------- TEMPORARY CODE ----------
			// get root thread group
			ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
			ThreadGroup parentGroup;
			while ((parentGroup = rootGroup.getParent()) != null)
				rootGroup = parentGroup;
			
			// enumerate all threads
			Thread[] threads = new Thread[rootGroup.activeCount()];
			while (rootGroup.enumerate(threads, true) == threads.length)
				threads = new Thread[threads.length * 2];
			
			// log them
			for (Thread thread: threads)
				logger.warn(thread.getName());
			// ---------- TEMPORARY CODE ----------
			
			JFrame frame = (JFrame) SwingUtils.getActiveFrame();
			frame.dispose();
		}
	}
}
