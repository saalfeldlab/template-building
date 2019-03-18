package process;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.utility.parse.ParseUtils;

import ij.IJ;
import ij.ImagePlus;
import io.IOHelper;
import io.nii.NiftiIo;
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
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
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
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import util.RenderUtil;

@Command( version = "0.1.1-SNAPSHOT" )
public class DownsampleGaussian implements Callable<Void>
{
	public static final String LINEAR_INTERPOLATION = "LINEAR";
	public static final String NEAREST_INTERPOLATION = "NEAREST";

	@Option( names = { "--input", "-i" }, description = "Input image file" )
	private String inputFilePath;

	@Option( names = { "--output", "-o" }, description = "Output image file" )
	private String outputFilePath;

	@Option( names = { "--factors", "-f" }, description = "Downsample factors", split=",")
	private double[] downsampleFactorsIn;

	@Option( names = { "--resolutions", "-r" }, description = "Resolution of output", split=",")
	private double[] resultResolutionsIn;

	@Option( names = { "--threads", "-j" }, description = "Number of threads" )
	private int nThreads = -1;

	@Option( names = { "--interp", "-p" }, description = "Interpolation type" )
	private String interpType = "LINEAR";

	@Option( names = { "--sourceSigma", "-s" }, description = "Sigma for source image", split="," )
	private double[] sourceSigmasIn = new double[] { 0.5 };

	@Option( names = { "--targetSigma", "-t" }, description = "Sigma for target image", split="," )
	private double[] targetSigmasIn = new double[] { 0.5 };

	@Option( names = { "--interval", "-n" }, description = "Output interval at the output resolution" )
	private String intervalString;

	@Option( names = { "--convert", "-c" }, description = "Convert type" )
	private String outputType;

	@Option( names = { "--n5dataset" }, description = "N5 output dataset" )
	private String n5dataset = "data";

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Print this help message" )
	private boolean help;
	
	private double[] resultResolutions;
	private double[] downsampleFactors;
	private double[] sourceSigmas;
	private double[] targetSigmas;

	private double epsilon = 1e-6;

	public static void main( String[] args ) throws ImgLibException
	{
		CommandLine.call( new DownsampleGaussian(), args );
	}
	
	public static <T extends RealType<T> & NativeType<T>> Converter< T, ? > 
	//public static <? extends RealType<?> & NativeType<?>> Converter< ?, ? > 
	//public static <T extends RealType<T> & NativeType<T>> Converter< T, ? > 
		getConverter( T inputType, String outputType )
	{
		String typeString = outputType.toLowerCase();
		switch( typeString )
		{
		case "float": return getConverter( inputType, new FloatType() );
		case "double": return getConverter( inputType, new DoubleType() );
		case "byte": return getConverter( inputType, new ByteType() );
		case "ubyte": return getConverter( inputType, new UnsignedByteType() );
		case "short": return getConverter( inputType, new ShortType() );
		case "ushort": return getConverter( inputType, new UnsignedShortType() );
		case "int": return getConverter( inputType, new IntType() );
		case "uint": return getConverter( inputType, new UnsignedIntType() );
		case "long": return getConverter( inputType, new LongType() );
		case "ulong": return getConverter( inputType, new UnsignedLongType() );
		default: return null;
		}
	}

	public static <T extends RealType<T> & NativeType<T>, O extends RealType<O> & NativeType<O>> Converter< T, O > getConverter(
			T inputType, O outputType )
	{
		return new Converter< T, O >(){
			@Override
			public void convert( T input, O output )
			{
				output.setReal( input.getRealDouble() );	
			}
		};
	}
	
	public static <T extends RealType<T> & NativeType<T>, O extends RealType<O> & NativeType<O>> 
		RandomAccessibleInterval< O > convert(
			RandomAccessibleInterval< T > img,
			Converter<T,O> conv,
			O o)
	{
		return Converters.convert( img, conv, o );
	}

	public void setEpsiolon( final double eps )
	{
		this.epsilon = eps;
	}
	
	public Void call()
	{
		process();
		return null;
	}

	@SuppressWarnings( "unchecked" )
	public <T extends RealType<T> & NativeType<T>, S extends RealType<S> & NativeType<S> > void process() throws ImgLibException
	{
		System.out.print( "Loading\n" + inputFilePath + "\n...");

		ImagePlus ipin = null;
		RandomAccessibleInterval<T> ipi_read = null;
		double[] resIn = null;
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
		else if( inputFilePath.endsWith( ".nrrd" ))
		{
			IOHelper io = new IOHelper();
			ipin = io.readIp( new File( inputFilePath ));
		}
		else if( inputFilePath.contains( ".h5:" ))
		{
			String inPath = new File( inputFilePath ).getAbsolutePath();
			String[] partList = inPath.split( ":" );
			String fpath = partList[ 0 ];
			String dset = partList[ 1 ];
			
			System.out.println( "fpath: " + fpath );
			System.out.println( "dset: " + dset );
			
			N5HDF5Reader n5;
			try
			{
				n5 = new N5HDF5Reader( fpath, 32, 32, 32 );
				ipi_read = N5Utils.open( n5, dset );

				float[] rtmp = n5.getAttribute( dset, "element_size_um", float[].class );
				if( rtmp != null )
				{
					resIn = new double[ 3 ];
					// h5 attributes are usually listed zyx not xyz
					resIn[ 0 ] = rtmp[ 2 ];
					resIn[ 1 ] = rtmp[ 1 ];
					resIn[ 2 ] = rtmp[ 0 ];
				}
				else
					resIn = new double[]{ 1, 1, 1 };
			
				System.out.println( "res: " + Arrays.toString( resIn ));

			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			ipin = IJ.openImage( inputFilePath );
		}
		if( ipin != null )
		{
			resIn = new double[]{ 
					ipin.getCalibration().pixelWidth,
					ipin.getCalibration().pixelHeight,
					ipin.getCalibration().pixelDepth };
		}

		if( ipi_read == null )
		{
			ipi_read = ( RandomAccessibleInterval< T > )ImageJFunctions.wrap( ipin );
		}

		System.out.println( "finished");
		System.out.println( ipi_read );
		
		
		
		T inputType = Views.flatIterable( ipi_read ).firstElement();
		
		RandomAccessibleInterval< S > ipi = null;
		if( outputType != null && !outputType.isEmpty() )
		{
			System.out.println( "converting to: " + outputType );

			S convType = null;
			String typeString = outputType.toLowerCase();
			if( typeString.equals("float") )
			{
				convType = (S)new FloatType();
			}
			else if( typeString.equals("double"))
			{
				convType = (S)new DoubleType();
			}
			else if( typeString.equals("ubyte"))
			{
				convType = (S)new UnsignedByteType();
			}
			else if( typeString.equals("byte"))
			{
				convType = (S)new ByteType();
			}
			else if( typeString.equals("uint") || typeString.equals("uinteger") )
			{
				convType = (S)new UnsignedIntType();
			}
			else if( typeString.equals("int") || typeString.equals("integer") )
			{
				convType = (S)new IntType();
			}
			else if( typeString.equals("ushort"))
			{
				convType = (S)new UnsignedShortType();
			}
			else if( typeString.equals("short"))
			{
				convType = (S)new ShortType();
			}
			else if( typeString.equals("ulong"))
			{
				convType = (S)new LongType();
			}
			else if( typeString.equals("long"))
			{
				convType = (S)new LongType();
			}

			ipi = (RandomAccessibleInterval<S>)Converters.convert( ipi_read, getConverter( inputType, convType ), convType );
		}
		else
		{
			ipi = (RandomAccessibleInterval<S>)ipi_read;
		}

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
			outputInterval = inferOutputIntervalFromFactors( ipi, downsampleFactors );
		}
		System.out.println("Output Interval: " + Util.printInterval(outputInterval) );

	
		
		InterpolatorFactory< S, RandomAccessible< S > > interpfactory = 
				getInterp( interpType, Views.flatIterable( ipi ).firstElement() );

		ImagePlusImgFactory< S > factory = new ImagePlusImgFactory< S >( Views.flatIterable( ipi ).firstElement());
		ImagePlusImg< S, ? > out = factory.create( outputInterval );

		validateSigmas( sourceSigmas, epsilon );
		validateSigmas( targetSigmas, epsilon );

		resampleGaussianInplace( 
				ipi, out, offset,
				interpfactory, 
				downsampleFactors, sourceSigmas, targetSigmas,
				nThreads,
				epsilon );


		if( outputFilePath.endsWith( "h5" ))
		{
			System.out.print( "Saving to\n" + outputFilePath + " ( dataset: " + n5dataset + "\n...");
			try
			{
				N5HDF5Writer n5 = new N5HDF5Writer( outputFilePath, 32, 32, 32 );
				N5Utils.save( out, n5, n5dataset, new int[]{ 32, 32, 32 }, new GzipCompression() );

				float[] resFloat = new float[ resIn.length ];
				for(int d=0; d<resIn.length; d++)
					resFloat[ resIn.length - d - 1] = (float)resIn[d];

				n5.setAttribute( n5dataset, "element_size_um", resFloat );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{

			System.out.print( "Saving to\n" + outputFilePath + "\n...");
			ImagePlus ipout = out.getImagePlus();

			if( ipin != null )
				ipout.getCalibration().setUnit( ipin.getCalibration().getUnit() );

			ipout.getCalibration().pixelWidth  = resultResolutions[ 0 ];
			ipout.getCalibration().pixelHeight = resultResolutions[ 1 ];
			ipout.getCalibration().pixelDepth  = resultResolutions[ 2 ];
			
			IOHelper.write( ipout, outputFilePath);
		}

		System.out.println( "finished");
	}
	
	public static void validateSigmas( final double[] sigmas, double epsilon )
	{
		for( int i = 0; i < sigmas.length; i++ )
		{
			if( sigmas[ i ] < epsilon || Double.isNaN( sigmas[ i ] ))
				sigmas[ i ] = epsilon;
		}
	}

	public static Interval inferOutputIntervalFromFactors(
			Interval inputInterval,
			double[] factors)
	{
		int nd = inputInterval.numDimensions();
		long[] newSz  = new long[ nd ];
		long[] offset = new long[ nd ];
		for( int d = 0; d < nd; d++)
		{
			newSz[d] = (long)Math.ceil( inputInterval.dimension( d ) / factors[d] );
			offset[ d ] = inputInterval.min( d );
		}
		return new FinalInterval( newSz );
	}
	
	public static <S extends RealType<S> & NativeType<S>, T extends RealType<T> & NativeType<T>>
		RandomAccessibleInterval<T> run(
			RandomAccessibleInterval<S> in, 
			ImgFactory<T> outputFactory,
			String interpType,
			double[] factors,
			double[] sourceSigmas,
			double[] targetSigmas,
			int nThreads,
			double epsilon )

	{
		int nd = in.numDimensions();
		long[] offset = new long[ nd ];
		Interval outputInterval = inferOutputIntervalFromFactors( in, factors );
		System.out.println("Output Interval: " + Util.printInterval(outputInterval) );

		InterpolatorFactory< S, RandomAccessible< S > > interpfactory = 
				getInterp( interpType , Views.flatIterable( in ).firstElement() );

		Img< T > out = outputFactory.create( outputInterval );

		resampleGaussianInplace( 
				in, out, offset,
				interpfactory, 
				factors, sourceSigmas, targetSigmas,
				nThreads, epsilon );
		
		return out;
	}

	public static double[] checkAndFillArrays( double[] in, int nd, String kind )
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
			final InterpolatorFactory< T, RandomAccessible< T > > interpFactory,
			final long[] newSz,
			final double[] downsampleFactors, 
			final double[] sourceSigmas,
			final double[] targetSigmas,
			final int nThreads )
	{
		ImgFactory< T > factory = new ImagePlusImgFactory< T >( Views.flatIterable( img ).firstElement() );
		int ndims = img.numDimensions();
		long[] sz = new long[ ndims ];

		img.dimensions(sz);

		double[] sigs = new double[ ndims ];

		for( int d = 0; d<ndims; d++)
		{
			double s = targetSigmas[d] * downsampleFactors[d]; 
			sigs[d] = Math.sqrt( s * s  - sourceSigmas[d] * sourceSigmas[d] );
		}

		int[] pos = new int[ ndims ];
		RandomAccess<T> inRa = img.randomAccess();
		inRa.setPosition(pos);

		System.out.print( "Allocating...");
		Img<T> out = factory.create( newSz );
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
		ImgFactory< T > factory = new ImagePlusImgFactory< T >( Views.flatIterable( img ).firstElement());
		int ndims = img.numDimensions();
		long[] sz = new long[ ndims ];

		int[] pos = new int[ ndims ];
		RandomAccess<T> inRa = img.randomAccess();
		inRa.setPosition(pos);

		System.out.print( "Allocating...");
		Img<T> out = factory.create( outputInterval );

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
	public static <T extends RealType<T> & NativeType<T>, S extends RealType<S> & NativeType<S>> void resampleGaussianInplace( 
			final RandomAccessibleInterval< S > img,
			final RandomAccessibleInterval< T > out,
			final long[] offset,
			final InterpolatorFactory< S, RandomAccessible< S > > interpFactory,
			final double[] downsampleFactors, 
			final double[] sourceSigmas,
			final double[] targetSigmas,
			final int nThreads,
			final double epsilon )
	{
	
		int ndims = img.numDimensions();

		if( !Double.isNaN( targetSigmas[0]) && !Double.isNaN(sourceSigmas[0]))
		{
			double[] sigs = new double[ ndims ];
			for( int d = 0; d<ndims; d++)
			{
				double s = targetSigmas[d] * downsampleFactors[d];
				sigs[d] = Math.sqrt( s * s  - sourceSigmas[d] * sourceSigmas[d] );

				if( sigs[d] < epsilon || Double.isNaN( sigs[ d ]))
					sigs[ d ] = epsilon;
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
				coordLUT[d][i] =  ( min[ d ] +  i * downsampleFactors[d] );
			}
		}
		
//		RealRandomAccessible< T > rra = Views.interpolate( Views.extendZero( img ), interpFactory );
//		RealRandomAccess< T > rrab = rra.realRandomAccess();

		RealRandomAccess< S > rra = Views.interpolate( Views.extendBorder( img ), interpFactory ).realRandomAccess();


		System.out.print( "Resampling..");
		int[] pos = new int[ out.numDimensions() ];
		Cursor<T> outc = Views.iterable( out ).cursor();
		while( outc.hasNext() )
		{
			outc.fwd();
			outc.localize( pos );
			setPositionFromLut( pos, coordLUT, rra );
			outc.get().setReal( rra.get().getRealDouble() );
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
			System.out.println( "LINEAR interp");
			return new NLinearInterpolatorFactory< T >();
		}
		else if ( type.equals( NEAREST_INTERPOLATION ) )
		{
			System.out.println( "NEAREST interp");
			return new NearestNeighborInterpolatorFactory< T >();
		} else
			return null;
	}

}
