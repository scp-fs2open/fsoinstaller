
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
	
	private String[] os_names()
	{
		return os_names;
	}
	
	private String[] mod_substrings()
	{
		return mod_substrings;
	}
	
	// the OS is not going to change, so let's cache it
	private static volatile OperatingSystem cachedHostOS = null;
	
	/**
	 * Determines the host operating system by examining the Java "os.name"
	 * property.
	 */
	public static OperatingSystem getHostOS()
	{
		OperatingSystem result = cachedHostOS;
		
		// figure it out if not yet cached
		if (result == null)
		{
			result = OperatingSystem.OTHER;
			
			String os_name_lcase = System.getProperty("os.name").toLowerCase();
osvalues:	for (OperatingSystem os: OperatingSystem.values())
			{
				for (String os_name: os.os_names())
				{
					if (os_name_lcase.startsWith(os_name))
					{
						result = os;
						break osvalues;
					}
				}
			}
			
			// now cache it
			// (it's okay if there are redundant caches because multiple computations will all obtain the same OS)
			cachedHostOS = result;
		}
		
		return result;
	}
	
	/**
	 * Checks whether a mod can be installed on this operating system. It is
	 * assumed that all mods can be installed on all systems unless the mod
	 * contains an operating system in its name.
	 */
	public boolean isModValidForOS(String modName)
	{
		// for nonspecific systems, all mods are valid
		if (this == OperatingSystem.OTHER)
			return true;
		
		// all we need to do is make sure the name doesn't match some other OS
		for (OperatingSystem os: OperatingSystem.values())
		{
			// obviously we're okay with matching our own OS...
			if (os != this)
			{
				// exclude all mods which have an match for some other OS
				String mod_lcase = modName.toLowerCase();
				for (String mod_substring: os.mod_substrings())
				{
					if (mod_lcase.contains(mod_substring))
						return false;
				}
			}
		}
		
		// no exclusions found
		return true;
	}
}
