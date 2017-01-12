package process;

import java.util.Arrays;

import org.janelia.utility.parse.ParseUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import jitk.spline.XfmUtils;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccess;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.ImgLibException;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class DownsampleGaussian
{
	public static final String LINEAR_INTERPOLATION = "LINEAR";
	public static final String NEAREST_INTERPOLATION = "NEAREST";

	private transient JCommander jCommander;

	@Parameter(names = {"--input", "-i"}, description = "Input image file" )
	private String inputFilePath;

	@Parameter(names = {"--output", "-o"}, description = "Output image file" )
	private String outputFilePath;

	@Parameter(names = {"--factors", "-f"}, description = "Downsample factors", 
			converter = ParseUtils.DoubleArrayConverter.class )
	private double[] downsampleFactorsIn;

	@Parameter(names = {"--threads", "-j"}, description = "Number of threads" )
	private int nThreads = -1;

	@Parameter(names = {"--interp", "-p"}, description = "Interpolation type" )
	private String interpType = "LINEAR";

	@Parameter(names = {"--sourceSigma", "-s"}, description = "Sigma for source image", 
			converter = ParseUtils.DoubleArrayConverter.class )
	private double[] sourceSigmasIn = new double[]{ 0.5 };

	@Parameter(names = {"--targetSigma", "-t"}, description = "Sigma for target image", 
			converter = ParseUtils.DoubleArrayConverter.class )
	private double[] targetSigmasIn = new double[]{ 0.5 };

	private double[] downsampleFactors;
	private double[] sourceSigmas;
	private double[] targetSigmas;

	public static void main( String[] args ) throws ImgLibException
	{
		DownsampleGaussian dg = DownsampleGaussian.parseCommandLineArgs( args );
		dg.process();
	}

	public <T extends RealType<T> & NativeType<T> > void process()
	{

//		FloatImagePlus<FloatType> ipi = new FloatImagePlus<FloatType>( IJ.openImage( args[0] )  );
		System.out.print( "Loading\n" + inputFilePath + "\n...");
		@SuppressWarnings("unchecked")
		RandomAccessibleInterval< T > ipi = ( RandomAccessibleInterval< T > )ImageJFunctions.wrap( IJ.openImage( inputFilePath ));
		System.out.println( "finished");
		System.out.println( ipi );

		int nd = ipi.numDimensions();
		// make sure the input factors and sigmas are the correct sizes

		downsampleFactors = checkAndFillArrays( downsampleFactorsIn, nd, "factors");
		sourceSigmas = checkAndFillArrays( sourceSigmasIn, nd, "source sigmas");
		targetSigmas = checkAndFillArrays( targetSigmasIn, nd, "target sigmas");

		System.out.println("nthreads: " + nThreads );

		System.out.println( XfmUtils.printArray( downsampleFactors ));
		System.out.println( XfmUtils.printArray( sourceSigmas ));
		System.out.println( XfmUtils.printArray( targetSigmas ));

		InterpolatorFactory< T, RandomAccessible< T > > interpfactory = 
				getInterp( interpType, Views.flatIterable( ipi ).firstElement() );

		Img< T > out = ( Img< T > )resampleGaussian( 
				ipi, interpfactory, 
				downsampleFactors, sourceSigmas, targetSigmas,
				nThreads );

		System.out.print( "Saving to\n" + outputFilePath + "\n...");
		IJ.save( ImageJFunctions.wrap( out, "out"), outputFilePath );
		System.out.println( "finished");
	}

	public  double[] checkAndFillArrays( double[] in, int nd, String kind )
	{
		if( in.length == 1 )
			return fill( in, nd );
		else if( in.length == nd )
			return in;
		else
		{
			System.err.println( "Error interpreting " + kind + " : " + 
					" image has " + nd + " dimensions, and input is of length " + in.length + 
					".  Expected length 1 or " + nd );
		}
		return null;
	}

	public static DownsampleGaussian parseCommandLineArgs( final String[] args )
	{
		DownsampleGaussian ds = new DownsampleGaussian();
		ds.initCommander();
		try 
		{
			ds.jCommander.parse( args );
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		return ds;
	}

	private void initCommander()
	{
		jCommander = new JCommander( this );
		jCommander.setProgramName( "input parser" ); 
	}

	/**
	 * Create a downsampled {@link Img}.
	 *
	 * @param img the source image
	 * @param downsampleFactors scaling factors in each dimension
	 * @param sourceSigmas the Gaussian at which the source was sampled (guess 0.5 if you do not know)
	 * @param targetSigmas the Gaussian at which the target will be sampled
	 *
	 * @return a new {@link Img}
	 */
	public static <T extends RealType<T> & NativeType<T>> Img<T> resampleGaussian( 
			final RandomAccessibleInterval< T > img, 
//			final ImgFactory< T > factory,
			final InterpolatorFactory< T, RandomAccessible< T > > interpFactory,
			final double[] downsampleFactors, 
			final double[] sourceSigmas,
			final double[] targetSigmas,
			final int nThreads )
	{
		ImgFactory< T > factory = new ImagePlusImgFactory< T >();
		int ndims = img.numDimensions();
		long[] sz = new long[ ndims ];

		img.dimensions(sz);

		double[] sigs = new double[ ndims ];
		long[] newSz  = new long[ ndims ];

		for( int d = 0; d<ndims; d++)
		{
			double s = targetSigmas[d] * downsampleFactors[d]; 
			sigs[d] = Math.sqrt( s * s  - sourceSigmas[d] * sourceSigmas[d] );
			newSz[d] = (long)Math.ceil( sz[d] / downsampleFactors[d] );
		}

		int[] pos = new int[ ndims ];
		RandomAccess<T> inRa = img.randomAccess();
		inRa.setPosition(pos);

		System.out.print( "Allocating...");
		Img<T> out = factory.create( newSz, inRa.get());
		System.out.println( "finished");

		try
		{
			System.out.print( "Gaussian..");
			if ( nThreads < 1 )
			{
				System.out.println( "using all threads" );
				Gauss3.gauss( sigs, Views.extendBorder( img ), img );
			}
			else
			{
				System.out.println( "using " + nThreads + " threads" );
				Gauss3.gauss( sigs, Views.extendBorder( img ), img, nThreads );
			}
			System.out.println( "finished");
		}
		catch ( IncompatibleTypeException e )
		{
			e.printStackTrace();
		}

		RealRandomAccess< T > rra = Views.interpolate( Views.extendZero( img ), interpFactory ).realRandomAccess();

		System.out.print( "Resampling..");
		double[][] coordLUT = new double[ndims][];
		for( int d = 0; d<ndims; d++)
		{
			int nd = (int)img.dimension(d);
			coordLUT[d] = new double[nd];
			for( int i = 0; i<nd; i++)
			{
				coordLUT[d][i] =  ( i * downsampleFactors[d] ) + downsampleFactors[d] / 2;
			}
		}

		Cursor<T> outc = out.cursor();
		while( outc.hasNext() )
		{
			outc.fwd();
			outc.localize( pos );
			setPositionFromLut( pos, coordLUT, rra );
			outc.get().set( rra.get() );
		}
		System.out.println( "finished");
		return out;
	}

	/**
	 * Create a downsampled {@link Img}.
	 *
	 * @param img the source image
	 * @param downsampleFactors scaling factors in each dimension
	 * @param sourceSigmas the Gaussian at which the source was sampled (guess 0.5 if you do not know)
	 * @param targetSigmas the Gaussian at which the target will be sampled
	 *
	 * @return a new {@link Img}
	 */
	public static <T extends RealType<T> & NativeType<T>> Img<T> resampleGaussian( 
			final RandomAccessibleInterval< T > img, 
//			final ImgFactory< T > factory,
			final InterpolatorFactory< T, RandomAccessible< T > > interpFactory,
			final double[] downsampleFactors, 
			final double[] sourceSigmas,
			final double[] targetSigmas,
			AffineGet xfm,
			Interval outputInterval,
			final int nThreads )
	{
		ImgFactory< T > factory = new ImagePlusImgFactory< T >();
		int ndims = img.numDimensions();
		long[] sz = new long[ ndims ];

		img.dimensions(sz);
		double[] sigs = new double[ ndims ];
		long[] newSz  = new long[ ndims ];

		for( int d = 0; d<ndims; d++)
		{
			double s = targetSigmas[d] * downsampleFactors[d]; 
			sigs[d] = Math.sqrt( s * s  - sourceSigmas[d] * sourceSigmas[d] );
			newSz[d] = (long)Math.ceil( sz[d] / downsampleFactors[d] );
		}

		int[] pos = new int[ ndims ];
		RandomAccess<T> inRa = img.randomAccess();
		inRa.setPosition(pos);

		System.out.print( "Allocating...");
		Img<T> out = factory.create( outputInterval, inRa.get());
		System.out.println( "finished");

		try
		{
			System.out.print( "Gaussian..");
			if ( nThreads < 1 )
			{
				System.out.println( "using all threads" );
				Gauss3.gauss( sigs, Views.extendBorder( img ), img );
			}
			else
			{
				System.out.println( "using " + nThreads + " threads" );
				Gauss3.gauss( sigs, Views.extendBorder( img ), img, nThreads );
			}
			System.out.println( "finished");
		}
		catch ( IncompatibleTypeException e )
		{
			e.printStackTrace();
		}

		RealRandomAccess< T > rra = Views.interpolate( Views.extendZero( img ), interpFactory ).realRandomAccess();
		System.out.print( "Resampling..");
		Cursor<T> outc = out.cursor();
		while( outc.hasNext() )
		{
			outc.fwd();
			// set the position of the rra
			xfm.apply( outc, rra );

			outc.get().set( rra.get() );
		}
		System.out.println( "finished");

		return out;
	}

	public static void setPositionFromLut( int[] destPos, double[][] lut,
			RealPositionable srcPos )
	{
		for ( int d = 0; d < destPos.length; d++ )
			srcPos.setPosition( lut[ d ][ destPos[ d ] ], d );
	}

	public static double[] fill( double[] in, int ndim )
	{
		double[] out = new double[ ndim ];
		Arrays.fill( out, in[ 0 ] );
		return out;
	}

	public static <T extends RealType< T >> InterpolatorFactory< T, RandomAccessible< T > > getInterp(
			String type, T t )
	{
		if ( type.equals( LINEAR_INTERPOLATION ) )
		{
			return new NLinearInterpolatorFactory< T >();
		} else if ( type.equals( NEAREST_INTERPOLATION ) )
		{
			return new NearestNeighborInterpolatorFactory< T >();
		} else
			return null;
	}

}
