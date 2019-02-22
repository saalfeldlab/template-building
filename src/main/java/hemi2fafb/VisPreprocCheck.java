package hemi2fafb;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import util.RenderUtil;

public class VisPreprocCheck
{

	
	/*
	 * Downsamples and offsets such that the result is in the 
	 * 
	 */
	public static void main( String[] args )
	{
		String iPath = "/nrs/saalfeld/john/fafbsynapse_to_template/syn_s5.tif";
		double[] downsampleFactors = new double[]{ 32, 32, 3 };


		
		Img<UnsignedByteType> img = ImageJFunctions.wrapByte( IJ.openImage( iPath ));
		RealRandomAccessible<UnsignedByteType> aphys = RealViews.affine(
				Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory<>() ),
				getXfm( downsampleFactors ) );
		
	}
	
	public static void vis()
	{
		String aPath = "/nrs/saalfeld/john/fafbsynapse_to_template/syn_s8.tif";
		String bPath = "/nrs/saalfeld/john/fafbsynapse_to_template/syn_s9.tif";

		//String outPath = "/nrs/saalfeld/john/fafbsynapse_to_template/syn_s8_downoffset-to-s7.tif";
		//String fPath = "/nrs/saalfeld/john/fafbsynapse_to_template/syn_s5.tif";

		double[] downsampleFactorsA = new double[]{ 256, 256, 26 };
		double[] downsampleFactorsB = new double[]{ 512, 512, 51 };
		//double[] downsampleFactors = new double[]{ 2, 2, 2 };
		//double[] downsampleFactors = new double[]{ 32, 32, 3 };


		Img<UnsignedByteType> a = ImageJFunctions.wrapByte( IJ.openImage( aPath ));
		Img<UnsignedByteType> b = ImageJFunctions.wrapByte( IJ.openImage( bPath ));


		RealRandomAccessible<UnsignedByteType> aphys = RealViews.affine(
				Views.interpolate( Views.extendZero( a ), new NLinearInterpolatorFactory<>() ),
				getXfm( downsampleFactorsA ) );

		RealRandomAccessible<UnsignedByteType> bphys = RealViews.affine(
				Views.interpolate( Views.extendZero( b ), new NLinearInterpolatorFactory<>() ),
				getXfm( downsampleFactorsB ) );
		
		
		FinalInterval itvl = RenderUtil.transformInterval( getXfm( downsampleFactorsA ), a );
		
		BdvStackSource< UnsignedByteType > bdv = BdvFunctions.show( aphys, itvl, "s7" );
		BdvOptions options = BdvOptions.options().addTo( bdv );
		BdvFunctions.show( bphys, itvl, "s8", options );
		
	}
	
	public static void face()
	{

//		//double[] downsampleFactorsMore = new double[]{ 32, 32, 3 };
//		
//		AffineTransform3D offset = getOffset( downsampleFactors );
//		System.out.println( offset );
//		
//		Img<UnsignedByteType> img = ImageJFunctions.wrapByte( IJ.openImage( inPath ));
//		System.out.println( "img: " + img );
//
//		AffineRandomAccessible< UnsignedByteType, AffineGet > imgoffset = RealViews.affine( Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory<>() ),
//				offset );
//		
//		ImagePlus ipxfm = ImageJFunctions.wrap( Views.interval( Views.raster( imgoffset ), img ), "xfmed" );
//		IJ.save( ipxfm, outPath );
//
//		System.out.println( "DONE");
		
	}
	

	public static AffineTransform3D getXfm( final double[] f )
	{
		AffineTransform3D xfm = new AffineTransform3D();
		for( int i = 0; i < 3; i++ )
		{
			xfm.set( f[i], i, i );
			xfm.set( ( f[i] / 2 ) , i, 3 );
		}
		return xfm;
	}

	public static AffineTransform3D getOffset( final double[] downFactors )
	{
		AffineTransform3D xfm = new AffineTransform3D();
//		for( int i = 0; i < 3; i++ )
//			xfm.set( -( downFactors[i] / 2 ) - 0.5 , i, 3 );

		for( int i = 0; i < 3; i++ )
			xfm.set( ( downFactors[i] / 2 ) - 0.5, i, 3 );
	
		return xfm;
	}

}
