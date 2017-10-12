package transforms;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import loci.formats.FormatException;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;

public class RemoveAffineFromDisplacement
{

	public static void main( String[] args ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		int argIdx = 0;


		String outPath = args[ argIdx++ ];
		String defPath = args[ argIdx++ ];
		String affinePath = args[ argIdx++ ];

		AffineTransform3D affine;
		try
		{
			affine = ANTSLoadAffine.loadAffine( affinePath );
		} catch ( IOException e1 )
		{
			e1.printStackTrace();
			System.err.println( "\nCould not load affine from: " + affinePath );
			return;
		}

		ImagePlus imp = null;
		Img< FloatType > displacement = null;
		if( defPath.endsWith( "nii" ))
		{
			try
			{
				imp = NiftiIo.readNifti( new File( defPath ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			imp = IJ.openImage( defPath );
		}
		displacement = ImageJFunctions.convertFloat( imp );

		if ( displacement == null )
		{
			System.err.println( "Failed to load displacement field" );
			return;
		}

		System.out.println(
				"DISPLACEMENT INTERVAL: " + Util.printInterval( displacement ) );


		System.out.println( "removing affine part from warp" );
		AffineFromDisplacement.removeAffineComponent( displacement, affine, true );
		System.out.println( "saving warp" );


//				IJ.save( ImageJFunctions.wrap( displacement, "warp" ),
//						outPath + "_warp.tif" );

		Nifti_Writer.writeDisplacementField3d( imp, new File( outPath ) );
//			}
		
	}

//	/**
//	 * Removes the affine part from a displacement field 
//	 * @param displacementField the displacement field 
//	 * @param affine the affine part 
//	 * @param filter if true, ignores vectors in the field equal to (0,0,0)
//	 */
//	public static <T extends RealType< T >> void removeAffineComponent(
//			RandomAccessibleInterval< T > displacementField, AffineTransform3D affine,
//			boolean filter )
//	{
//
//		CompositeIntervalView< T, ? extends GenericComposite< T > > vectorField =
//				Views.collapse( displacementField );
//
//		Cursor< ? extends GenericComposite< T > > c =
//				Views.flatIterable( vectorField ).cursor();
//
//		RealPoint affineResult = new RealPoint( 3 );
//		RealPoint y = new RealPoint( 3 );
//
//		while ( c.hasNext() )
//		{
//			GenericComposite< T > vector = c.next();
//
//			if ( filter && 
//					vector.get( 0 ).getRealDouble() == 0 && 
//					vector.get( 1 ).getRealDouble() == 0 && 
//					vector.get( 2 ).getRealDouble() == 0 )
//			{
//				continue;
//			}
//
//			for ( int i = 0; i < 3; i++ )
//				y.setPosition( c.getDoublePosition( i ) + vector.get( i ).getRealDouble(), i );
//
//			affine.applyInverse( affineResult, y  );
//			for ( int i = 0; i < 3; i++ )
//				vector.get( i ).setReal(
//						 affineResult.getDoublePosition( i ) - c.getDoublePosition( i ) );
//
//		}
//	}


}

