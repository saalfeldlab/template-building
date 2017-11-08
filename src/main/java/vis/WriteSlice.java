package vis;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import loci.formats.FormatException;
import process.PerPixelMeanVariance;

public class WriteSlice
{

	public static void main( String[] args ) throws FormatException, IOException
	{
		File fin = new File( args[0] );
		String foutBase = fin.getAbsolutePath().substring( 0, fin.getAbsolutePath().lastIndexOf( '.' ) );
		System.out.println( foutBase );

		double min = Double.parseDouble( args[1] );
		double max = Double.parseDouble( args[2] );

		ImagePlus ip = PerPixelMeanVariance.read( fin );

		String suffix = "";
		if( ip.getNChannels() > 1 )
		{
			int c = ip.getNChannels() / 2 ;
			suffix += String.format("-c%d", c);
			ip.setC( c );
		}
		if( ip.getNSlices() > 1 )
		{
			int z = ip.getNSlices() / 2;
			suffix += String.format("-z%d", z );
			ip.setSlice( z );
		}
		if( ip.getNFrames() > 1 )
		{
			int t = ip.getNFrames() / 2;
			suffix += String.format("-t%d", t );
			ip.setT( t );
		}

		ImagePlus ipOut = new ImagePlus( "", ip.getProcessor() );

//		IJ.run( ipOut, "Enhance Contrast", "saturated=0.35");
		System.out.println( "min: " + min );
		System.out.println( "max: " + max );
		IJ.setMinAndMax( ipOut, min, max );

		IJ.save( ipOut, foutBase + suffix +".png" );
	}

}
