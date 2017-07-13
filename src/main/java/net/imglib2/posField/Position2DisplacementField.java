package net.imglib2.posField;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import process.RenderTransformed;

public class Position2DisplacementField
{

	public static void main( String[] args ) throws ImgLibException
	{
		String outArg = args[ 0 ];

		RandomAccessibleInterval<FloatType> posField = null; 
		if ( args.length == 2 )
		{
			posField = read( args[ 1 ] );
		}
		else
		{
			ArrayList<RandomAccessibleInterval<FloatType>> list = 
					new ArrayList<RandomAccessibleInterval<FloatType>>( args.length - 1 );
			for( int i = 1; i < args.length; i++ )
			{
				list.add( read( args[ i ]));
			}
			posField = Views.stack( list );
		}

		FloatImagePlus< FloatType > displacement = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( posField ) );
		position2Displacement( posField, displacement );
		IJ.save( displacement.getImagePlus(), outArg + ".tif" );
	}

	/**
	 * Converts a position field to a displacement field.
	 * 
	 * @param posRai the position field
	 * @param disRai the displacement field
	 */
	public static <T extends RealType< T >> void position2Displacement(
			RandomAccessibleInterval< T > posRai, RandomAccessibleInterval< T > disRai )
	{
		int nd = posRai.numDimensions();

		RandomAccess< T > pra = posRai.randomAccess();
		Cursor< T > c = Views.flatIterable( disRai ).cursor();
		while ( c.hasNext() )
		{
			c.fwd();
			pra.setPosition( c );

			double dst = pra.get().getRealDouble();
			if( dst == 0.0 )
				continue;

			double src = c.getDoublePosition( c.getIntPosition( nd - 1 ) );
			c.get().setReal( dst - src );
		}
	}
	
	
	public static Img<FloatType> read( String filePath )
	{
		if( filePath.endsWith( "nii" ))
		{
			try
			{
				return ImageJFunctions.convertFloat( 
						NiftiIo.readNifti( new File( filePath ) ) );
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
			return ImageJFunctions.convertFloat( IJ.openImage( filePath ));
		}	
		return null;
	}
}
