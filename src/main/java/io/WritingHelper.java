package io;

import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import io.nii.Nifti_Writer;
import sc.fiji.io.Nrrd_Writer;

public class WritingHelper {

	public static void write( ImagePlus ip, String outputFilePath )
	{
		if( outputFilePath.endsWith( "nii" ))
		{
			File f = new File( outputFilePath );

			boolean is_displacement = false;
			if( ip.getDimensions()[ 2 ] == 3 )
				is_displacement = true;

			Nifti_Writer writer = new Nifti_Writer( is_displacement );
			writer.save( ip, f.getParent(), f.getName() );
		}
		else if( outputFilePath.endsWith( "nrrd" ))
		{
			File f = new File( outputFilePath );
			Nrrd_Writer writer = new Nrrd_Writer();
			writer.save( ip, f.getParent(), f.getName() );
		}
		else
		{
			IJ.save( ip, outputFilePath );
		}
			
	}
}
