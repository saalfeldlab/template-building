package hemi2fafb;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import process.DownsampleGaussian;

public class PreProcForFafbReg
{

	
	/*
	 * Downsamples and offsets such that the result is in the 
	 * 
	 */
	@SuppressWarnings( "unchecked" )
	public static void main( String[] args )
	{
//		String inPath = "/nrs/saalfeld/john/fafbsynapse_to_template/syn_s8.tif";
//		String outPath = "/nrs/saalfeld/john/fafbsynapse_to_template/syn_s8_downoffset-to-s7.tif";
		//String fPath = "/nrs/saalfeld/john/fafbsynapse_to_template/syn_s5.tif";

		String inPath = "/nrs/saalfeld/john/fafbsynapse_to_template/syn_s5.tif";
		String outPath = "/nrs/saalfeld/john/fafbsynapse_to_template/syn_s5_to1024iso.tif";

		//double[] downsampleFactors = new double[]{ 256, 256, 26 };
		double[] downsampleFactors = new double[]{ 2, 2, 2 };
		//double[] downsampleFactors = new double[]{ 32, 32, 3 };

		//double[] downsampleFactorsMore = new double[]{ 32, 32, 3 };
		// double[] downsampleFactorsMore = new double[]{ 1, 1, 1 };
		double[] downsampleFactorsMore = new double[]{ 8.0, 8.0, 25.6 };
		
		AffineTransform3D offset = getOffset( downsampleFactors );
		System.out.println( offset );
		
		Img<FloatType> img = ImageJFunctions.wrapFloat( IJ.openImage( inPath ));
		System.out.println( "img: " + img );

		//AffineRandomAccessible< FloatType, AffineGet > imgoffset = RealViews.affine( Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory<>() ),
				//offset );
		
		double[] sigmas = new double[]{ 0.5, 0.5, 0.5 };
		ImagePlusImgFactory< FloatType > outputFactory = new ImagePlusImgFactory<>( new FloatType() );

		ImagePlusImg< FloatType, ? > out = (ImagePlusImg<FloatType,?>)DownsampleGaussian.run( img, outputFactory, "LINEAR", downsampleFactorsMore, sigmas, sigmas, 8, 0.01 );
		
		
//		ImagePlus ipxfm = ImageJFunctions.wrap( Views.interval( Views.raster( imgoffset ), img ), "xfmed" );
		IJ.save( out.getImagePlus(), outPath );

	}
	
	public static void old()
	{
		
//		Img<UnsignedByteType> img = ImageJFunctions.wrapByte( IJ.openImage( inPath ));
//		System.out.println( "img: " + img );
//
//		AffineRandomAccessible< UnsignedByteType, AffineGet > imgoffset = RealViews.affine( Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory<>() ),
//				offset );
//		
//		ImagePlus ipxfm = ImageJFunctions.wrap( Views.interval( Views.raster( imgoffset ), img ), "xfmed" );
//		IJ.save( ipxfm, outPath );
	}
	
	public static AffineTransform3D getOffset( final double[] downFactors )
	{
		AffineTransform3D xfm = new AffineTransform3D();
//		for( int i = 0; i < 3; i++ )
//			xfm.set( -( downFactors[i] / 2 ) - 0.5 , i, 3 );

		for( int i = 0; i < 3; i++ )
			xfm.set( -( downFactors[i] / 2 ), i, 3 );
	
		return xfm;
	}

}
