
package com.fsoinstaller.utils;

public enum OperatingSystem
{
	WINDOWS("windows"),
	MAC("mac os", new String[]
	{
		"macintosh",
		"osx",
		"os x"
	}),
	LINUX(new String[]
	{
		"linux",
		"unix"
	}),
	FREEBSD("freebsd"),
	SOLARIS("solaris"),
	OTHER(new String[0]);
	
	private final String[] os_names;
	private final String[] mod_substrings;
	
	OperatingSystem(Object os_names)
	{
		this(os_names, os_names);
	}
	
	OperatingSystem(Object os_names, Object mod_substrings)
	{
		this.os_names = (os_names instanceof String) ? new String[]
		{
			(String) os_names
		} : (String[]) os_names;
		this.mod_substrings = (mod_substrings instanceof String) ? new String[]
		{
			(String) mod_substrings
		} : (String[]) mod_substrings;
	}
	
	public String[] os_names()
	{
		return os_names;
	}
	
	public String[] mod_substrings()
	{
		return mod_substrings;
	}
}
