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

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.utils.Logger;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


public class InstallPage extends WizardPage
{
	private static final Logger logger = Logger.getLogger(InstallPage.class);
	
	private final JPanel installPanel;
	
	private final List<InstallItem> installItems;
	private final List<String> installNotes;
	private final List<String> installErrors;
	private int remainingMods;
	
	public InstallPage()
	{
		super("install");
		
		installItems = new ArrayList<InstallItem>();
		installNotes = new ArrayList<String>();
		installErrors = new ArrayList<String>();
		remainingMods = 0;
		
		// create widgets
		installPanel = new JPanel();
		installPanel.setLayout(new BoxLayout(installPanel, BoxLayout.Y_AXIS));
	}
	
	@Override
	public JPanel createCenterPanel()
	{
		JPanel labelPanel = new JPanel();
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
		labelPanel.add(new JLabel(XSTR.getString("installPageText")));
		labelPanel.add(Box.createHorizontalGlue());
		
		// this little trick prevents the install items from stretching if there aren't enough to fill the vertical space
		JPanel scrollPanel = new JPanel(new BorderLayout());
		scrollPanel.add(installPanel, BorderLayout.NORTH);
		scrollPanel.add(Box.createGlue(), BorderLayout.CENTER);
		
		JScrollPane installScrollPane = new JScrollPane(scrollPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		installScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		
		JPanel panel = new JPanel(new BorderLayout(0, GUIConstants.DEFAULT_MARGIN));
		panel.setBorder(BorderFactory.createEmptyBorder(GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN));
		panel.add(labelPanel, BorderLayout.NORTH);
		panel.add(installScrollPane, BorderLayout.CENTER);
		
		return panel;
	}
	
	@Override
	public void prepareForDisplay()
	{
		backButton.setVisible(false);
		nextButton.setEnabled(false);
		
		// change what the cancel button does
		// (instead of closing the entire application, it will cancel all the downloads and clear the GUI to proceed to the next page)
		cancelButton.setAction(new CancelAction());
		
		Map<String, Object> settings = configuration.getSettings();
		@SuppressWarnings("unchecked")
		List<InstallerNode> modNodes = (List<InstallerNode>) settings.get(Configuration.MOD_NODES_KEY);
		@SuppressWarnings("unchecked")
		Set<String> selectedMods = (Set<String>) settings.get(Configuration.MODS_TO_INSTALL_KEY);
		
		// log the mods
		logger.info("Selected mods:");
		for (String mod: selectedMods)
			logger.info(mod);
		
		// iterate through the mod nodes so we keep the proper order
		for (InstallerNode node: modNodes)
		{
			if (!selectedMods.contains(node.getTreePath()))
				continue;
			
			// add item's GUI (and all its children) to the panel
			final InstallItem item = new InstallItem(node, selectedMods);
			installItems.add(item);
			installPanel.add(item);
			
			// keep track of mods that have completed (whether success or failure)
			remainingMods++;
			item.addCompletionListener(new ChangeListener()
			{
				public void stateChanged(ChangeEvent e)
				{
					// add any feedback to the output
					installNotes.addAll(item.getInstallNotes());
					installErrors.addAll(item.getInstallErrors());
					
					remainingMods--;
					
					// if ALL of the nodes have completed, do some more stuff
					if (remainingMods == 0)
						installCompleted();
				}
			});
			
			// let's go!
			item.start();
		}
		installPanel.add(Box.createGlue());
	}
	
	protected void installCompleted()
	{
		logger.info("Install completed!  Ready to move to next page.");
		
		nextButton.setEnabled(true);
		cancelButton.setEnabled(false);
		
		Map<String, Object> settings = configuration.getSettings();
		settings.put(Configuration.INSTALL_NOTES_KEY, installNotes);
		settings.put(Configuration.INSTALL_ERRORS_KEY, installErrors);
		
		// done managing tasks
		FreeSpaceOpenInstaller.getInstance().shutDownTasks();
	}
	
	@Override
	public void prepareToLeavePage(Runnable runWhenReady, boolean progressing)
	{
		runWhenReady.run();
	}
	
	private final class CancelAction extends AbstractAction
	{
		public CancelAction()
		{
			putValue(Action.NAME, XSTR.getString("cancelButtonName"));
			putValue(Action.SHORT_DESCRIPTION, XSTR.getString("cancelButtonTooltip"));
		}
		
		public void actionPerformed(ActionEvent e)
		{
			int response = JOptionPane.showConfirmDialog(gui, XSTR.getString("cancelPrompt"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (response != JOptionPane.YES_OPTION)
				return;
			
			for (InstallItem item: installItems)
				item.cancel();
		}
	}
}
