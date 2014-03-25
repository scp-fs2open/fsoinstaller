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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

import com.fsoinstaller.common.BaseURL;
import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.common.InstallerNode.FilePair;
import com.fsoinstaller.common.InstallerNode.HashTriple;
import com.fsoinstaller.common.InstallerNode.InstallUnit;
import com.fsoinstaller.common.InvalidBaseURLException;
import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.utils.KeyPair;
import com.fsoinstaller.utils.Logger;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


public class InstallPage extends WizardPage
{
	private static final Logger logger = Logger.getLogger(InstallPage.class);
	
	private static final List<KeyPair<String, String>> gogMovies = new ArrayList<KeyPair<String, String>>();
	static
	{
		gogMovies.add(new KeyPair<String, String>("INTRO.MVE", "data2"));
		gogMovies.add(new KeyPair<String, String>("MONO1.MVE", "data2"));
		gogMovies.add(new KeyPair<String, String>("COLOSSUS.MVE", "data2"));
		gogMovies.add(new KeyPair<String, String>("MONO2.MVE", "data3"));
		gogMovies.add(new KeyPair<String, String>("MONO3.MVE", "data3"));
		gogMovies.add(new KeyPair<String, String>("MONO4.MVE", "data3"));
		gogMovies.add(new KeyPair<String, String>("BASTION.MVE", "data3"));
		gogMovies.add(new KeyPair<String, String>("ENDPART1.MVE", "data3"));
		gogMovies.add(new KeyPair<String, String>("ENDPRT2A.MVE", "data3"));
		gogMovies.add(new KeyPair<String, String>("ENDPRT2B.MVE", "data3"));
	}
	
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
		
		// we might want to add some special non-selectable mods...
		List<InstallerNode> newNodes = maybeCreateNonSelectableNodes();
		
		// if they exist, move them to the front
		if (!newNodes.isEmpty())
		{
			modNodes.addAll(0, newNodes);
			for (InstallerNode node: newNodes)
				selectedMods.add(node.getName());
		}
		
		// log the mods
		logger.info("Selected mods:");
		for (String mod: selectedMods)
			logger.info(mod);
		
		// iterate through the mod nodes so we keep the proper order
		for (InstallerNode node: modNodes)
		{
			if (!selectedMods.contains(node.getName()))
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
	
	protected List<InstallerNode> maybeCreateNonSelectableNodes()
	{
		List<InstallerNode> nodes = new ArrayList<InstallerNode>();
		
		// if OpenAL needs to be installed, do that first
		// (note, we can't use a primitive boolean because it may be null)
		Boolean installOpenAL = (Boolean) configuration.getSettings().get(Configuration.ADD_OPENAL_INSTALL_KEY);
		if (installOpenAL == Boolean.TRUE)
		{
			InstallerNode openAL = new InstallerNode(XSTR.getString("installPageOpenALText"));
			openAL.setFolder(File.separator);
			openAL.setVersion(UUID.randomUUID().toString().replaceAll("-", ""));
			InstallUnit installUnit = new InstallUnit();
			for (String url: FreeSpaceOpenInstaller.INSTALLER_HOME_URLs)
			{
				try
				{
					installUnit.addBaseURL(new BaseURL(url));
				}
				catch (InvalidBaseURLException iburle)
				{
					logger.error("Impossible error: Internal home URLs should always be correct!", iburle);
				}
			}
			installUnit.addFile("oalinst.exe");
			openAL.addInstall(installUnit);
			
			openAL.addHashTriple(new HashTriple("MD5", "oalinst.exe", "694F54BD227916B89FC3EB1DB53F0685"));
			
			openAL.addExecCmd("oalinst.exe");
			
			openAL.setNote(XSTR.getString("openALNote"));
			
			if (!installUnit.getBaseURLList().isEmpty())
				nodes.add(openAL);
		}
		
		// all of the optional nodes, except for OpenAL, only apply for FS2
		if (configuration.requiresFS2())
		{
			// if GOG needs to be installed, do so
			File gogInstallPackage = (File) configuration.getSettings().get(Configuration.GOG_INSTALL_PACKAGE_KEY);
			InstallerNode gog = null;
			if (gogInstallPackage != null)
			{
				gog = new InstallerNode(XSTR.getString("installPageGOGText"));
				gog.setFolder(File.separator);
				gog.setVersion(UUID.randomUUID().toString().replaceAll("-", ""));
				
				// TODO: add GOG install option
				
				nodes.add(gog);
			}
			
			// if version 1.2 patch needs to be applied, then add it
			String hash = (String) configuration.getSettings().get(Configuration.ROOT_FS2_VP_HASH_KEY);
			if (hash != null && hash.equalsIgnoreCase("42bc56a410373112dfddc7985f66524a"))
			{
				InstallerNode patchTo1_2 = new InstallerNode(XSTR.getString("installPagePatchText"));
				patchTo1_2.setFolder(File.separator);
				patchTo1_2.setVersion(UUID.randomUUID().toString().replaceAll("-", ""));
				InstallUnit installUnit = new InstallUnit();
				for (String url: FreeSpaceOpenInstaller.INSTALLER_HOME_URLs)
				{
					try
					{
						installUnit.addBaseURL(new BaseURL(url));
					}
					catch (InvalidBaseURLException iburle)
					{
						logger.error("Impossible error: Internal home URLs should always be correct!", iburle);
					}
				}
				installUnit.addFile("fs21x-12.7z");
				patchTo1_2.addInstall(installUnit);
				
				patchTo1_2.addDelete("FRED2.exe");
				patchTo1_2.addDelete("FreeSpace2.exe");
				patchTo1_2.addDelete("FS2.exe");
				patchTo1_2.addDelete("UpdateLauncher.exe");
				patchTo1_2.addDelete("readme.txt");
				patchTo1_2.addDelete("root_fs2.vp");
				
				patchTo1_2.addHashTriple(new HashTriple("MD5", "FRED2.exe", "9b8a3dfeb586de9584056f9e8fa28bb5"));
				patchTo1_2.addHashTriple(new HashTriple("MD5", "FreeSpace2.exe", "091f3f06f596a48ba4e3c42d05ec379f"));
				patchTo1_2.addHashTriple(new HashTriple("MD5", "FS2.exe", "2c8133768ebd99faba5c00dd03ebf9ea"));
				patchTo1_2.addHashTriple(new HashTriple("MD5", "UpdateLauncher.exe", "babe53dc03c83067a3336f0c888c4ac2"));
				patchTo1_2.addHashTriple(new HashTriple("MD5", "readme.txt", "b4df1711c324516e497ce90b66f45de9"));
				patchTo1_2.addHashTriple(new HashTriple("MD5", "root_fs2.vp", "0d9fd69acfe8b29d616377b057d2fc04"));
				
				if (!installUnit.getBaseURLList().isEmpty())
					nodes.add(patchTo1_2);
			}
			
			// if any of the MVE files exist in data2 and data3, but not in data/movies, copy them
			InstallerNode copyMVEs = new InstallerNode(XSTR.getString("installPageCopyCutscenesText"));
			copyMVEs.setFolder(File.separator);
			copyMVEs.setVersion(UUID.randomUUID().toString().replaceAll("-", ""));
			boolean doCopy = false;
			for (KeyPair<String, String> pair: gogMovies)
			{
				// e.g. data2/INTRO.MVE
				String source = pair.getObject2() + File.separator + pair.getObject1();
				// e.g. data/movies/INTRO.MVE
				String dest = "data" + File.separator + "movies" + File.separator + pair.getObject1();
				
				// anticipate copying the files
				copyMVEs.addCopyPair(new FilePair(source, dest));
				
				// check whether at least one file needs to be copied
				if ((new File(configuration.getApplicationDir(), source)).exists() && !(new File(configuration.getApplicationDir(), dest)).exists())
					doCopy = true;
			}
			if (gog != null)
				gog.addChild(copyMVEs);
			else if (doCopy)
				nodes.add(copyMVEs);
		}
		
		return nodes;
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
	public void prepareToLeavePage(Runnable runWhenReady)
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
