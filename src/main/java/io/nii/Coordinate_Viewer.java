package io.nii;

import ij.*;
import ij.plugin.frame.*;
import ij.gui.*;
import java.awt.*;
import java.awt.event.*;

public class Coordinate_Viewer extends PlugInFrame implements MouseMotionListener, ActionListener { 

	private Label imageName; 
	
	private ImagePlus currentImage; 
	private ImageCanvas currentCanvas; 
	private CoordinateMapper[] mapper;
	private Label[] coors; 
	
	public Coordinate_Viewer() { 
		super("Coordinate Viewer");
		setUpWindow();
		setVisible(true); // show();
	}

	private void setUpWindow() { 
		
		removeAll();
		setLayout(new GridBagLayout()); 
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);

		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0; 
		gbc.gridy = 0; 
		gbc.weightx = 0; 
		gbc.weighty = 0; 

		gbc.anchor = GridBagConstraints.NORTHWEST; 
		add(new Label("Image: "), gbc); 
		gbc.gridx = 1; 
		gbc.weightx = 1; 
		currentImage = WindowManager.getCurrentImage(); 
		imageName = new Label( currentImage!=null ? currentImage.getTitle() : "" ); 
		add(imageName, gbc); 
	
		gbc.gridy = 1; 
		gbc.anchor = GridBagConstraints.NORTHEAST;
		
		Button newImage = new Button("Set Image"); 
		gbc.fill = GridBagConstraints.VERTICAL;
		add(newImage, gbc); 
		newImage.addActionListener(this); 
		
		gbc.gridy = 2;
		gbc.gridx = 0; 
		gbc.weighty = 0;
		gbc.gridwidth = 2; 
		gbc.fill = GridBagConstraints.BOTH;
	
		pack();
		if (currentImage == null) { 
			gbc.weighty = 1;
			add(new Panel(), gbc);
			return; 
		}

		Object prop = currentImage.getProperty("coors");
		if (prop == null) return;
		if (!(prop instanceof CoordinateMapper[] )) {
			gbc.weighty = 1;
			add(new Panel(), gbc);
			return; 
		}

		add( new Label(" "), gbc); 
		mapper = ( CoordinateMapper[] ) prop;
		coors = new Label [ mapper.length ];

		gbc.gridy = 3;
		
		for (int i=0; i<mapper.length; i++) { 
			gbc.anchor = GridBagConstraints.WEST; 
			gbc.gridwidth = 1;
			add( new Label( mapper[i].getName() + ":" ), gbc );
			if (mapper[i].getCoorType() != CoordinateMapper.UNKNOWN ) { 
				gbc.gridy++; 
				gbc.gridx = 0; 
				gbc.anchor = GridBagConstraints.EAST; 
				add( new Label(" x increases from ", Label.RIGHT), gbc ); 
				gbc.gridx = 1;
				gbc.anchor = GridBagConstraints.WEST; 
				add( new Label(mapper[i].getXDescription()), gbc ); 
				gbc.gridy++; 
				gbc.gridx = 0; 
				gbc.anchor = GridBagConstraints.EAST; 
				add( new Label(" y increases from ", Label.RIGHT), gbc ); 
				gbc.gridx = 1;
				gbc.anchor = GridBagConstraints.WEST; 
				add( new Label(mapper[i].getYDescription()), gbc ); 
				gbc.gridy++; 
				gbc.gridx = 0; 
				gbc.anchor = GridBagConstraints.EAST; 
				add( new Label(" z increases from ", Label.RIGHT), gbc ); 
				gbc.gridx = 1;
				gbc.anchor = GridBagConstraints.WEST; 
				add( new Label(mapper[i].getZDescription()), gbc ); 
			}

			gbc.gridx = 0; 
			gbc.gridy++;
			gbc.anchor = GridBagConstraints.NORTH;
			gbc.gridwidth = 2;
			coors[i] = new Label("(0.00,0.00,0.00)", Label.CENTER);
			add( coors[i], gbc );
			gbc.gridy++;
		}
	
		gbc.weighty = 1;
		add(new Panel(), gbc);
		
		currentCanvas = currentImage.getWindow().getCanvas();
		currentCanvas.addMouseMotionListener(this);
		
		pack();
	}

	public void actionPerformed( ActionEvent e ) { 
		if (currentCanvas != null) currentCanvas.removeMouseMotionListener(this);
		/* double [][] mat = new double[3][4];
		mat[0][0] = 1.0; mat[1][1] = 1.0; mat[2][2] = 1.0;
		NiftiSCoors [] ns = new NiftiSCoors[1];
		ns[0] = new NiftiSCoors( mat );
		WindowManager.getCurrentImage().setProperty("coors", ns); */ 

		setUpWindow(); 
	}

	public void mouseDragged(MouseEvent e) { } 
	public void mouseMoved(MouseEvent e) { 
		
		int x = currentCanvas.offScreenX( e.getX() );
		int y = currentCanvas.offScreenY( e.getY() );
		//int z = currentImage.getCurrentSlice() - 1;
		int z = currentImage.getSlice() - 1;


		// The below code was needed for compatibility with Image5D classes
		// Now it works with Hyperstacks instead.
		//
/*		boolean isImage5D = false;
		try { 
			Class c = Class.forName( "i5d.Image5D" ); 
			isImage5D = c.isInstance( currentImage );
		} catch (ClassNotFoundException ee) { } 

		if (!isImage5D) { 
			z = z / currentImage.getNChannels(); 
			z = z % currentImage.getNSlices(); 
		}
*/
		for (int i=0; i<mapper.length; i++) { 
			String loc = "(" + IJ.d2s( mapper[i].getX( x, y, z ) ) + "," 
				+  IJ.d2s( mapper[i].getY( x, y, z ) ) + ","
				+  IJ.d2s( mapper[i].getZ( x, y, z ) ) + ")"; 
			coors[i].setText( loc ); 
			coors[i].repaint();
		}
	}

}

		
