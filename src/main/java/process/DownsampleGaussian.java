package process;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.janelia.utility.parse.ParseUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import ij.IJ;
import ij.ImagePlus;
import io.WritingHelper;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import jitk.spline.XfmUtils;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.exception.ImgLibException;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;
import util.RenderUtil;

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

	@Parameter(names = {"--resolutions", "-r"}, description = "Resolution of output", 
			converter = ParseUtils.DoubleArrayConverter.class )
	private double[] resultResolutionsIn;
	
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

	@Parameter(names = {"--interval", "-n"}, description = "Output interval at the output resolution" )
	private String intervalString;
	
	private double[] resultResolutions;
	private double[] downsampleFactors;
	private double[] sourceSigmas;
	private double[] targetSigmas;

	public static void main( String[] args ) throws ImgLibException
	{
		DownsampleGaussian dg = DownsampleGaussian.parseCommandLineArgs( args );
		dg.process();
	}

	public <T extends RealType<T> & NativeType<T> > void process() throws ImgLibException
	{

//		FloatImagePlus<FloatType> ipi = new FloatImagePlus<FloatType>( IJ.openImage( args[0] )  );
		System.out.print( "Loading\n" + inputFilePath + "\n...");

		ImagePlus ipin = null;
		if( inputFilePath.endsWith( "nii" ))
		{
			try
			{
				ipin = NiftiIo.readNifti( new File( inputFilePath ));
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
			ipin = IJ.openImage( inputFilePath );
		}

		@SuppressWarnings("unchecked")
		RandomAccessibleInterval< T > ipi = ( RandomAccessibleInterval< T > )ImageJFunctions.wrap( ipin );
		System.out.println( "finished");
		System.out.println( ipi );

		int nd = ipi.numDimensions();
		// make sure the input factors and sigmas are the correct sizes

		if( downsampleFactorsIn != null )
			downsampleFactors = checkAndFillArrays( downsampleFactorsIn, nd, "factors");
		
		if( resultResolutionsIn != null )
			resultResolutions = checkAndFillArrays( resultResolutionsIn, nd, "resolutions" );
		
		/*
		 * Require either downsampleFactors or resultResolutions
		 * return early if they're both present to be safe 
		 */
		if( resultResolutions != null && downsampleFactors != null)
		{
			System.err.println( "AMBIGUOUS INPUT : only ONE of -r or -f inputs allowed" );
			return;
		}
		else if( resultResolutions != null )
		{
			System.out.println( "Inferring downsample factors from resolution inputs" );
			double[] resIn = new double[]{ 
					ipin.getCalibration().pixelWidth,
					ipin.getCalibration().pixelHeight,
					ipin.getCalibration().pixelDepth };

			downsampleFactors = new double[ nd ];
			for( int d = 0; d < nd; d++ )
			{
				downsampleFactors[ d ] = resultResolutions[ d ] / resIn[ d ];
			}

			System.out.println( "resultResolutions: " + XfmUtils.printArray( resultResolutions ));
			System.out.println( "downsampleFactors: " + XfmUtils.printArray( downsampleFactors ));
		}
		else if( downsampleFactors != null  )
		{
			System.out.println( "Inferring resolution outputs from downsample factors input" );
			double[] resIn = new double[]{ 
					ipin.getCalibration().pixelWidth,
					ipin.getCalibration().pixelHeight,
					ipin.getCalibration().pixelDepth };

			resultResolutions = new double[ nd ];
			for( int d = 0; d < nd; d++ )
			{
				resultResolutions[ d ] = downsampleFactors[ d ] * resIn[ d ];
			}
			
			System.out.println( "resultResolutions: " + XfmUtils.printArray( resultResolutions ));
			System.out.println( "downsampleFactors: " + XfmUtils.printArray( downsampleFactors ));
		}
		else
		{
			System.err.println( "Must give input for either -r (--resolutions) or -f (--factors)" );
			return;
		}
		
		sourceSigmas = checkAndFillArrays( sourceSigmasIn, nd, "source sigmas");
		targetSigmas = checkAndFillArrays( targetSigmasIn, nd, "target sigmas");

		System.out.println("nthreads: " + nThreads );
		System.out.println( XfmUtils.printArray( sourceSigmas ));
		System.out.println( XfmUtils.printArray( targetSigmas ));
		
		Interval outputInterval = ipi;
		long[] offset = new long[ nd ];
		if( intervalString != null && !intervalString.isEmpty() )
		{
			System.out.println( "itvl string: " + intervalString );
			outputInterval = RenderTransformed.parseInterval( intervalString );
		}
		else
		{
			long[] newSz  = new long[ nd ];
			for( int d = 0; d < nd; d++)
			{
				newSz[d] = (long)Math.ceil( ipi.dimension( d ) / downsampleFactors[d] );
				offset[ d ] = outputInterval.min( d );
			}
			outputInterval = new FinalInterval( newSz );
			
		}
		System.out.println("Output Interval: " + Util.printInterval(outputInterval) );

		InterpolatorFactory< T, RandomAccessible< T > > interpfactory = 
				getInterp( interpType, Views.flatIterable( ipi ).firstElement() );

		ImagePlusImgFactory< T > factory = new ImagePlusImgFactory< T >();
		ImagePlusImg< T, ? > out = factory.create( outputInterval, Views.flatIterable( ipi ).firstElement());


		resampleGaussianInplace( 
				ipi, out, offset,
				interpfactory, 
				downsampleFactors, sourceSigmas, targetSigmas,
				nThreads );


		System.out.print( "Saving to\n" + outputFilePath + "\n...");
		ImagePlus ipout = out.getImagePlus();
		ipout.getCalibration().setUnit( ipin.getCalibration().getUnit() );
		ipout.getCalibration().pixelWidth  = resultResolutions[ 0 ];
		ipout.getCalibration().pixelHeight = resultResolutions[ 1 ];
		ipout.getCalibration().pixelDepth  = resultResolutions[ 2 ];

		WritingHelper.write( ipout, outputFilePath);
		System.out.println( "finished");
	}

	public double[] checkAndFillArrays( double[] in, int nd, String kind )
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
			final long[] newSz,
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
//		long[] newSz  = new long[ ndims ];

		for( int d = 0; d<ndims; d++)
		{
			double s = targetSigmas[d] * downsampleFactors[d]; 
			sigs[d] = Math.sqrt( s * s  - sourceSigmas[d] * sourceSigmas[d] );
//			newSz[d] = (long)Math.ceil( sz[d] / downsampleFactors[d] );
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

		int[] pos = new int[ ndims ];
		RandomAccess<T> inRa = img.randomAccess();
		inRa.setPosition(pos);

		System.out.print( "Allocating...");
		Img<T> out = factory.create( outputInterval, inRa.get());

		resampleGaussianInplace( img, out, interpFactory,
			downsampleFactors, sourceSigmas, targetSigmas,
			xfm, outputInterval, nThreads );

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
	public static <T extends RealType<T> & NativeType<T>> void resampleGaussianInplace( 
			final RandomAccessibleInterval< T > img,
			final RandomAccessibleInterval< T > out,
			final InterpolatorFactory< T, RandomAccessible< T > > interpFactory,
			final double[] downsampleFactors, 
			final double[] sourceSigmas,
			final double[] targetSigmas,
			AffineGet xfm,
			Interval outputInterval,
			final int nThreads )
	{

		int ndims = img.numDimensions();
		long[] sz = new long[ ndims ];

		img.dimensions(sz);
		double[] sigs = new double[ ndims ];

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

		RealRandomAccessible< T > realImg = Views.interpolate( Views.extendZero( img ), interpFactory );
		AffineRandomAccessible< T, AffineGet > rrax = RealViews.affine( realImg, xfm.inverse() );
		RandomAccessibleOnRealRandomAccessible< T > imgXfm = Views.raster( rrax );	

		System.out.print( "Resampling with " + nThreads + " threads ...");
		RenderUtil.copyToImageStack( imgXfm, out, nThreads );

//		BdvStackSource< T > bdv = BdvFunctions.show( rrax, out, "rrax", BdvOptions.options() );

//		RealRandomAccess< T > rra = realImg.realRandomAccess();
//		System.out.print( "Resampling..");
//		Cursor<T> outc = Views.iterable( out ).cursor();
//		while( outc.hasNext() )
//		{
//			outc.fwd();
//			// set the position of the rra
//			xfm.apply( outc, rra );
//
//			outc.get().set( rra.get() );
//		}

		System.out.println( "finished");

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
	public static <T extends RealType<T> & NativeType<T>> void resampleGaussianInplace( 
			final RandomAccessibleInterval< T > img,
			final RandomAccessibleInterval< T > out,
			final long[] offset,
			final InterpolatorFactory< T, RandomAccessible< T > > interpFactory,
			final double[] downsampleFactors, 
			final double[] sourceSigmas,
			final double[] targetSigmas,
			final int nThreads )
	{
	
		int ndims = img.numDimensions();

		double[] sigs = new double[ ndims ];
		for( int d = 0; d<ndims; d++)
		{
			double s = targetSigmas[d] * downsampleFactors[d];
			sigs[d] = Math.sqrt( s * s  - sourceSigmas[d] * sourceSigmas[d] );
		}
		
		System.out.println( "using sigs: " + Arrays.toString( sigs ));
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


		double[] min = new double[ out.numDimensions() ];
		for( int d = 0; d<ndims; d++)
			min[ d ] = offset[ d ] * downsampleFactors[ d ];

		System.out.println( "min : " + Arrays.toString( min ));

		// use LUT to trade memory for speed 
		System.out.print( "Build LUT.."); 
		double[][] coordLUT = new double[ndims][];
		for( int d = 0; d<ndims; d++)
		{
			int nd = (int)out.dimension(d);
			coordLUT[d] = new double[nd];
			for( int i = 0; i<nd; i++)
			{
				coordLUT[d][i] =  ( min[ d ] +  i * downsampleFactors[d] ) + downsampleFactors[d] / 2;
			}
		}
		
//		RealRandomAccessible< T > rra = Views.interpolate( Views.extendZero( img ), interpFactory );
//		RealRandomAccess< T > rrab = rra.realRandomAccess();

		RealRandomAccess< T > rra = Views.interpolate( Views.extendZero( img ), interpFactory ).realRandomAccess();


		System.out.print( "Resampling..");
		int[] pos = new int[ out.numDimensions() ];
		Cursor<T> outc = Views.iterable( out ).cursor();
		while( outc.hasNext() )
		{
			outc.fwd();
			outc.localize( pos );
			setPositionFromLut( pos, coordLUT, rra );
			outc.get().set( rra.get() );
		}
		System.out.println( "finished");

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
