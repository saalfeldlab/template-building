package evaluation;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import net.imglib2.algorithm.morphology.distance.DistanceTransform;

public class RunDistanceTransform
{

	public static void main( String[] args ) throws InterruptedException, ExecutionException, FormatException, IOException, ImgLibException
	{
		int idx = 0;
		String imgF = args[ idx ];
		idx++;

		System.out.println( idx );
		System.out.println( args.length );
		String baseOutputName = imgF;
		if( args.length > idx)
		{
			baseOutputName = args[ idx ];
			System.out.println( "output name: " + baseOutputName );
			idx++;
		}

		ImagePlus ip = read( imgF );
		double[] res = new double[]{ 1.0, 1.0, 1.0 };
		res[ 0 ] = ip.getCalibration().pixelWidth;
		res[ 1 ] = ip.getCalibration().pixelHeight;
		res[ 2 ] = ip.getCalibration().pixelDepth;
		System.out.println( "res: " + Arrays.toString( res ));

		Img<FloatType> img = ImageJFunctions.convertFloat( ip );

		RandomAccessibleInterval< FloatType > sc = invThreshBig( img, 1.0f, Float.MAX_VALUE );

		FloatImagePlus< FloatType > dist = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( img ) );
		FloatImagePlus< FloatType > tmp = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( img ) );
		
		final int nThreads = 4;
		final ExecutorService es = Executors.newFixedThreadPool( nThreads );
		System.out.println( "computing distance");
		DistanceTransform.transform( sc, tmp, dist, DistanceTransform.DISTANCE_TYPE.EUCLIDIAN, es, nThreads, res );
		sqrt( dist );

		ImagePlus ipout = dist.getImagePlus();
		ipout.getCalibration().pixelWidth  = res[ 0 ];
		ipout.getCalibration().pixelHeight = res[ 1 ];
		ipout.getCalibration().pixelDepth  = res[ 2 ];

		IJ.save( ipout, baseOutputName + "_distXfm.tif" );

		System.out.println( "done");
		System.exit( 0 );
	}

	public static ImagePlus read( final String arg ) throws FormatException, IOException
	{
		if( arg.endsWith( "nii" ) || arg.endsWith( "nii.gz" ))
		{
			return NiftiIo.readNifti( new File( arg ) );
		}
		else
		{
			return IJ.openImage( arg );
		}
	}

	public static void sqrt( final RandomAccessibleInterval<FloatType> img )
	{
		Views.flatIterable( img ).cursor().forEachRemaining( 
				x -> x.setReal( sqrtIfPos( x.get() )));
	}

	public static double sqrtIfPos( final double x )
	{
		if( x > 0 )
			return Math.sqrt( x );
		else
			return x;
	}

	public static RandomAccessibleInterval< FloatType > invThreshBig( final RandomAccessibleInterval<FloatType> img, final float threshold, final float big )
	{
		return invThreshBig( img, threshold, big, 0f );
	}

	public static RandomAccessibleInterval< FloatType > invThreshBig( final RandomAccessibleInterval<FloatType> img,
			final float threshold, final float big, final float small )
	{
		Converter< FloatType, FloatType > conv = new Converter<FloatType, FloatType>()
		{
			@Override
			public void convert( FloatType input, FloatType output )
			{
				if( input.getRealFloat() >= threshold )
					output.setReal( small );
				else
					output.setReal( big );
			}
		};
		return Converters.convert(  img, conv, new FloatType() );
	}
	
	public static RandomAccessibleInterval< UnsignedByteType > inverseThreshold( final RandomAccessibleInterval<FloatType> img, final float threshold )
	{
		Converter< FloatType, UnsignedByteType > conv = new Converter<FloatType, UnsignedByteType>()
		{
			@Override
			public void convert( FloatType input, UnsignedByteType output )
			{
				if( input.getRealFloat() >= threshold )
					output.setZero();
				else
					output.setInteger( 255 );
			}
		};
		return Converters.convert(  img, conv, new UnsignedByteType() );
	}
	
}
