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

package com.fsoinstaller.wizard;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.JLabel;
import javax.swing.JPanel;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


/**
 * This is a panel that just paints an image while staying the size of that
 * image.
 * 
 * @author Goober5000
 */
public class ImagePanel extends JPanel
{
	private transient final Image image;
	private final int width;
	private final int height;
	
	public ImagePanel(BufferedImage image)
	{
		this.image = image;
		
		int w, h;
		if (image != null)
		{
			w = image.getWidth();
			h = image.getHeight();
		}
		else
		{
			JLabel label = new JLabel(XSTR.getString("imageNotAvailable"));
			label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() + 2));
			label.setForeground(Color.GRAY);
			
			setLayout(new BorderLayout());
			add(label, BorderLayout.CENTER);
			w = label.getWidth();
			h = label.getHeight();
		}
		
		this.width = w;
		this.height = h;
		
		Dimension size = new Dimension(width, height);
		setMinimumSize(size);
		setMaximumSize(size);
		setPreferredSize(size);
	}
	
	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		
		if (image != null)
		{
			g.drawImage(image, 0, 0, width, height, this);
		}
		else
		{
			Color savedColor = g.getColor();
			
			// red X indicates image not available
			g.setColor(Color.RED);
			g.drawLine(0, 0, width, height);
			g.drawLine(0, height, width, 0);
			
			g.setColor(savedColor);
		}
	}
}
