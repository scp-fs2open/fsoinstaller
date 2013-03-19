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

package com.fsoinstaller.wizard;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.fsoinstaller.internet.Connector;
import com.fsoinstaller.internet.DownloadEvent;
import com.fsoinstaller.internet.DownloadListener;
import com.fsoinstaller.internet.Downloader;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.MiscUtils;


public class ProgressPanel extends JPanel implements DownloadListener
{
	private static final Logger logger = Logger.getLogger(ProgressPanel.class);
	
	private final Downloader downloader;
	private final JProgressBar progressBar;
	private final JButton cancelButton;
	private final StoplightPanel stoplightPanel;
	
	public ProgressPanel(Downloader downloader)
	{
		super();
		
		if (!EventQueue.isDispatchThread())
			throw new IllegalStateException("Must be run on the event dispatch thread!");
		
		this.downloader = downloader;
		if (downloader != null)
		{
			// this will ensure the <tt>this</tt> doesn't escape the constructor
			EventQueue.invokeLater(new Runnable()
			{
				@Override
				public void run()
				{
					ProgressPanel.this.downloader.addDownloadListener(ProgressPanel.this);
				}
			});
		}
		
		this.progressBar = new JProgressBar(0, 100);
		progressBar.setString("Waiting...");
		progressBar.setStringPainted(true);
		progressBar.setIndeterminate(true);
		progressBar.setValue(0);
		
		this.cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				cancelButton.setEnabled(false);
				cancelButton.setText("Cancelling...");
				if (ProgressPanel.this.downloader != null)
					ProgressPanel.this.downloader.cancel();
			}
		});
		
		this.stoplightPanel = new StoplightPanel((int) cancelButton.getPreferredSize().getHeight());
		
		setBorder(BorderFactory.createEmptyBorder(GUIConstants.SMALL_MARGIN, GUIConstants.SMALL_MARGIN, GUIConstants.SMALL_MARGIN, GUIConstants.SMALL_MARGIN));
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		add(progressBar);
		add(Box.createHorizontalStrut(GUIConstants.SMALL_MARGIN));
		add(cancelButton);
		add(Box.createHorizontalStrut(GUIConstants.SMALL_MARGIN));
		add(stoplightPanel);
	}
	
	@Override
	public void downloadAboutToStart(DownloadEvent event)
	{
		progressBar.setString(event.getDownloadName() + ": 0 of " + event.getTotalBytes() + " bytes");
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
	}
	
	@Override
	public void downloadProgressReport(DownloadEvent event)
	{
		progressBar.setString(event.getDownloadName() + ": " + event.getDownloadedBytes() + " of " + event.getTotalBytes() + " bytes");
		progressBar.setIndeterminate(false);
		progressBar.setValue((int) ((double) event.getDownloadedBytes() / event.getTotalBytes() * 100));
	}
	
	@Override
	public void downloadNotNecessary(DownloadEvent event)
	{
		progressBar.setString(event.getDownloadName() + ": Up to date");
		progressBar.setIndeterminate(false);
		progressBar.setValue(100);
		
		cancelButton.setText("Cancel");
		cancelButton.setEnabled(false);
		
		stoplightPanel.setSuccess();
	}
	
	@Override
	public void downloadComplete(DownloadEvent event)
	{
		progressBar.setString(event.getDownloadName() + ": Complete");
		progressBar.setIndeterminate(false);
		progressBar.setValue(100);
		
		cancelButton.setText("Cancel");
		cancelButton.setEnabled(false);
		
		stoplightPanel.setSuccess();
	}
	
	@Override
	public void downloadFailed(DownloadEvent event)
	{
		progressBar.setString(event.getDownloadName() + ": Failed");
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
		
		if (event.getException() instanceof InterruptedException)
			cancelButton.setText("Cancelled");
		else
			cancelButton.setText("Cancel");
		cancelButton.setEnabled(false);
		
		stoplightPanel.setFailure();
	}
	
	private static class StoplightPanel extends JPanel
	{
		private final int height;
		private Color redColor;
		private Color greenColor;
		
		public StoplightPanel(int height)
		{
			this.height = height;
			this.redColor = new Color(63, 0, 0);
			this.greenColor = new Color(0, 63, 0);
		}
		
		public void setSuccess()
		{
			this.greenColor = Color.GREEN;
			getParent().repaint();
		}
		
		public void setFailure()
		{
			this.redColor = Color.RED;
			getParent().repaint();
		}
		
		@Override
		protected void paintComponent(Graphics g)
		{
			int width = (getWidth() - GUIConstants.SMALL_MARGIN) / 2;
			
			g.setColor(redColor);
			g.fillOval(0, 0, width, height);
			
			g.setColor(greenColor);
			g.fillOval(width + GUIConstants.SMALL_MARGIN, 0, width, height);
		}
		
		private Dimension calculateSize()
		{
			return new Dimension(height * 2 + GUIConstants.SMALL_MARGIN, height);
		}
		
		@Override
		public Dimension getMinimumSize()
		{
			return calculateSize();
		}
		
		@Override
		public Dimension getPreferredSize()
		{
			return calculateSize();
		}
		
		@Override
		public Dimension getMaximumSize()
		{
			return calculateSize();
		}
	}
	
	public static void main(String[] args)
	{
		EventQueue.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				logger.debug("Setting look-and-feel...");
				try
				{
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				}
				catch (ClassNotFoundException cnfe)
				{
					logger.error("Error setting look-and-feel!", cnfe);
				}
				catch (InstantiationException ie)
				{
					logger.error("Error setting look-and-feel!", ie);
				}
				catch (IllegalAccessException iae)
				{
					logger.error("Error setting look-and-feel!", iae);
				}
				catch (UnsupportedLookAndFeelException iae)
				{
					logger.error("Error setting look-and-feel!", iae);
				}
				
				URL url;
				try
				{
					url = new URL("http://fsport.hard-light.net/wip/screenshots.zip");
				}
				catch (MalformedURLException murle)
				{
					logger.error("bleh", murle);
					return;
				}
				File dir = new File("C:\\temp\\screenshots.zip");
				Connector connector = new Connector();
				final Downloader downloader = new Downloader(connector, url, dir);
				
				ExecutorService service = Executors.newCachedThreadPool();
				service.execute(new Runnable()
				{
					@Override
					public void run()
					{
						MiscUtils.sleep(3000);
						
						boolean result = downloader.download();
						System.out.println(result);
					}
				});
				service.shutdown();
				
				ProgressPanel panel = new ProgressPanel(downloader);
				
				JFrame frame = new JFrame("Test Download Progress");
				frame.setContentPane(panel);
				frame.pack();
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				
				MiscUtils.centerWindowOnScreen(frame);
				frame.setVisible(true);
			}
		});
	}
}
