package process;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.janelia.utility.parse.ParseUtils;

import ij.IJ;
import io.AffineImglib2IO;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.util.Util;
import process.DownsampleGaussian;
import transforms.AffineHelper;

public class DownsampleCanonical
{
	public static double PIO4 = Math.PI / 4;
	public static double MPIO2 = -Math.PI / 2;
	public static double MPIO4 = -Math.PI / 4;

	public static void main( String[] args ) throws ImgLibException, IOException
	{
		String path = args[ 0 ];
		String destdir = args[ 1 ];

		double[] factor = ParseUtils.parseDoubleArray( args[ 2 ] );
		long[] min = ParseUtils.parseLongArray( args[ 3 ] );
		long[] max = ParseUtils.parseLongArray( args[ 4 ] );

		FinalInterval intervalOut = new FinalInterval( min, max );
		System.out.println( Util.printInterval( intervalOut ) );

		boolean doFlip = false;
		if( args.length >= 6 ){
			if( args[ 5 ].equals( "1" ) || args[ 5 ].equals( "true" ))
				doFlip = true;
		}

		File f = new File( path );
		String name = f.getName();
		
		String fout = "";
		String affineFOut = "";
		if( doFlip )
		{
			fout = name.replaceAll( ".tif", "_downFlip.tif" );
			affineFOut = destdir + File.separator + 
					name.replaceAll( ".tif", "_downFlipXfm.txt" );
		}
		else
		{
			fout = name.replaceAll( ".tif", "_down.tif" );
			affineFOut = destdir + File.separator + 
					name.replaceAll( ".tif", "_down.txt" );
		}

		System.out.println( f );
		System.out.println( name );
		System.out.println( fout );
		System.out.println( affineFOut );

//		Img< FloatType > img = ImageJFunctions.wrap( IJ.openImage( f ) );
		Img< ShortType > img = ImageJFunctions.wrap( IJ.openImage( path ) );

		double[] sourceSigmas = new double[]{ 0.5, 0.5, 0.5 };
		double[] targetSigmas = new double[]{ 0.5, 0.5, 0.5 };

		int nd = 3;
		AffineTransform3D up3d = new AffineTransform3D();
		for( int i = 0; i < nd; i++ )
		{
			up3d.set( factor[ i ], i, i );
			up3d.set( factor[ i ] / 2, i, nd );
		}

		AffineTransform3D flipX = new AffineTransform3D();
		flipX.set( -1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0 );

		AffineTransform3D rotzm90 = new AffineTransform3D();
		rotzm90.rotate( 2, MPIO2 );

		double[] center = AffineHelper.center( img );
		
		AffineTransform3D firstXfm = new AffineTransform3D();
		if( doFlip )
		{
			System.out.println("Flipping transform");
			AffineTransform3D flipXcenter = AffineHelper.rotateOffset( flipX, center, center );
			AffineTransform3D rotzm90center = AffineHelper.rotateOffset( rotzm90, center, center );
			firstXfm = flipXcenter.concatenate( rotzm90center );
		}

		AffineTransform3D rotz45 = new AffineTransform3D();
		rotz45.rotate( 2, PIO4 );
		AffineTransform3D toNormal = AffineHelper.rotateOffset( rotz45, center, center );	
		firstXfm = firstXfm.concatenate( toNormal );
		AffineTransform3D intermXfm = up3d.copy().preConcatenate( firstXfm );
		
		// make sure the old interval's center is in the new interval's center
		double[] inCenter = AffineHelper.center( img );
		double[] outCenterXfm = new double[ 3 ];
		double[] outCenter = AffineHelper.center( intervalOut );
		intermXfm.apply( outCenter, outCenterXfm );

		AffineTransform3D translate = new AffineTransform3D();
		translate.set( inCenter[ 0 ] - outCenterXfm[ 0 ], 0, 3 );
		translate.set( inCenter[ 1 ] - outCenterXfm[ 1 ], 1, 3 );
		translate.set( inCenter[ 2 ] - outCenterXfm[ 2 ], 2, 3 );
		AffineTransform3D totalXfmAndDownsample = intermXfm.preConcatenate( translate );
		
//		double[] asdf = new double[ 3 ];
//		totalXfmAndDownsample.apply( outCenter, asdf );
//		System.out.println( " new center: " +  Arrays.toString( asdf ));
//		System.out.println( " should be : " +  Arrays.toString( inCenter ));

		System.out.println( "total xfm\n" + totalXfmAndDownsample );

		NLinearInterpolatorFactory< ShortType > interpFactory = new NLinearInterpolatorFactory<ShortType>();
		Img< ShortType > imgout = DownsampleGaussian.resampleGaussian( 
				img, interpFactory, factor, 
				sourceSigmas, targetSigmas, totalXfmAndDownsample, intervalOut, 8 );

		System.out.println( "writing xfm" );
		AffineImglib2IO.writeXfm( new File( affineFOut ), totalXfmAndDownsample );

		System.out.println( "writing volume" );
		IJ.save( ImageJFunctions.wrap( imgout, "imgout"), ( destdir + File.separator + fout ));

	}
	
	public static AffineTransform3D genFlipXfm( Interval img )
	{
		AffineTransform3D flipX = new AffineTransform3D();
		flipX.set( -1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0 );

		AffineTransform3D rotzm90 = new AffineTransform3D();
		rotzm90.rotate( 2, MPIO2 );

		double[] center = AffineHelper.center( img );
		AffineTransform3D flipXcenter = AffineHelper.rotateOffset( flipX, center, center );
		AffineTransform3D rotzm90center = AffineHelper.rotateOffset( rotzm90, center, center );
		AffineTransform3D firstXfm = flipXcenter.concatenate( rotzm90center );

		return firstXfm;
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
