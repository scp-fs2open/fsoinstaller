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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.MiscUtils;
import com.fsoinstaller.utils.ProgressBarDialog;
import com.fsoinstaller.utils.ThreadSafeJOptionPane;
import com.l2fprod.common.swing.JDirectoryChooser;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


public class ConfigPage extends WizardPage
{
	private static final Logger logger = Logger.getLogger(ConfigPage.class);
	
	private final JTextField directoryField;
	private final JTextField hostField;
	private final JTextField portField;
	private boolean usingProxy;
	
	public ConfigPage()
	{
		super("config");
		
		// load initial directory
		File dir = configuration.getApplicationDir();
		if (dir == null)
		{
			logger.warn("Application directory should have been assigned by now!");
			dir = new File(configuration.getDefaultDirList().get(0));
		}
		
		// get canonical path
		String dirText;
		try
		{
			dirText = dir.getCanonicalPath();
		}
		catch (IOException ioe)
		{
			logger.warn("Could not get canonical path of destination directory", ioe);
			dirText = dir.getAbsolutePath();
		}
		
		// load initial proxy settings
		String host = configuration.getProxyHost();
		int port = configuration.getProxyPort();
		usingProxy = (host != null && port >= 0);
		
		// create widgets
		directoryField = new JTextField(dirText);
		hostField = new JTextField(usingProxy ? host : XSTR.getString("none"));
		portField = new JTextField(usingProxy ? Integer.toString(port) : XSTR.getString("none"));
		hostField.setEnabled(usingProxy);
		portField.setEnabled(usingProxy);
		
		// they shouldn't change vertical size
		directoryField.setMaximumSize(new Dimension((int) directoryField.getMaximumSize().getWidth(), (int) directoryField.getPreferredSize().getHeight()));
		hostField.setMaximumSize(new Dimension((int) hostField.getMaximumSize().getWidth(), (int) hostField.getPreferredSize().getHeight()));
		portField.setMaximumSize(new Dimension((int) portField.getMaximumSize().getWidth(), (int) portField.getPreferredSize().getHeight()));
	}
	
	@Override
	public JPanel createCenterPanel()
	{
		JLabel dummy = new JLabel();
		
		// bleh, for multiline we need a JTextArea, but we want it to look like a JLabel
		JTextArea text = new JTextArea(XSTR.getString("configPageText"));
		text.setEditable(false);
		text.setRows(4);
		text.setOpaque(false);
		text.setHighlighter(null);
		text.setFont(dummy.getFont());
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		
		JPanel dirPanel = new JPanel();
		dirPanel.setBorder(BorderFactory.createEmptyBorder(GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN));
		dirPanel.setLayout(new BoxLayout(dirPanel, BoxLayout.X_AXIS));
		dirPanel.add(directoryField);
		dirPanel.add(Box.createHorizontalStrut(GUIConstants.DEFAULT_MARGIN));
		dirPanel.add(new JButton(new BrowseAction()));
		
		JPanel outerDirPanel = new JPanel();
		outerDirPanel.setBorder(BorderFactory.createTitledBorder(XSTR.getString("installationDirBorder")));
		outerDirPanel.setLayout(new BoxLayout(outerDirPanel, BoxLayout.Y_AXIS));
		outerDirPanel.add(dirPanel);
		
		JCheckBox check = new JCheckBox(new ProxyCheckAction());
		check.setSelected(usingProxy);
		JLabel hostLabel = new JLabel(XSTR.getString("proxyHostLabel"));
		JLabel portLabel = new JLabel(XSTR.getString("proxyPortLabel"));
		int m_width = (int) Math.max(hostLabel.getMinimumSize().getWidth(), portLabel.getMinimumSize().getWidth());
		int p_width = (int) Math.max(hostLabel.getPreferredSize().getWidth(), portLabel.getPreferredSize().getWidth());
		hostLabel.setMinimumSize(new Dimension(m_width, (int) hostLabel.getMinimumSize().getHeight()));
		portLabel.setMinimumSize(new Dimension(m_width, (int) portLabel.getMinimumSize().getHeight()));
		hostLabel.setPreferredSize(new Dimension(p_width, (int) hostLabel.getPreferredSize().getHeight()));
		portLabel.setPreferredSize(new Dimension(p_width, (int) portLabel.getPreferredSize().getHeight()));
		
		JPanel checkPanel = new JPanel();
		checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.X_AXIS));
		checkPanel.add(check);
		checkPanel.add(Box.createHorizontalGlue());
		
		JPanel hostPanel = new JPanel();
		hostPanel.setLayout(new BoxLayout(hostPanel, BoxLayout.X_AXIS));
		hostPanel.add(Box.createHorizontalStrut(GUIConstants.DEFAULT_MARGIN * 3));
		hostPanel.add(hostLabel);
		hostPanel.add(Box.createHorizontalStrut(GUIConstants.DEFAULT_MARGIN));
		hostPanel.add(hostField);
		
		JPanel portPanel = new JPanel();
		portPanel.setLayout(new BoxLayout(portPanel, BoxLayout.X_AXIS));
		portPanel.add(Box.createHorizontalStrut(GUIConstants.DEFAULT_MARGIN * 3));
		portPanel.add(portLabel);
		portPanel.add(Box.createHorizontalStrut(GUIConstants.DEFAULT_MARGIN));
		portPanel.add(portField);
		
		JPanel proxyPanel = new JPanel();
		proxyPanel.setBorder(BorderFactory.createEmptyBorder(0, GUIConstants.SMALL_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN));
		proxyPanel.setLayout(new BoxLayout(proxyPanel, BoxLayout.Y_AXIS));
		proxyPanel.add(checkPanel);
		proxyPanel.add(Box.createVerticalStrut(GUIConstants.SMALL_MARGIN));
		proxyPanel.add(hostPanel);
		proxyPanel.add(Box.createVerticalStrut(GUIConstants.SMALL_MARGIN));
		proxyPanel.add(portPanel);
		
		JPanel outerProxyPanel = new JPanel();
		outerProxyPanel.setBorder(BorderFactory.createTitledBorder(XSTR.getString("proxySettingsBorder")));
		outerProxyPanel.setLayout(new BoxLayout(outerProxyPanel, BoxLayout.Y_AXIS));
		outerProxyPanel.add(proxyPanel);
		
		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createEmptyBorder(GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(text);
		panel.add(Box.createVerticalStrut(GUIConstants.DEFAULT_MARGIN * 2));
		panel.add(outerDirPanel);
		panel.add(Box.createVerticalStrut(GUIConstants.DEFAULT_MARGIN * 2));
		panel.add(outerProxyPanel);
		panel.add(Box.createVerticalStrut(GUIConstants.DEFAULT_MARGIN * 2));
		panel.add(Box.createVerticalGlue());
		
		return panel;
	}
	
	@Override
	public void prepareForDisplay()
	{
		backButton.setVisible(false);
	}
	
	@Override
	public void prepareToLeavePage(final Runnable runWhenReady, boolean progressing)
	{
		// what happens when we're finished validating
		Runnable toRunNext = runWhenReady;
		
		// what happens if we cancel
		final Runnable exitRunnable = new Runnable()
		{
			public void run()
			{
				JFrame frame = gui;
				logger.debug("Disposing active JFrame '" + frame.getName() + "'...");
				frame.dispose();
			}
		};
		
		// exception callback
		final ProgressBarDialog.AbnormalTerminationCallback callback = new ProgressBarDialog.AbnormalTerminationCallback()
		{
			public void handleCancellation()
			{
				ThreadSafeJOptionPane.showMessageDialog(gui, XSTR.getString("validationCancelled"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
			}
			
			public void handleException(Exception exception)
			{
				if (exception instanceof SecurityException)
				{
					ThreadSafeJOptionPane.showMessageDialog(gui, XSTR.getString("securityManagerError"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					exitRunnable.run();
				}
				else if (exception instanceof InterruptedException)
				{
					ThreadSafeJOptionPane.showMessageDialog(gui, XSTR.getString("validationInterrupted"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
					
					// restore interrupt
					Thread.currentThread().interrupt();
					
					return;
				}
				else
				{
					ThreadSafeJOptionPane.showMessageDialog(gui, XSTR.getString("unexpectedRuntimeException"), FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					exitRunnable.run();
				}
			}
		};
		
		// this is okay because the checked directories are only ever accessed from the event dispatching thread
		Map<String, Object> settings = configuration.getSettings();
		@SuppressWarnings("unchecked")
		Set<String> checked = (Set<String>) settings.get(Configuration.CHECKED_DIRECTORIES_KEY);
		if (checked == null)
		{
			checked = new HashSet<String>();
			settings.put(Configuration.CHECKED_DIRECTORIES_KEY, checked);
		}
		
		// don't do directory manipulation if we don't need to
		if (!checked.contains(directoryField.getText()))
		{
			// we need to insert this task between validation and proceeding to the next page
			toRunNext = new Runnable()
			{
				public void run()
				{
					Callable<Void> gog = new DirectoryTask(gui, directoryField.getText(), runWhenReady, exitRunnable);
					ProgressBarDialog dialog = new ProgressBarDialog(gui, XSTR.getString("progressBarCheckingDir"));
					dialog.runTask(gog, callback);
				}
			};
		}
		
		Callable<Void> validation = new SuperValidationTask(gui, directoryField.getText(), usingProxy, hostField.getText(), portField.getText(), toRunNext, exitRunnable);
		ProgressBarDialog dialog = new ProgressBarDialog(gui, XSTR.getString("progressBarSettingUpInstaller"));
		dialog.runTask(validation, callback);
	}
	
	private final class BrowseAction extends AbstractAction
	{
		public BrowseAction()
		{
			putValue(Action.NAME, XSTR.getString("browseButtonName"));
			putValue(Action.SHORT_DESCRIPTION, XSTR.getString("browseButtonTooltip"));
		}
		
		public void actionPerformed(ActionEvent e)
		{
			File dir = MiscUtils.validateApplicationDir(directoryField.getText());
			
			// create a file chooser
			JDirectoryChooser chooser = new JDirectoryChooser();
			chooser.setCurrentDirectory(dir);
			chooser.setDialogTitle(XSTR.getString("chooseDirTitle"));
			chooser.setShowingCreateDirectory(false);
			
			// display it
			int result = chooser.showDialog(gui, XSTR.getString("OK"));
			if (result == JDirectoryChooser.APPROVE_OPTION)
				directoryField.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	}
	
	private final class ProxyCheckAction extends AbstractAction
	{
		public ProxyCheckAction()
		{
			putValue(Action.NAME, XSTR.getString("proxyButtonName"));
		}
		
		public void actionPerformed(ActionEvent e)
		{
			usingProxy = !usingProxy;
			
			hostField.setEnabled(usingProxy);
			portField.setEnabled(usingProxy);
		}
	}
}
