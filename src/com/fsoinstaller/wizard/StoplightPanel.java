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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JPanel;


public class StoplightPanel extends JPanel
{
	private static final Color DULL_RED = new Color(63, 0, 0);
	private static final Color DULL_GREEN = new Color(0, 63, 0);
	
	private final int height;
	private Color redColor;
	private Color greenColor;
	
	public StoplightPanel(int height)
	{
		this.height = height;
		this.redColor = DULL_RED;
		this.greenColor = DULL_GREEN;
	}
	
	public void setPending()
	{
		this.redColor = DULL_RED;
		this.greenColor = DULL_GREEN;
		if (getParent() != null)
			getParent().repaint();
	}
	
	public void setSuccess()
	{
		this.greenColor = Color.GREEN;
		if (getParent() != null)
			getParent().repaint();
	}
	
	public void setFailure()
	{
		this.redColor = Color.RED;
		if (getParent() != null)
			getParent().repaint();
	}
	
	@Override
	protected void paintComponent(Graphics g)
	{
		int width = (getWidth() - GUIConstants.SMALL_MARGIN) / 2;
		
		g.setColor(redColor);
		g.fillOval(0, 0, width, height);
		
		g.setColor(greenColor);
		g.fillOval(width + GUIConstants.SMALL_MARGIN, 0, width, height);
	}
	
	private Dimension calculateSize()
	{
		return new Dimension(height * 2 + GUIConstants.SMALL_MARGIN, height);
	}
	
	@Override
	public Dimension getMinimumSize()
	{
		return calculateSize();
	}
	
	@Override
	public Dimension getPreferredSize()
	{
		return calculateSize();
	}
	
	@Override
	public Dimension getMaximumSize()
	{
		return calculateSize();
	}
}
