package net.imglib2.posField;

import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import io.nii.Nifti_Writer;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import process.RenderTransformed;

public class GeneratePositionField
{

	public static void main( String[] args )
	{
		String outArg = args[ 0 ];
		String intervalArg = args[ 1 ];
		
		int dim = -1;
		if ( args.length >= 2 )
			dim = Integer.parseInt( args[ 2 ]);

		FinalInterval interval = RenderTransformed.parseInterval( intervalArg );
		PositionRandomAccessible< FloatType, ? > pra = new PositionRandomAccessible<>( 
				interval.numDimensions(), new FloatType() );

//		PositionRandomAccessible< ShortType > pra = new PositionRandomAccessible< ShortType >( 
//				interval.numDimensions(), new ShortType() );

		int nd = interval.numDimensions();
//		long[] min = null;
//		long[] max = null;
		long[] min = new long[ nd + 1 ];
		long[] max = new long[ nd + 1 ];
		
		for( int d = 0; d < nd; d++ )
		{
			min[ d ] = interval.min( d );
			max[ d ] = interval.max( d );
		}

		if( dim < 0 )
		{
			min[ nd ] = 0;
			max[ nd ] = nd-1;
		} 
		else
		{
			min[ nd ] = dim;
			max[ nd ] = dim; 
		}
		FinalInterval outputInterval = new FinalInterval( min, max );

		// Permute so that slices are correctly interpreted as such 
		// (instead of channels )
		RandomAccessibleInterval< FloatType > raiout = 
				Views.permute( Views.interval( pra, outputInterval ), 3, 2 );

		System.out.println( raiout );
		System.out.println( Util.printInterval(raiout) );

		ImagePlus ip = ImageJFunctions.wrapFloat( raiout, "out");
		
		if( outArg.endsWith( "nii" ))
		{
			Nifti_Writer writer = new Nifti_Writer();

			File f = new File( outArg );
			System.out.println( "writing to: " );
			System.out.println( f.getParent());
			System.out.println( f.getName());

			writer.save( ip, f.getParent(), f.getName() );
		}
		else
		{
			IJ.save( ip, outArg );
		}
	}

}
