package io.nii;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import loci.formats.FormatException;
import loci.formats.in.NiftiReader;
import loci.plugins.util.ImageProcessorReader;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

public class NiftiIo
{
	public static final String KEY_XRES = "Voxel width";
	public static final String KEY_YRES = "Voxel height";
	public static final String KEY_ZRES = "Slice thickness";
	
	
	public static ImagePlus readNifti( File f ) throws FormatException, IOException
	{
		NiftiReader reader = new NiftiReader();
		reader.setId( f.getAbsolutePath() );
		

		int width = reader.getSizeX();
		int height = reader.getSizeY();
		int depth = reader.getSizeZ();
		int timepts = reader.getSizeT();
		int channels = reader.getSizeC();
		int imgCount = reader.getImageCount();
		System.out.println( "img count" + imgCount );
		System.out.println("timepts " + timepts  );
		System.out.println("channels " + channels  );
		
		if( timepts > 1 && channels == 1 )
		{
			channels = timepts;
		}
		
		double[] res = new double[]{ 1.0, 1.0, 1.0 };
		Hashtable< String, Object > meta = reader.getGlobalMetadata();
//		for( String key : meta.keySet() )
//		{
//			System.out.println( " " + key + " - " + meta.get( key ));
////			System.out.println( key.equals( KEY_XRES ));
//		}

		// Get image resolutions from file
		if( meta.keySet().contains( KEY_XRES ))
		{
//			System.out.println( "resx ");
			res[ 0 ] = (Double)(meta.get( KEY_XRES ));
		}
		
		if( meta.keySet().contains( KEY_YRES ))
		{
//			System.out.println( "resy");
			res[ 1 ] = (Double)(meta.get( KEY_YRES ));
		}
		
		if( meta.keySet().contains( KEY_ZRES ))
		{
//			System.out.println( "resz ");
			res[ 2 ] = (Double)(meta.get( KEY_ZRES ));
		}
		
		//System.out.println( "res: " + res[0]  + " " + res[1] + " " + res[2] );

		ImageProcessorReader ipr = new ImageProcessorReader( reader );
//		ipr.openProcessors( arg0, arg1, arg2, arg3, arg4 )
		ImageStack stack = new ImageStack( width, height );
		for ( int z = 0; z < imgCount; z++ )
		{
//			System.out.println( "z: " + z );
			ImageProcessor[] processors = ipr.openProcessors( z );
//			System.out.println( processors.length );
//			System.out.println( processors[ 0 ] );
			stack.addSlice( processors[ 0 ] );
		}

		reader.close();
		ipr.close();

		ImagePlus ip = new ImagePlus( f.getName(), stack );
		ip.setDimensions( 1, depth, channels );

		ip.getCalibration().pixelWidth  = res[ 0 ];
		ip.getCalibration().pixelHeight = res[ 1 ];
		ip.getCalibration().pixelDepth  = res[ 2 ];

		return ip;
	}
}
