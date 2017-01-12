package process;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import io.AffineImglib2IO;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import transforms.AffineHelper;

public class FlipAboutYRotate45
{
	public static double PIO4 = Math.PI / 4;
	public static double MPIO2 = -Math.PI / 2;

	public static void main( String[] args ) throws IOException
	{
		String destdir = args[ 0 ];

		String[] subjects = new String[ args.length - 1 ];
		System.arraycopy( args, 1, subjects, 0, subjects.length );

		for ( String f : subjects )
		{
			String fout = f.replaceAll( ".tif", "-flip.tif" );
			File fin = new File( f );

			String affineFOut = destdir + File.separator + fin.getName() + "-flipXfm.txt";
			System.out.println( f );
			System.out.println( fout );
			System.out.println( affineFOut );

			Img< FloatType > img = ImageJFunctions.wrap( IJ.openImage( f ) );

			AffineTransform3D flipX = new AffineTransform3D();
			flipX.set( -1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0 );

			AffineTransform3D rotzm90 = new AffineTransform3D();
			rotzm90.rotate( 2, MPIO2 );

			double[] center = AffineHelper.center( img );
			AffineTransform3D flipXcenter = AffineHelper.rotateOffset( flipX, center,
					center );

			AffineTransform3D rotzm90center = AffineHelper.rotateOffset( rotzm90, center,
					center );
			AffineTransform3D tmpXfm = flipXcenter.concatenate( rotzm90center );
			System.out.println( tmpXfm.toString() );

			FinalInterval intervalXfm = AffineHelper.transformInterval( img, tmpXfm );
			System.out.println( "\ntmp interval: " );
			System.out.println( Util.printInterval( intervalXfm ) );
			System.out.println( " " );

			AffineTransform3D translate = new AffineTransform3D();
			translate.set( intervalXfm.min( 0 ), 0, 3 );
			translate.set( intervalXfm.min( 1 ), 1, 3 );
			translate.set( intervalXfm.min( 2 ), 2, 3 );

			System.out.println( "tmp xfm:\n" + tmpXfm + "\n" );
			System.out.println( "translate xfm:\n" + translate + "\n" );
			AffineTransform3D totalXfm = tmpXfm.copy()
					.preConcatenate( translate.inverse() );
			System.out.println( "final xfm:\n" + totalXfm + "\n" );

			FinalInterval intervalOut = AffineHelper.transformInterval( img, totalXfm );
			System.out.println( "\nfinal interval: " );
			System.out.println( Util.printInterval( intervalOut ) );
			System.out.println( " " );

			IntervalView< FloatType > result = Views.interval(
					Views.raster( RealViews.affine(
							Views.interpolate( Views.extendZero( img ),
									new NLinearInterpolatorFactory< FloatType >() ),
							totalXfm ) ),
					intervalOut );

			System.out.println( "writing xfm" );
			AffineImglib2IO.writeXfm( new File( affineFOut ), totalXfm );

			System.out.println( totalXfm );

			IJ.save( ImageJFunctions.wrap( result, "img flip" ), fout );

			System.out.println( " " );
		}

	}

	public static void centerFlipXYInPlace( double[] center )
	{
		double tmp = center[ 0 ];
		center[ 0 ] = center[ 1 ];
		center[ 1 ] = tmp;
	}

	public static double[] centerFlipXY( double[] center )
	{
		double[] out = new double[ center.length ];
		for ( int d = 0; d < center.length; d++ )
		{
			if ( d == 0 )
				out[ d ] = center[ 1 ];
			else if ( d == 1 )
				out[ d ] = center[ 0 ];
			else
				out[ d ] = center[ d ];
		}
		return out;
	}

	public static FinalInterval intervalFlipXY3d( Interval interval )
	{
		return new FinalInterval( 
				interval.dimension( 1 ), 
				interval.dimension( 0 ),
				interval.dimension( 2 ) );
	}

}
