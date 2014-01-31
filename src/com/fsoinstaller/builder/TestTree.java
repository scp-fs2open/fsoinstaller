
package com.fsoinstaller.builder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JTree;

import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.common.InstallerNodeParseException;
import com.fsoinstaller.common.InstallerNodeRoot;
import com.fsoinstaller.common.InstallerNodeTreeModel;
import com.fsoinstaller.utils.IOUtils;


public class TestTree extends JFrame
{
	public TestTree() throws FileNotFoundException, IOException, InstallerNodeParseException
	{
		File file = new File("dist/scp_files.txt");
		
		InstallerNodeRoot root = new InstallerNodeRoot();
		for (InstallerNode node: IOUtils.readInstallFile(file))
			root.addChild(node);
		
		JTree tree = new JTree(new InstallerNodeTreeModel(root));
		add(tree);
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException, InstallerNodeParseException
	{
		JFrame frame = new TestTree();
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setVisible(true);
	}
}
