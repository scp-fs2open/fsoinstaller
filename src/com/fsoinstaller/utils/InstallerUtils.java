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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fsoinstaller.common.BaseURL;
import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.common.InvalidBaseURLException;
import com.fsoinstaller.common.InstallerNode.FilePair;
import com.fsoinstaller.common.InstallerNode.HashTriple;
import com.fsoinstaller.common.InstallerNode.InstallUnit;
import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.utils.MiscUtils.OperatingSystem;

import static com.fsoinstaller.main.ResourceBundleManager.XSTR;


public class InstallerUtils
{
	private static final Logger logger = Logger.getLogger(InstallerUtils.class);
	
	private InstallerUtils()
	{
	}
	
	public static List<InstallerNode> maybeCreateAutomaticNodes()
	{
		Configuration configuration = Configuration.getInstance();
		List<InstallerNode> nodes = new ArrayList<InstallerNode>();
		
		// if OpenAL needs to be installed, do that first
		// (note, we can't use a primitive boolean because it may be null)
		Boolean installOpenAL = (Boolean) configuration.getSettings().get(Configuration.ADD_OPENAL_INSTALL_KEY);
		if (installOpenAL == Boolean.TRUE)
		{
			InstallerNode openAL = new InstallerNode(XSTR.getString("installOpenALName"));
			openAL.setDescription(XSTR.getString("installOpenALDesc"));
			openAL.setFolder(File.separator);
			openAL.setVersion(versionUUID());
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
				gog = new InstallerNode(XSTR.getString("installGOGName"));
				gog.setDescription(XSTR.getString("installGOGDesc"));
				gog.setFolder(UUID());
				gog.setVersion(versionUUID());
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
				gog.addInstall(installUnit);
				
				OperatingSystem os = MiscUtils.determineOS();
				if (os == OperatingSystem.WINDOWS)
				{
					installUnit.addFile("innoextract-1.4-windows-r2.zip");
					gog.addHashTriple(new HashTriple("MD5", "innoextract-1.4-windows-r2" + File.separator + "innoextract.exe", "5D8A2A1C3058FCEC812914F9B6DBEE68"));
				}
				else if (os == OperatingSystem.UNIX)
				{
					installUnit.addFile("innoextract-1.4-linux.zip");
					gog.addHashTriple(new HashTriple("MD5", "innoextract-1.4-linux" + File.separator + "innoextract", "AAE9EC9837C951E8908D242A68846974"));
					gog.addHashTriple(new HashTriple("MD5", "innoextract-1.4-linux" + File.separator + "bin" + File.separator + "amd64" + File.separator + "innoextract", "0BE2D7E5CF76DD368FC1FF245EE97D9E"));
					gog.addHashTriple(new HashTriple("MD5", "innoextract-1.4-linux" + File.separator + "bin" + File.separator + "armv6j-hardfloat" + File.separator + "innoextract", "87A48DBEE7A5BE8CD9B27C139D979017"));
					gog.addHashTriple(new HashTriple("MD5", "innoextract-1.4-linux" + File.separator + "bin" + File.separator + "i686" + File.separator + "innoextract", "310E477F2F911A147AD7AAEF7512DF06"));
				}
				
				// don't add any explicit commands because those will be handled in the InnoExtractTask class
				
				if (!installUnit.getBaseURLList().isEmpty())
					nodes.add(gog);
			}
			
			// if version 1.2 patch needs to be applied, then add it
			String hash = (String) configuration.getSettings().get(Configuration.ROOT_FS2_VP_HASH_KEY);
			if (hash != null && hash.equalsIgnoreCase("42bc56a410373112dfddc7985f66524a"))
			{
				InstallerNode patchTo1_2 = new InstallerNode(XSTR.getString("installPatchName"));
				patchTo1_2.setDescription(XSTR.getString("installPatchDesc"));
				patchTo1_2.setFolder(File.separator);
				patchTo1_2.setVersion(versionUUID());
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
			InstallerNode copyMVEs = new InstallerNode(XSTR.getString("installCopyCutscenesName"));
			copyMVEs.setDescription(XSTR.getString("installCopyCutscenesDesc"));
			copyMVEs.setFolder(File.separator);
			copyMVEs.setVersion(versionUUID());
			boolean doCopy = false;
			for (KeyPair<String, String> pair: Configuration.GOG_MOVIES)
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
	
	private static String UUID()
	{
		return UUID.randomUUID().toString().replaceAll("-", "");
	}
	
	private static String versionUUID()
	{
		return "UUID" + UUID();
	}
	
	public static List<InstallerNode> generateTreeWalk(List<InstallerNode> nodes)
	{
		List<InstallerNode> treeWalk = new ArrayList<InstallerNode>();
		
		for (InstallerNode node: nodes)
			generateTreeWalk(treeWalk, node);
		
		return treeWalk;
	}
	
	public static void generateTreeWalk(List<InstallerNode> treeWalk, InstallerNode root)
	{
		treeWalk.add(root);
		
		for (InstallerNode child: root.getChildren())
			generateTreeWalk(treeWalk, child);
	}
}
