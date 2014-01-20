
package com.fsoinstaller.builder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.JFrame;
import javax.swing.JTree;

import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.common.InstallerNodeFactory;
import com.fsoinstaller.common.InstallerNodeParseException;
import com.fsoinstaller.common.InstallerNodeRoot;
import com.fsoinstaller.common.InstallerNodeTreeModel;
import com.fsoinstaller.internet.Connector;
import com.fsoinstaller.internet.Downloader;
import com.fsoinstaller.internet.InvalidProxyException;


public class TestTree extends JFrame
{
	public TestTree() throws MalformedURLException, InvalidProxyException, IOException, InstallerNodeParseException
	{
		
		Reader reader = new FileReader("dist/scp_files.txt");
		
		InstallerNodeRoot root = new InstallerNodeRoot();
		
		while (true)
		{
			InstallerNode node = InstallerNodeFactory.readNode(reader);
			if (node == null)
				break;
			root.addChild(node);
		}

		reader.close();
		
		
		Writer writer = new FileWriter("dist/new.txt");
		
		for (InstallerNode node: root.getChildren())
		{
			InstallerNodeFactory.writeNode(writer, node);
		}
		
		writer.close();

		JTree tree = new JTree(new InstallerNodeTreeModel(root));
		add(tree);		
	}

	public static void main(String[] args) throws IOException, InvalidProxyException, InstallerNodeParseException
	{
		/*
		JFrame frame = new TestTree();
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setVisible(true);
		*/
		new TestTree();
		/*		
		Connector connector = new Connector();
		Downloader downloader = new Downloader(connector);
		
		URL url = new URL("http://fsport.hard-light.net/wip/FSPort31Campaigns/Aftermath.7z");
		InputStream is = url.openStream();
		is.read(new byte[8192]);
		System.out.println(is.getClass());
		is.close();
System.exit(0);		
		File dir = new File("C:\\temp\\download\\");
		boolean result = downloader.download(url, dir);
		System.out.println(result);
*/
	}
}
