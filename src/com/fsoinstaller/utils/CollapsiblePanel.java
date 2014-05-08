/*
 * This file is part of the FreeSpace Open Installer
 * Copyright (C) 2013 The FreeSpace 2 Source Code Project
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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

import com.fsoinstaller.wizard.GUIConstants;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


/**
 * This class provides a way to show or hide a child panel by means of an arrow
 * button that expands or collapses a panel header.
 */
public class CollapsiblePanel extends JPanel
{
	private static final ImageIcon arrow_right = new ImageIcon(GraphicsUtils.getResourceImage("arrow_right.png"));
	private static final ImageIcon arrow_down = new ImageIcon(GraphicsUtils.getResourceImage("arrow_down.png"));
	
	private boolean collapsed;
	private final JButton toggleButton;
	private final JPanel disappearingPanel;
	
	public CollapsiblePanel(String headerText, JComponent collapsingComponent)
	{
		this(new JLabel(headerText), collapsingComponent);
	}
	
	public CollapsiblePanel(JComponent headerComponent, JComponent collapsingComponent)
	{
		toggleButton = new JButton(arrow_down);
		toggleButton.setMargin(new Insets(0, 0, 0, 0));
		toggleButton.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				setCollapsed(!collapsed);
			}
		});
		toggleButton.setToolTipText(XSTR.getString("collapseExpandTooltip"));
		
		// gotta set the alignments so that the components don't float around
		toggleButton.setAlignmentY(TOP_ALIGNMENT);
		Box.Filler filler1 = (Box.Filler) Box.createRigidArea(new Dimension(GUIConstants.SMALL_MARGIN, 0));
		filler1.setAlignmentY(TOP_ALIGNMENT);
		headerComponent.setAlignmentY(TOP_ALIGNMENT);
		Box.Filler filler2 = (Box.Filler) Box.createHorizontalGlue();
		filler2.setAlignmentY(TOP_ALIGNMENT);
		
		// now add them
		JPanel headerPanel = new JPanel();
		headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.X_AXIS));
		headerPanel.add(toggleButton);
		headerPanel.add(filler1);
		headerPanel.add(headerComponent);
		
		// this is useful for left-justifying JLabels, but it messes up alignment if we have an actual component
		if (headerComponent instanceof JLabel)
			headerPanel.add(filler2);
		
		disappearingPanel = new JPanel(new BorderLayout());
		disappearingPanel.setBorder(BorderFactory.createEmptyBorder(GUIConstants.SMALL_MARGIN, arrow_right.getIconWidth() + GUIConstants.SMALL_MARGIN, 0, 0));
		disappearingPanel.add(collapsingComponent, BorderLayout.CENTER);
		
		// and now put everything together
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(headerPanel);
		add(disappearingPanel);
	}
	
	public void setCollapsed(boolean collapse)
	{
		// keep track of state
		if (collapse == collapsed)
			return;
		collapsed = collapse;
		
		// set button image
		toggleButton.setIcon(collapsed ? arrow_right : arrow_down);
		
		// change the GUI
		disappearingPanel.setVisible(!collapsed);
		
		// repaint
		JRootPane rootPane = SwingUtilities.getRootPane(this);
		if (rootPane != null)
			rootPane.repaint();
	}
}
