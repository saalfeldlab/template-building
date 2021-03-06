package io;

import java.io.File;
import java.io.IOException;

import org.janelia.utility.parse.ParseUtils;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;

public class SetResolution {

	public static void main(String[] args) throws FormatException, IOException
	{
		String imF = args[ 0 ];
		String out = args[ 1 ];
		double[] res = ParseUtils.parseDoubleArray( args[ 2 ] );
		
		String unit = null;
		if( args.length >= 4 )
			unit = args[ 3 ];

		IOHelper io = new IOHelper();
		ImagePlus ip = io.readIp( new File( imF ));

		ip.getCalibration().pixelWidth  = res[ 0 ];
		ip.getCalibration().pixelHeight = res[ 1 ];
		ip.getCalibration().pixelDepth  = res[ 2 ];
		
		if( unit != null )
			ip.getCalibration().setUnit( unit );

		IOHelper.write( ip, out );
	}

}
