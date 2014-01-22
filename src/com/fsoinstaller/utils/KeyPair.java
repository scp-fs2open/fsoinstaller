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
 * Wrapper class for encapsulating two objects in a single hash map key.
 * 
 * @author Goober5000
 */
public class KeyPair<T1, T2>
{
	private final T1 object1;
	private final T2 object2;
	
	public KeyPair(T1 object1, T2 object2)
	{
		this.object1 = object1;
		this.object2 = object2;
	}
	
	public T1 getObject1()
	{
		return object1;
	}
	
	public T2 getObject2()
	{
		return object2;
	}
	
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((object1 == null) ? 0 : object1.hashCode());
		result = prime * result + ((object2 == null) ? 0 : object2.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object object)
	{
		if (this == object)
			return true;
		else if (object == null)
			return false;
		else if (getClass() != object.getClass())
			return false;
		
		KeyPair<?, ?> other = (KeyPair<?, ?>) object;
		return MiscUtils.nullSafeEquals(object1, other.object1) && MiscUtils.nullSafeEquals(object2, other.object2);
	}
	
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder("[");
		sb.append(object1 == null ? "null" : object1.toString());
		sb.append(",");
		sb.append(object2 == null ? "null" : object2.toString());
		sb.append("]");
		return sb.toString();
	}
}
