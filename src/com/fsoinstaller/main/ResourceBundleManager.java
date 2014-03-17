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

package com.fsoinstaller.main;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;


/**
 * Thread-safe class which provides a ResourceBundle so that the application can
 * display locale-specific translations.
 * 
 * @author Goober5000
 */
public class ResourceBundleManager
{
	public static final ResourceBundle XSTR;
	static
	{
		// determine current locale (which is specified by the system)
		Locale locale = Locale.getDefault();
		
		try
		{
			// load the appropriate properties file, e.g. XSTR_en.properties, XSTR_fr.properties, XSTR_es.properties
			// (there should always be a plain old XSTR.properties for fallback)
			XSTR = ResourceBundle.getBundle("resources.XSTR", locale);
		}
		catch (MissingResourceException mre)
		{
			throw new AssertionError("Could not load an XSTR resource bundle!", mre);
		}
	}
}
