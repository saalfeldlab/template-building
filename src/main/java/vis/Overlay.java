package vis;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.RGBStackMerge;
import io.nii.NiftiIo;
import loci.formats.FormatException;

public class Overlay
{

	static final String PROP_FORMAT ="channels=1 slices=%d frames=1 unit=pixel pixel_width=1.0000 pixel_height=1.0000 voxel_depth=1.0000";
	static final String MERGE_FORMAT ="c2=%s c6=%s create keep ignore";
	public static void main( String[] args ) throws FormatException, IOException
	{
		String outpath = args[ 0 ];

		ImagePlus a = null;
		if( args[ 1 ].endsWith( "nii" ))
			a = NiftiIo.readNifti( new File( args[1] ) );
		else
			a = IJ.openImage( args[ 1 ]);

		IJ.run( a, "Properties...", String.format( PROP_FORMAT, a.getStackSize() ));
		a.setSlice( a.getStackSize() / 2 );
		IJ.run( a, "Enhance Contrast", "saturated=0.35");

		ImagePlus b = null;
		if( args[ 2 ].endsWith( "nii" ))
			b = NiftiIo.readNifti( new File( args[ 2 ] ) );
		else
			b = IJ.openImage( args[ 2 ]);

		IJ.run( b, "Properties...", String.format( PROP_FORMAT, b.getStackSize() ));
		b.setSlice( a.getStackSize() / 2 );
		IJ.run( b, "Enhance Contrast", "saturated=0.35");
		
		// this line makes 'b' green and 'a' magenta
		ImagePlus mergedIp = new ImagePlus( "result", RGBStackMerge.mergeStacks( a.getImageStack(), b.getImageStack(), a.getImageStack(), false ));
		System.out.println( "mergedIp: " + mergedIp );
	
		for( int i = 3; i < args.length; i++ )
		{
			int z = Integer.parseInt( args[ i ]);
			System.out.println( "z : " + z );

			mergedIp.setZ( z );
			ImagePlus ipout = new ImagePlus( "z:"+z, mergedIp.getProcessor() );

			IJ.save( ipout, outpath + z + ".png" );
		}

	}
	
}
