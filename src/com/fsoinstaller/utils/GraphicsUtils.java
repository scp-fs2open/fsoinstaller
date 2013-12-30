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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;


/**
 * Utility methods for working with graphics.
 * 
 * @author Goober5000
 */
public class GraphicsUtils
{
	private static final Logger logger = Logger.getLogger(GraphicsUtils.class);
	
	/**
	 * Prevent instantiation.
	 */
	private GraphicsUtils()
	{
	}
	
	public static BufferedImage getResourceImage(String resource)
	{
		InputStream is = IOUtils.getResourceStream(resource);
		if (is == null)
		{
			logger.error("The resource '" + resource + "' could not be found!");
			return null;
		}
		
		BufferedImage image = null;
		try
		{
			try
			{
				image = ImageIO.read(is);
				if (image == null)
					logger.error("The resource '" + resource + "' was found, but it was read as a null image!");
			}
			finally
			{
				is.close();
			}
		}
		catch (IOException ioe)
		{
			logger.error("There was a problem reading the image '" + resource + "'!", ioe);
		}
		
		return image;
	}
}
