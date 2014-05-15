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
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import com.fsoinstaller.utils.GraphicsUtils;


public class StoplightPanel extends JPanel
{
	private static final BufferedImage BRIGHT_GREEN = GraphicsUtils.getResourceImage("orb_green1.png");
	private static final BufferedImage BRIGHT_RED = GraphicsUtils.getResourceImage("orb_red1.png");
	private static final BufferedImage DULL_GREEN = GraphicsUtils.getResourceImage("orb_green3.png");
	private static final BufferedImage DULL_RED = GraphicsUtils.getResourceImage("orb_red3.png");
	
	private static final int ORB_WIDTH = 18;
	
	private final int height;
	
	private BufferedImage redImage;
	private BufferedImage greenImage;
	
	public StoplightPanel(int height)
	{
		this.height = height;
		this.redImage = DULL_RED;
		this.greenImage = DULL_GREEN;
	}
	
	public void setPending()
	{
		this.redImage = DULL_RED;
		this.greenImage = DULL_GREEN;
		if (getParent() != null)
			getParent().repaint();
	}
	
	public void setSuccess()
	{
		this.greenImage = BRIGHT_GREEN;
		this.redImage = DULL_RED;
		if (getParent() != null)
			getParent().repaint();
	}
	
	public void setFailure()
	{
		this.redImage = BRIGHT_RED;
		this.greenImage = DULL_GREEN;
		if (getParent() != null)
			getParent().repaint();
	}
	
	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		
		int width = (getWidth() - GUIConstants.SMALL_MARGIN) / 2;
		
		// to avoid stretching problems, we want to draw the icons centered at their respective halves and at their native sizes,
		// rather than taking up their entire halves
		
		int topLeftX = (width - ORB_WIDTH) / 2;
		int topLeftY = (height - ORB_WIDTH) / 2;
		
		g.drawImage(redImage, topLeftX, topLeftY, ORB_WIDTH, ORB_WIDTH, this);
		g.drawImage(greenImage, topLeftX + GUIConstants.SMALL_MARGIN + width, topLeftY, ORB_WIDTH, ORB_WIDTH, this);
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
