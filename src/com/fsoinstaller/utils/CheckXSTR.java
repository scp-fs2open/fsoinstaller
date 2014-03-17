
package com.fsoinstaller.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CheckXSTR
{
	public static void main(String[] args)
	{
		// get all java files
		File root = new File("src");
		List<File> javaFiles = new ArrayList<File>();
		addJavaFilesInTree(root, javaFiles);
		// get all keys from them
		Set<String> javaKeys = new HashSet<String>();
		for (File file: javaFiles)
			addKeysInJavaFile(file, javaKeys);
		
		// get all keys from XSTR
		Properties XSTR = PropertiesUtils.loadProperties("resources/XSTR.properties");
		Set<String> XSTRkeys = XSTR.stringPropertyNames();
		
		// find the differences
		Set<String> javaKeysNotInXSTR = new HashSet<String>();
		Set<String> XSTRKeysNotInJava = new HashSet<String>();
		for (String javaKey: javaKeys)
			if (!XSTRkeys.contains(javaKey))
				javaKeysNotInXSTR.add(javaKey);
		for (String XSTRkey: XSTRkeys)
			if (!javaKeys.contains(XSTRkey))
				XSTRKeysNotInJava.add(XSTRkey);
		
		if (javaKeysNotInXSTR.isEmpty() && XSTRKeysNotInJava.isEmpty())
		{
			System.out.println("All keys are matched!");
		}
		else
		{
			if (!javaKeysNotInXSTR.isEmpty())
			{
				System.out.println("The following keys are in .java files but not in XSTR.properties:");
				for (String key: javaKeysNotInXSTR)
					System.out.println(key);
			}
			if (!XSTRKeysNotInJava.isEmpty())
			{
				System.out.println("The following keys are in XSTR.properties but not in .java files:");
				for (String key: XSTRKeysNotInJava)
					System.out.println(key);
			}
		}
	}
	
	private static void addJavaFilesInTree(File root, List<File> fileList)
	{
		if (root.isDirectory())
		{
			File[] files = root.listFiles();
			if (files == null)
				throw new IllegalStateException("Failed to list files in " + root.getAbsolutePath());
			
			for (File file: files)
				addJavaFilesInTree(file, fileList);
		}
		else
		{
			if (root.getName().endsWith(".java"))
				fileList.add(root);
		}
	}
	
	private static void addKeysInJavaFile(File file, Set<String> keys)
	{
		Pattern pattern = Pattern.compile("XSTR\\.getString\\(\"([^\"]*)\"\\)");
		List<String> fileLines = IOUtils.readTextFileCleanly(file);
		for (String line: fileLines)
		{
			Matcher m = pattern.matcher(line);
			while (m.find())
				keys.add(m.group(1));
		}
	}
}
