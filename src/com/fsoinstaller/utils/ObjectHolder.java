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

package com.fsoinstaller.utils;

/**
 * A simple wrapper class to hold a reference to an arbitrary object. Can be
 * used as a pass-by-reference parameter in a method or as a manually
 * synchronized version of AtomicReference.
 * 
 * @author Goober5000
 */
public class ObjectHolder<T>
{
	private T contents;
	
	public ObjectHolder()
	{
		this(null);
	}
	
	public ObjectHolder(T contents)
	{
		this.contents = contents;
	}
	
	public T get()
	{
		return contents;
	}
	
	public void set(T object)
	{
		this.contents = object;
	}
	
	@Override
	public boolean equals(Object object)
	{
		if (object == null)
			return false;
		else if (object == this)
			return true;
		else if (getClass() != object.getClass())
			return false;
		
		ObjectHolder<?> other = (ObjectHolder<?>) object;
		return MiscUtils.nullSafeEquals(contents, other.contents);
	}
	
	@Override
	public int hashCode()
	{
		return (contents == null) ? 0 : contents.hashCode();
	}
}
