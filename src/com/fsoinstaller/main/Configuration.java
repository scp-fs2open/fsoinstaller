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

package com.fsoinstaller.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fsoinstaller.utils.KeyPair;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.MiscUtils;
import com.fsoinstaller.utils.OperatingSystem;
import com.fsoinstaller.utils.PropertiesUtils;


/**
 * Thread-safe class which manages access to global state.
 * 
 * @author Goober5000
 */
public class Configuration
{
	private static final Logger logger = Logger.getLogger(Configuration.class);
	
	public static final List<KeyPair<String, String>> GOG_MOVIES;
	static
	{
		List<KeyPair<String, String>> temp = new ArrayList<KeyPair<String, String>>();
		temp.add(new KeyPair<String, String>("INTRO.MVE", "data2"));
		temp.add(new KeyPair<String, String>("MONO1.MVE", "data2"));
		temp.add(new KeyPair<String, String>("COLOSSUS.MVE", "data2"));
		temp.add(new KeyPair<String, String>("MONO2.MVE", "data3"));
		temp.add(new KeyPair<String, String>("MONO3.MVE", "data3"));
		temp.add(new KeyPair<String, String>("MONO4.MVE", "data3"));
		temp.add(new KeyPair<String, String>("BASTION.MVE", "data3"));
		temp.add(new KeyPair<String, String>("ENDPART1.MVE", "data3"));
		temp.add(new KeyPair<String, String>("ENDPRT2A.MVE", "data3"));
		temp.add(new KeyPair<String, String>("ENDPRT2B.MVE", "data3"));
		
		GOG_MOVIES = Collections.unmodifiableList(temp);
	}
	
	// these are for the settings
	public static final String PROXY_KEY = "PROXY";
	public static final String CONNECTOR_KEY = "CONNECTOR";
	public static final String REMOTE_VERSION_KEY = "REMOTE-VERSION";
	public static final String MOD_URLS_KEY = "MOD-URLS";
	public static final String BASIC_CONFIG_MODS_KEY = "BASIC-CONFIG-MODS";
	public static final String MOD_NODES_KEY = "MOD-NODES";
	public static final String AUTOMATIC_NODES_KEY = "AUTOMATIC-NODES";
	public static final String INSTALL_CHOICE_KEY = "INSTALL-CHOICE";
	public static final String MODS_TO_INSTALL_KEY = "MODS-TO-INSTALL";
	public static final String CHECKED_DIRECTORIES_KEY = "CHECKED-DIRECTORIES";
	public static final String INSTALL_NOTES_KEY = "INSTALL-NOTES";
	public static final String INSTALL_ERRORS_KEY = "INSTALL-ERRORS";
	public static final String ROOT_FS2_VP_HASH_KEY = "ROOT-FS2-VP-HASH";
	public static final String GOG_INSTALL_PACKAGE_KEY = "GOG-INSTALL-PACKAGE";
	public static final String STEAM_INSTALL_LOCATION_KEY = "STEAM-INSTALL-LOCATION";
	public static final String ADD_OPENAL_INSTALL_KEY = "ADD-OPENAL-INSTALL";
	public static final String DONT_SHORT_CIRCUIT_INSTALLATION_KEY = "DON'T-SHORT-CIRCUIT-INSTALLATION";
	public static final String OVERRIDE_INSTALL_MOD_NODES_KEY = "OVERRIDE-INSTALL-MOD-FILE";
	
	/**
	 * Use the Initialization On Demand Holder idiom for thread-safe
	 * non-synchronized singletons.
	 */
	private static final class InstanceHolder
	{
		private static final Configuration INSTANCE = new Configuration();
	}
	
	public static Configuration getInstance()
	{
		return InstanceHolder.INSTANCE;
	}
	
	private final Properties applicationProperties;
	private final Properties userProperties;
	private final Map<String, Object> settings;
	
	// prevent instantiation
	private Configuration()
	{
		Properties temp;
		
		// this should always be present, but we technically allow it to be absent because we always supply defaults
		temp = PropertiesUtils.loadProperties("application.properties");
		if (temp == null)
		{
			logger.error("No application.properties file could be found!");
			temp = new Properties();
		}
		applicationProperties = temp;
		
		// this could be created upon first run but is otherwise persistent
		String userPropertiesName = applicationProperties.getProperty("application.userproperties", "fsoinstaller.properties");
		temp = PropertiesUtils.loadProperties(userPropertiesName);
		if (temp == null)
		{
			logger.info("No " + userPropertiesName + " file could be found; a new one will be created");
			temp = new Properties();
		}
		userProperties = temp;
		
		// this is always created anew for each run
		// (since Properties inherits from Hashtable, it is already thread-safe)
		settings = Collections.synchronizedMap(new HashMap<String, Object>());
	}
	
	// APPLICATION PROPERTIES ----------
	// these should always return a value or a default
	
	public String getApplicationTitle()
	{
		return applicationProperties.getProperty("application.title", "FreeSpace Open Installer");
	}
	
	public String getDefaultDir()
	{
		String dir = null;
		
		switch (OperatingSystem.getHostOS())
		{
			case WINDOWS:
				dir = applicationProperties.getProperty("application.defaultdir.windows");
				break;
			
			case MAC:
				dir = applicationProperties.getProperty("application.defaultdir.mac");
				if (dir == null)
					dir = applicationProperties.getProperty("application.defaultdir.osx");
				break;
			
			case LINUX:
				dir = applicationProperties.getProperty("application.defaultdir.linux");
				if (dir == null)
					dir = applicationProperties.getProperty("application.defaultdir.unix");
				break;
			
			case FREEBSD:
				dir = applicationProperties.getProperty("application.defaultdir.freebsd");
				break;
			
			case SOLARIS:
				dir = applicationProperties.getProperty("application.defaultdir.solaris");
				break;
			
			case OTHER:
			default:
				break;
		}
		
		// must always have a non-null default
		if (dir == null)
			dir = applicationProperties.getProperty("application.defaultdir", "/Games/FreeSpace2");
		
		dir = dir.trim();
		
		// Java doesn't expand tilde, so let's do it ourselves
		if (dir.startsWith("~" + File.separator))
			dir = MiscUtils.getUserHome() + dir.substring(1);
		
		return dir;
	}
	
	public boolean requiresFS2()
	{
		String string = applicationProperties.getProperty("application.requiresfs2", "true");
		return Boolean.parseBoolean(string);
	}
	
	public String getUserPropertiesName()
	{
		return applicationProperties.getProperty("application.userproperties", "fsoinstaller.properties");
	}
	
	public List<String> getAllowedVPs()
	{
		String string = applicationProperties.getProperty("application.allowedvps", "root_fs2.vp,sparky_fs2.vp,sparky_hi_fs2.vp,stu_fs2.vp,tango1_fs2.vp,tango2_fs2.vp,tango3_fs2.vp,warble_fs2.vp,smarty_fs2.vp,FS2OGGcutscenepack.vp,multi-mission-pack.vp,multi-voice-pack.vp");
		
		List<String> vpList = new ArrayList<String>();
		String[] vpArray = string.toLowerCase().split(",");
		for (String vp: vpArray)
			vpList.add(vp.trim());
		
		return vpList;
	}
	
	// USER PROPERTIES ----------
	// these could be null
	
	public Properties getUserProperties()
	{
		return userProperties;
	}
	
	public boolean saveUserProperties()
	{
		String userPropertiesName = applicationProperties.getProperty("application.userproperties", "fsoinstaller.properties");
		return PropertiesUtils.saveProperties(userPropertiesName, userProperties);
	}
	
	public String getProxyHost()
	{
		// don't reference XSTR earlier than we have to, since Configuration is initialized at the same time as FreeSpaceOpenInstaller
		String NONE = ResourceBundleManager.XSTR.getString("none");
		
		String host = userProperties.getProperty("proxy.host");
		if (host == null || host.equalsIgnoreCase(NONE) || host.length() == 0)
			return null;
		return host;
	}
	
	public int getProxyPort()
	{
		String port = userProperties.getProperty("proxy.port");
		try
		{
			return Integer.parseInt(port);
		}
		catch (NumberFormatException re)
		{
			return -1;
		}
	}
	
	public void setProxyInfo(String host, int port)
	{
		// don't reference XSTR earlier than we have to, since Configuration is initialized at the same time as FreeSpaceOpenInstaller
		String NONE = ResourceBundleManager.XSTR.getString("none");
		
		// resolve host
		if (host == null || host.equalsIgnoreCase(NONE) || host.length() == 0)
			host = null;
		
		// store either both or none
		boolean valid = (host != null && port >= 0);
		
		userProperties.setProperty("proxy.host", valid ? host : NONE);
		userProperties.setProperty("proxy.port", valid ? Integer.toString(port) : NONE);
	}
	
	public File getApplicationDir()
	{
		String fileName = userProperties.getProperty("application.dir");
		return MiscUtils.validateApplicationDir(fileName);
	}
	
	public void setApplicationDir(File dir)
	{
		// don't reference XSTR earlier than we have to, since Configuration is initialized at the same time as FreeSpaceOpenInstaller
		String NONE = ResourceBundleManager.XSTR.getString("none");
		
		String dirStr = MiscUtils.validateApplicationDir(dir) ? dir.getAbsolutePath() : NONE;
		userProperties.setProperty("application.dir", dirStr);
	}
	
	// SETTINGS ----------
	// these can pretty much be anything 	
	
	public Map<String, Object> getSettings()
	{
		return settings;
	}
}
