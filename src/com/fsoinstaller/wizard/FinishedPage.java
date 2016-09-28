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

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


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
		JLabel header = new JLabel(XSTR.getString("finishedPageText"));
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
		List<String> installNotes = (List<String>) settings.get(Configuration.INSTALL_NOTES_KEY);
		@SuppressWarnings("unchecked")
		List<String> installErrors = (List<String>) settings.get(Configuration.INSTALL_ERRORS_KEY);
		
		StringBuilder text = new StringBuilder();
		if (installNotes.isEmpty() && installErrors.isEmpty())
			text.append(XSTR.getString("allModsSuccessful"));
		
		// add any notes
		if (!installNotes.isEmpty())
		{
			for (String note: installNotes)
			{
				text.append(note);
				text.append("\n");
			}
		}
		
		// add any errors
		if (!installErrors.isEmpty())
		{
			if (!installNotes.isEmpty())
				text.append("\n\n");
			
			if (installErrors.size() == 1)
				text.append(XSTR.getString("readErrors1"));
			else
				text.append(XSTR.getString("readErrors2"));
			text.append("\n\n");
			
			for (String error: installErrors)
			{
				text.append(error);
				text.append("\n");
			}
		}
		
		// log the results for great justice
		logger.info(text.toString());
		
		// populate the text pane
		textPane.setText(text.toString());
	}
	
	@Override
	public void prepareToLeavePage(Runnable runWhenReady, boolean progressing)
	{
		// we're not going anywhere
	}
	
	private final class FinishAction extends AbstractAction
	{
		public FinishAction()
		{
			putValue(Action.NAME, XSTR.getString("finishButtonName"));
			putValue(Action.SHORT_DESCRIPTION, XSTR.getString("finishButtonTooltip"));
		}
		
		public void actionPerformed(ActionEvent e)
		{
			JFrame frame = gui;
			logger.debug("Disposing active JFrame '" + frame.getName() + "'...");
			frame.dispose();
		}
	}
}
