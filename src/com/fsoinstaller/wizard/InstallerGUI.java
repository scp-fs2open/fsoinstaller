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

import java.awt.CardLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JFrame;

import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.utils.GraphicsUtils;


public class InstallerGUI extends JFrame
{
	private static final BufferedImage app_icon = GraphicsUtils.getResourceImage("installer_icon.png");
	
	protected final List<WizardPage> pageList;
	protected final CardLayout layout;
	
	protected WizardPage activePage;
	protected Map<WizardPage, WizardPage> backMap;
	protected Map<WizardPage, WizardPage> nextMap;
	
	public InstallerGUI()
	{
		// instantiate all the pages we'll be using
		this(Arrays.asList(new WizardPage[]
		{
			new ConfigPage(),
			new ChoicePage(),
			new ModSelectPage(),
			new InstallPage(),
			new FinishedPage()
		}));
	}
	
	public InstallerGUI(Collection<? extends WizardPage> pages)
	{
		setName("InstallerGUI");
		layout = new CardLayout();
		
		pageList = new ArrayList<WizardPage>(pages);
		if (pageList.isEmpty())
			throw new IllegalArgumentException("No pages provided!");
		
		activePage = pageList.get(0);
		backMap = new HashMap<WizardPage, WizardPage>();
		nextMap = new HashMap<WizardPage, WizardPage>();
		
		// establish page links
		for (int i = 0; i < pageList.size(); i++)
		{
			backMap.put(pageList.get(i), i > 0 ? pageList.get(i - 1) : null);
			nextMap.put(pageList.get(i), i < pageList.size() - 1 ? pageList.get(i + 1) : null);
		}
	}
	
	/**
	 * This should be called immediately after the object is constructed, so
	 * that the appropriate widgets are added in the appropriate way. This is
	 * done to avoid inheritance problems when a subclassed panel is working
	 * with widgets previously created in a superclass panel.
	 */
	public void buildUI()
	{
		JComponent contentPane = (JComponent) getContentPane();
		contentPane.setLayout(layout);
		
		// add pages to layout
		contentPane.removeAll();
		for (WizardPage page: pageList)
		{
			page.setGUI(this);
			page.buildUI();
			contentPane.add(page, page.getName());
		}
		
		// final frame tweaks
		setIconImage(app_icon);
		// setResizable(false);
		setTitle(FreeSpaceOpenInstaller.INSTALLER_TITLE + " v" + FreeSpaceOpenInstaller.INSTALLER_VERSION);
	}
	
	@Override
	public void setVisible(boolean b)
	{
		if (b)
			activePage.prepareForDisplay();
		super.setVisible(b);
	}
	
	public void moveBack()
	{
		WizardPage backPage = backMap.get(activePage);
		if (backPage == null)
			return;
		
		activePage = backPage;
		activePage.prepareForDisplay();
		layout.show(getContentPane(), activePage.getName());
	}
	
	public void moveNext()
	{
		WizardPage nextPage = nextMap.get(activePage);
		if (nextPage == null)
			return;
		
		activePage = nextPage;
		activePage.prepareForDisplay();
		layout.show(getContentPane(), activePage.getName());
	}
	
	public void skip(Class<? extends WizardPage> pageClass)
	{
		// skip over all pages that have this class
		for (WizardPage page: pageList)
		{
			if (page.getClass() == pageClass)
				skip(page);
		}
	}
	
	public void skip(WizardPage page)
	{
		// for all pages which go back to this page, have them go back one more step
		for (Map.Entry<WizardPage, WizardPage> backEntry: backMap.entrySet())
		{
			if (backEntry.getValue() == page)
				backEntry.setValue(backMap.get(page));
		}
		
		// for all pages which go next to this page, have them go next one more step
		for (Map.Entry<WizardPage, WizardPage> nextEntry: nextMap.entrySet())
		{
			if (nextEntry.getValue() == page)
				nextEntry.setValue(nextMap.get(page));
		}
	}
}
