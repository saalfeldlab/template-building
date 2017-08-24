package evaluation;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.janelia.utility.parse.ParseUtils;

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

		double[] res = new double[]{ 1.0, 1.0, 1.0 };
		if( args.length > idx )
		{
			res = ParseUtils.parseDoubleArray( args[ idx ] );
			System.out.println( "res: " + Arrays.toString( res ));
			idx++;
		}

		System.out.println( idx );
		System.out.println( args.length );
		String baseOutputName = imgF;
		if( args.length > idx)
		{
			baseOutputName = args[ idx ];
			System.out.println( "output name: " + baseOutputName );
			idx++;
		}

		Img<FloatType> img = ImageJFunctions.convertFloat( read( imgF ));

		RandomAccessibleInterval< FloatType > sc = invThreshBig( img, 1.0f, Float.MAX_VALUE );

		FloatImagePlus< FloatType > dist = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( img ) );
		FloatImagePlus< FloatType > tmp = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( img ) );
		
		final int nThreads = 4;
		final ExecutorService es = Executors.newFixedThreadPool( nThreads );
		System.out.println( "computing distance");
		DistanceTransform.transform( sc, tmp, dist, DistanceTransform.DISTANCE_TYPE.EUCLIDIAN, es, nThreads, res );
		sqrt( dist );

//		Bdv bdv = BdvFunctions.show( scDist, "dist" );

		// Multiply the distance transform with the ground truth and write the result
		IJ.save( dist.getImagePlus(), baseOutputName + "_distXfm.tif" );

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
				x -> x.setReal( Math.sqrt(x.get())) );
	}

	public static RandomAccessibleInterval< FloatType > invThreshBig( final RandomAccessibleInterval<FloatType> img, final float threshold, final float big )
	{
		Converter< FloatType, FloatType > conv = new Converter<FloatType, FloatType>()
		{
			@Override
			public void convert( FloatType input, FloatType output )
			{
				if( input.getRealFloat() >= threshold )
					output.setZero();
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
