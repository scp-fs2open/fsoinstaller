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

package com.fsoinstaller.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fsoinstaller.utils.Logger;


/**
 * This class represents a single unit of installation, such as a mod, with a
 * group of associated files.
 * 
 * @author Goober5000
 */
public class InstallerNode
{
	private static final Logger logger = Logger.getLogger(InstallerNode.class);
	
	// possible flags for flag list
	public static final String EXCLUDE_FROM_COMPLETE_INSTALLATION = "EXCLUDE-FROM-COMPLETE-INSTALLATION";
	public static final List<String> ALL_FLAGS = Collections.unmodifiableList(Arrays.asList(EXCLUDE_FROM_COMPLETE_INSTALLATION));
	
	protected String name;
	protected String description;
	protected String folder;
	protected String radioButtonGroup;
	protected String version;
	protected String note;
	
	protected String treePath;
	
	protected final List<String> deleteList;
	protected final List<FilePair> renameList;
	protected final List<FilePair> copyList;
	protected final List<InstallUnit> installList;
	protected final List<HashTriple> hashList;
	protected final List<String> cmdList;
	protected final List<String> dependencyList;
	protected final List<String> flagList;
	
	protected InstallerNode parent;
	protected final List<InstallerNode> children;
	
	protected Object userObject;
	
	public InstallerNode(String name)
	{
		if (name == null)
			throw new NullPointerException("The 'name' field cannot be null!");
		
		this.name = name;
		this.description = null;
		this.folder = null;
		this.radioButtonGroup = null;
		this.version = null;
		this.note = null;
		
		this.deleteList = new ArrayList<String>();
		this.renameList = new ArrayList<FilePair>();
		this.copyList = new ArrayList<FilePair>();
		this.installList = new ArrayList<InstallUnit>();
		this.hashList = new ArrayList<HashTriple>();
		this.cmdList = new ArrayList<String>();
		this.dependencyList = new ArrayList<String>();
		this.flagList = new ArrayList<String>();
		
		this.parent = null;
		this.children = new ArrayList<InstallerNode>();
		
		this.userObject = null;
	}
	
	public String getName()
	{
		return name;
	}
	
	public void setName(String name)
	{
		if (name == null)
			throw new NullPointerException("The 'name' field cannot be null!");
		
		this.name = name;
	}
	
	/**
	 * It is assumed that the tree path of a node will never change after a
	 * certain point before which we care about calling this method.
	 * <p>
	 * (This isn't synchronized, but since the tree path is assumed to be
	 * immutable, that shouldn't matter.)
	 */
	public String getTreePath()
	{
		if (treePath == null)
		{
			// construct tree path to this node
			StringBuilder builder = new StringBuilder();
			InstallerNode ii = this;
			while (ii != null)
			{
				builder.insert(0, '.');
				builder.insert(0, ii.getName());
				ii = ii.getParent();
			}
			
			// get rid of trailing period
			builder.setLength(builder.length() - 1);
			
			treePath = builder.toString();
		}
		
		return treePath;
	}
	
	public String getDescription()
	{
		return description;
	}
	
	public void setDescription(String description)
	{
		this.description = description;
	}
	
	public String getFolder()
	{
		return folder;
	}
	
	public void setFolder(String folder)
	{
		this.folder = folder;
	}
	
	public String getRadioButtonGroup()
	{
		return radioButtonGroup;
	}
	
	public void setRadioButtonGroup(String group)
	{
		this.radioButtonGroup = group;
	}
	
	public String getVersion()
	{
		return version;
	}
	
	public void setVersion(String version)
	{
		this.version = version;
	}
	
	public String getNote()
	{
		return note;
	}
	
	public void setNote(String note)
	{
		this.note = note;
	}
	
	public List<String> getDeleteList()
	{
		return Collections.unmodifiableList(deleteList);
	}
	
	public List<FilePair> getRenameList()
	{
		return Collections.unmodifiableList(renameList);
	}
	
	public List<FilePair> getCopyList()
	{
		return Collections.unmodifiableList(copyList);
	}
	
	public List<InstallUnit> getInstallList()
	{
		return Collections.unmodifiableList(installList);
	}
	
	public List<HashTriple> getHashList()
	{
		return Collections.unmodifiableList(hashList);
	}
	
	public List<String> getExecCmdList()
	{
		return Collections.unmodifiableList(cmdList);
	}
	
	public List<String> getDependencyList()
	{
		return Collections.unmodifiableList(dependencyList);
	}
	
	public List<String> getFlagList()
	{
		return Collections.unmodifiableList(flagList);
	}
	
	public List<InstallerNode> getChildren()
	{
		return Collections.unmodifiableList(children);
	}
	
	public InstallerNode getParent()
	{
		return parent;
	}
	
	public Object getUserObject()
	{
		return userObject;
	}
	
	public void addDelete(String deleteItem)
	{
		if (deleteItem == null)
			throw new NullPointerException("Cannot add a null delete item!");
		
		deleteList.add(deleteItem);
	}
	
	public void removeDelete(String deleteItem)
	{
		deleteList.remove(deleteItem);
	}
	
	public void addRenamePair(FilePair renamePair)
	{
		if (renamePair == null)
			throw new NullPointerException("Cannot add a null rename pair!");
		
		renameList.add(renamePair);
	}
	
	public void removeRenamePair(FilePair renamePair)
	{
		renameList.remove(renamePair);
	}
	
	public void addCopyPair(FilePair copyPair)
	{
		if (copyPair == null)
			throw new NullPointerException("Cannot add a null copy pair!");
		
		copyList.add(copyPair);
	}
	
	public void removeCopyPair(FilePair copyPair)
	{
		copyList.remove(copyPair);
	}
	
	public void addInstall(InstallUnit installUnit)
	{
		if (installUnit == null)
			throw new NullPointerException("Cannot add a null install unit!");
		
		installList.add(installUnit);
	}
	
	public void removeInstall(InstallUnit installUnit)
	{
		installList.remove(installUnit);
	}
	
	public void addHashTriple(HashTriple hashTriple)
	{
		if (hashTriple == null)
			throw new NullPointerException("Cannot add a null hash triple!");
		
		hashList.add(hashTriple);
	}
	
	public void removeHashTriple(HashTriple hashTriple)
	{
		hashList.remove(hashTriple);
	}
	
	public void addExecCmd(String cmd)
	{
		if (cmd == null)
			throw new NullPointerException("Cannot add a null exec command!");
		
		cmdList.add(cmd);
	}
	
	public void removeExecCmd(String cmd)
	{
		cmdList.remove(cmd);
	}
	
	public void addDependency(String dependency)
	{
		if (dependency == null)
			throw new NullPointerException("Cannot add a null dependency!");
		
		dependencyList.add(dependency);
	}
	
	public void removeDependency(String dependency)
	{
		dependencyList.remove(dependency);
	}
	
	public void addFlag(String flag)
	{
		if (flag == null)
			throw new NullPointerException("Cannot add a null flag!");
		
		flag = flag.toUpperCase();
		
		if (!ALL_FLAGS.contains(flag))
			logger.warn("Tried to add flag '" + flag + "' that is not recognized as an allowed flag!");
		else if (!flagList.contains(flag))
			flagList.add(flag);
	}
	
	public void removeFlag(String flag)
	{
		flagList.remove(flag);
	}
	
	public void addChild(InstallerNode installerNode)
	{
		if (installerNode == null)
			throw new NullPointerException("Cannot add a null child!");
		
		children.add(installerNode);
		installerNode.parent = this;
	}
	
	public void removeChild(InstallerNode installerNode)
	{
		children.remove(installerNode);
		installerNode.parent = null;
	}
	
	public void setUserObject(Object userObject)
	{
		this.userObject = userObject;
	}
	
	@Override
	public boolean equals(Object object)
	{
		if (this == object)
			return true;
		if (object == null)
			return false;
		if (getClass() != object.getClass())
			return false;
		InstallerNode other = (InstallerNode) object;
		return name.equals(other.name);
	}
	
	@Override
	public int hashCode()
	{
		return name.hashCode();
	}
	
	@Override
	public String toString()
	{
		return name;
	}
	
	public InstallerNode findTreePath(String treePath)
	{
		if (treePath == null)
			throw new NullPointerException("Tree path cannot be null!");
		
		if (treePath.equals(getTreePath()))
			return this;
		
		for (InstallerNode child: this.children)
		{
			InstallerNode result = child.findTreePath(treePath);
			if (result != null)
				return result;
		}
		
		return null;
	}
	
	public static class FilePair
	{
		private String from;
		private String to;
		
		public FilePair(String from, String to)
		{
			if (from == null || to == null)
				throw new NullPointerException("Arguments cannot be null!");
			
			this.from = from;
			this.to = to;
		}
		
		public String getFrom()
		{
			return from;
		}
		
		public void setFrom(String from)
		{
			if (from == null)
				throw new NullPointerException("The 'from' field cannot be null!");
			
			this.from = from;
		}
		
		public String getTo()
		{
			return to;
		}
		
		public void setTo(String to)
		{
			if (from == null)
				throw new NullPointerException("The 'to' field cannot be null!");
			
			this.to = to;
		}
	}
	
	public static class HashTriple
	{
		private String algorithm;
		private String filename;
		private String hash;
		
		public HashTriple(String algorithm, String filename, String hash)
		{
			if (algorithm == null || filename == null || hash == null)
				throw new NullPointerException("Arguments cannot be null!");
			
			this.algorithm = algorithm;
			this.filename = filename;
			this.hash = hash;
		}
		
		public String getAlgorithm()
		{
			return algorithm;
		}
		
		public void setAlgorithm(String algorithm)
		{
			if (algorithm == null)
				throw new NullPointerException("The 'algorithm' field cannot be null!");
			
			this.algorithm = algorithm;
		}
		
		public String getFilename()
		{
			return filename;
		}
		
		public void setFilename(String filename)
		{
			if (filename == null)
				throw new NullPointerException("The 'filename' field cannot be null!");
			
			this.filename = filename;
		}
		
		public String getHash()
		{
			return hash;
		}
		
		public void setHash(String hash)
		{
			if (hash == null)
				throw new NullPointerException("The 'hash' field cannot be null!");
			
			this.hash = hash;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if (obj == null)
				return false;
			else if (obj == this)
				return true;
			else if (!obj.getClass().equals(getClass()))
				return false;
			else
				// only the filename matters
				return filename.equals(((HashTriple) obj).filename);
		}
		
		@Override
		public int hashCode()
		{
			return filename.hashCode();
		}
	}
	
	public static class PatchTriple
	{
		private String patchType;
		private HashTriple prePatch;
		private HashTriple patch;
		private HashTriple postPatch;
		
		public PatchTriple(String patchType, HashTriple prePatch, HashTriple patch, HashTriple postPatch)
		{
			if (patchType == null || prePatch == null || patch == null || postPatch == null)
				throw new NullPointerException("Arguments cannot be null!");
			
			this.patchType = patchType;
			this.prePatch = prePatch;
			this.patch = patch;
			this.postPatch = postPatch;
		}
		
		public String getPatchType()
		{
			return patchType;
		}
		
		public void setPatchType(String patchType)
		{
			if (prePatch == null)
				throw new NullPointerException("The 'patchType' field cannot be null!");
			
			this.patchType = patchType;
		}
		
		public HashTriple getPrePatch()
		{
			return prePatch;
		}
		
		public void setPrePatch(HashTriple prePatch)
		{
			if (prePatch == null)
				throw new NullPointerException("The 'prePatch' field cannot be null!");
			
			this.prePatch = prePatch;
		}
		
		public HashTriple getPatch()
		{
			return patch;
		}
		
		public void setPatch(HashTriple patch)
		{
			if (patch == null)
				throw new NullPointerException("The 'patch' field cannot be null!");
			
			this.patch = patch;
		}
		
		public HashTriple getPostPatch()
		{
			return postPatch;
		}
		
		public void setPostPatch(HashTriple postPatch)
		{
			if (postPatch == null)
				throw new NullPointerException("The 'postPatch' field cannot be null!");
			
			this.postPatch = postPatch;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			if (obj == null)
				return false;
			else if (obj == this)
				return true;
			else if (!obj.getClass().equals(getClass()))
				return false;
			else
				// only the before and after triples matter
				return patchType.equals(((PatchTriple) obj).patchType)
						&& prePatch.equals(((PatchTriple) obj).prePatch)
						&& postPatch.equals(((PatchTriple) obj).postPatch);
		}
		
		@Override
		public int hashCode()
		{
			int result = 17;
			result = 31 * result + patchType.hashCode();
			result = 31 * result + prePatch.hashCode();
			result = 31 * result + postPatch.hashCode();
			return result;
		}
	}
	
	public static class InstallUnit
	{
		private List<BaseURL> baseURLList;
		private List<String> fileList;
		private List<PatchTriple> patchList;
		
		public InstallUnit()
		{
			this.baseURLList = new ArrayList<BaseURL>();
			this.fileList = new ArrayList<String>();
			this.patchList = new ArrayList<PatchTriple>();
		}
		
		public List<BaseURL> getBaseURLList()
		{
			return Collections.unmodifiableList(baseURLList);
		}
		
		public List<String> getFileList()
		{
			return Collections.unmodifiableList(fileList);
		}
		
		public List<PatchTriple> getPatchList()
		{
			return Collections.unmodifiableList(patchList);
		}
		
		public void addBaseURL(BaseURL baseURL)
		{
			if (baseURL == null)
				throw new NullPointerException("Cannot add a null base URL!");
			
			// ensure no duplicates
			if (!baseURLList.contains(baseURL))
				baseURLList.add(baseURL);
		}
		
		public void removeBaseURL(BaseURL baseURL)
		{
			baseURLList.remove(baseURL);
		}
		
		public void addFile(String file)
		{
			if (file == null)
				throw new NullPointerException("Cannot add a null file!");
			
			// ensure no duplicates
			if (!fileList.contains(file))
				fileList.add(file);
		}
		
		public void removeFile(String file)
		{
			fileList.remove(file);
		}
		
		public void addPatchTriple(PatchTriple patchTriple)
		{
			if (patchTriple == null)
				throw new NullPointerException("Cannot add a null patch triple!");
			
			patchList.add(patchTriple);
		}
		
		public void removePatchTriple(PatchTriple patchTriple)
		{
			patchList.remove(patchTriple);
		}
	}
}
