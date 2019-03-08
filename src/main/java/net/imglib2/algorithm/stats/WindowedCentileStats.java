package net.imglib2.algorithm.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.RealComposite;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import util.RenderUtil;

/* *
 * Computes centiles over RandomAccessibleIntervals 
 */
@Command( version = "0.1.1-SNAPSHOT" )
public class WindowedCentileStats implements Callable<Void>
{

	@Option(names = { "--help", "-h" }, description = "Help")
	protected boolean help = false;

	@Option( names ={ "--centiles", "-c" }, description = "Centile list", required = true, split=",")
	protected double[] centiles;

	@Option(names ={ "--output", "-o" }, description = "Output path", required = true)
	protected String outputPath;

	@Option(names ={ "--width", "-w" }, description = "Width of interval")
	protected int width = 0;

	@Option(names = { "--nThreads", "-q" }, description = "Number of threads")
	protected int nThreads = 1;

	@Option(names={"-i"}, description = "input images")
	protected List< String > imagePathList;

	FloatImagePlus< FloatType > output;

	public WindowedCentileStats()
	{
	}

	public WindowedCentileStats( final double[] centiles )
	{
		this.centiles = centiles;
	}

	public WindowedCentileStats( final double[] centiles, int width )
	{
		this.centiles = centiles;
		this.width = width;
	}

	/**
	 * Computes the percentile across all values in the last dimension.
	 * Ignores window.
	 *  
	 * @param img
	 */
	public <T extends RealType< T >, S extends RealType< S >> void processPerPixel( 
			RandomAccessibleInterval< T > img, RandomAccessibleInterval< S > dest )
	{
		final CompositeView< T, RealComposite< T > >.CompositeRandomAccess cra =  Views.collapseReal( img ).randomAccess();

		int N = (int)img.dimension( img.numDimensions() - 1 );;
		final double[] values = new double[ N ];
		final double[] results = new double[ centiles.length ];

		// TODO multithread
		Cursor< RealComposite< S > > c = Views.flatIterable( Views.collapseReal( dest ) ).cursor();
		while( c.hasNext())
		{
			c.fwd();
			cra.setPosition( c );

			RealComposite< T > composite = cra.get();
			RealComposite< S > outputs = c.get();

			int i = 0;
			for( T t : composite )
				values[ i++ ] = t.getRealDouble();

			centiles( values, results );

			int j = 0;
			for( S s : outputs )
				s.setReal( results[ j++ ] );
		}
	}

	/**
	 * Computes the percentile across all values in the last dimension.
	 * Ignores window.
	 *  
	 * @param img
	 */
	public <T extends RealType< T >, S extends RealType< S >> void processPerPixelParallel( 
			RandomAccessibleInterval< T > img, RandomAccessibleInterval< S > dest )
	{
		System.out.println( String.format( "using %d threads", nThreads ));
		// always split on the second-to-last dimension, since
		// we "pool" data over the last dimension
		final int dim2split = img.numDimensions() - 2;  
		final long[] splitPoints = RenderUtil.splitPoints( nThreads, dim2split, img );

		ExecutorService threadPool = Executors.newFixedThreadPool( nThreads );
		LinkedList< Callable< Boolean > > jobs = new LinkedList< Callable< Boolean > >();
		for ( int i = 0; i < nThreads; i++ )
		{
			final long start = splitPoints[ i ];
			final long end   = splitPoints[ i+1 ];

			jobs.add( new Callable<Boolean>()
			{
				public Boolean call()
				{
					try
					{
						final FinalInterval subItvl = RenderUtil.getSubInterval( dest, dim2split, start, end );
						final IntervalView< T > subImg = Views.interval( img, subItvl );
						final IntervalView< S > subDest = Views.interval( dest, subItvl );
						System.out.println( "subImg sz: " + Util.printInterval( subImg ));
						System.out.println( "subDest sz: " + Util.printInterval( subDest ));

						processPerPixel( subImg, subDest );

						return true;
					}
					catch( Exception e )
					{
						e.printStackTrace();
					}
					return false;
				}
			});
		}

		try
		{
			List< Future< Boolean > > futures = threadPool.invokeAll( jobs );
			threadPool.shutdown(); // wait for all jobs to finish
		}
		catch ( InterruptedException e1 )
		{
			e1.printStackTrace();
		}
	}

	public <T extends RealType< T >> FloatImagePlus< FloatType > process( RandomAccessibleInterval< T > img )
	{
		long[] outputDim = Intervals.dimensionsAsLongArray( img );
		outputDim[ img.numDimensions() - 1 ] = centiles.length;

		System.out.println( "outputDim: "
				+  Arrays.toString( outputDim ));

		System.out.println( "allocating" );
		FloatImagePlus< FloatType > output = ImagePlusImgs.floats( outputDim );

		System.out.println( "working" );
		if( nThreads <= 1 )
			processPerPixel( img, output );
		else
			processPerPixelParallel( img, output );

		return output;
	}

	public <T extends RealType< T >> FloatImagePlus< FloatType > process( List< RandomAccessibleInterval< T > > imgList )
	{
		return process( Views.stack( imgList ));
	}

	public double[] centiles( final double[] values, final double[] results )
	{
		return centiles( values, centiles, results );
	}

	/*
	 * Computes the percentile as recommended by NIST
	 * http://www.itl.nist.gov/div898/handbook/prc/section2/prc262.htm
	 */
	public static double[] centiles( final double[] values, final double[] centiles, final double[] results )
	{
		// TODO unit tests - be sure to check case where all values are
		// identical
		Arrays.sort( values );
		int nV = values.length;
		int nC = centiles.length;

		if ( nV == 0 )
			return null;

		for ( int i = 0; i < nC; i++ )
		{
			double c = centiles[ i ];

			// skip all the logic when there is only one value
			if ( nV == 1 )
			{
				results[ i ] = values[ 0 ];
				continue;
			}

			if ( c <= 0.0 )
			{
				// min value
				results[ i ] = values[ 0 ];
			} else if ( c >= 1.0 )
			{
				// max value
				results[ i ] = values[ nV - 1 ];
			} else
			{
				double x = c * (nV + 1);
				int lo = (int) Math.floor( x ) - 1;
				int hi = (int) Math.ceil( x ) - 1;

				// this can happen when all values are the same
				if ( lo == -1 )
				{
					lo++;
					hi++;
				}
				if ( hi == nV )
				{
					lo--;
					hi--;
				}

				double m = x % 1;
				results[ i ] = (1 - m) * values[ lo ] + m * values[ hi ];
			}
		}
		return results;
	}

	/*
	 * Computes the percentile as recommended by NIST
	 * TODO find reference
	 */
	public double[] centiles( final double[] values )
	{
		int nC = centiles.length;
		double[] out = new double[ nC ];
		return centiles( values, out );
	}

	public Void call() throws ImgLibException
	{
		ArrayList<RandomAccessibleInterval<FloatType>> imglist = new ArrayList<RandomAccessibleInterval<FloatType>>();
		for( String imgPath : imagePathList )
		{
			System.out.println( "loading img: " + imgPath );
			imglist.add( ImageJFunctions.convertFloat( IJ.openImage( imgPath )));
		}
		System.out.println( "working" );
		output = process( imglist );

		System.out.println( "writing to: " );
		System.out.println( outputPath );
		ImagePlus ip = output.getImagePlus();
		IJ.save( ip, outputPath );

		return null;
	}

	public FloatImagePlus< FloatType > getOutput()
	{
		return output;
	}

	public static void main( String[] args ) throws ImgLibException
	{
		CommandLine.call( new WindowedCentileStats(), args );
	}

}
