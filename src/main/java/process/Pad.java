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
import loci.formats.FormatException;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
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
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.1.1-SNAPSHOT" )
public class Pad implements Callable<Void>
{
	public static final String LINEAR_INTERPOLATION = "LINEAR";
	public static final String NEAREST_INTERPOLATION = "NEAREST";


	@Option(names = {"--input", "-i"}, description = "Input image file" )
	private String inputFilePath;

	@Option(names = {"--output", "-o"}, description = "Output image file" )
	private String outputFilePath;

	@Option(names = {"--pad", "-p"}, description = "Pad amount in physical units",  split="," )
	private double[] padAmountIn;

	@Option(names = {"--padpixels", "-x"}, description = "Pad amount in pixels", split="," )
	private int[] padAmountPixelsIn;

	@Option(names = {"--interval", "-n"}, description = "Output interval at the output resolution" )
	private String intervalString;

	@Option(names = {"--convert", "-c"}, description = "Convert type" )
	private String outputType;

	@Option(names = {"--n5dataset"}, description = "N5 output dataset" )
	private String n5dataset = "data";
	
	private double[] padAmount;
	private int[] padAmountPixels;

	public static void main( String[] args ) throws ImgLibException
	{
		CommandLine.call( new Pad(), args );
	}
	
	public static <T extends RealType<T> & NativeType<T>> Converter< T, ? > 
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
	
	public Void call()
	{
		process();
		return null;
	}

	@SuppressWarnings( "unchecked" )
	public <T extends RealType<T> & NativeType<T>, S extends RealType<S> & NativeType<S> > void process() throws ImgLibException
	{
//
////		FloatImagePlus<FloatType> ipi = new FloatImagePlus<FloatType>( IJ.openImage( args[0] )  );
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
		else if( inputFilePath.contains( ".h5:" ))
		{
			String[] partList = inputFilePath.split( ":" );
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

		if( padAmountIn != null )
			padAmount = checkAndFillArrays( padAmountIn, nd, "pad physical");
		
		if( padAmountPixelsIn != null )
			padAmountPixels = checkAndFillArrays( padAmountPixelsIn, nd, "pad pixels" );
		
		System.out.println( "pad in pixels: " + padAmountPixels );
		
		/*
		 * Require either downsampleFactors or resultResolutions
		 * return early if they're both present to be safe 
		 */
//		if( true )
//		{
//			
//		}
//		else
//		{
//			System.err.println( "Must give input for either -r (--resolutions) or -f (--factors)" );
//			return;
//		}
		
//		System.out.println( Arrays.toString( padAmount ));
//		System.out.println( Arrays.toString( padAmountPixels ));
		
		int[] padlo = Arrays.copyOf( padAmountPixels, ipi.numDimensions() );
		int[] padhi = Arrays.copyOf( padAmountPixels, ipi.numDimensions() );
		
		Interval outputInterval = pad( ipi, padlo, padhi );
		System.out.println("Output Interval: " + Util.printInterval(outputInterval) );
		
		IntervalView< S > out = Views.interval( Views.extendZero( ipi ), outputInterval );

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
			ImagePlus ipout = ImageJFunctions.wrap( out, "padded" );

			if( ipin != null )
				ipout.getCalibration().setUnit( ipin.getCalibration().getUnit() );

			ipout.getCalibration().pixelWidth  = resIn[ 0 ];
			ipout.getCalibration().pixelHeight = resIn[ 1 ];
			ipout.getCalibration().pixelDepth  = resIn[ 2 ];
			
			IOHelper.write( ipout, outputFilePath);
		}

		System.out.println( "finished");
	}
	
	public static Interval pad( final Interval in, int[] padlo, int[] padhi )
	{
		int nd = in.numDimensions();
		long[] min = new long[ nd ];
		long[] max = new long[ nd ];
		for( int d = 0; d < nd; d++)
		{
			min[ d ] = in.min( d ) - padlo[ d ];
			max[ d ] = in.max( d ) + padhi[ d ];
		}
		return new FinalInterval( min, max );
	}

	public static Interval pad( final Interval in, long[] padlo, long[] padhi )
	{
		int nd = in.numDimensions();
		long[] min = new long[ nd ];
		long[] max = new long[ nd ];
		for( int d = 0; d < nd; d++)
		{
			min[ d ] = in.min( d ) - padlo[ d ];
			max[ d ] = in.max( d ) + padhi[ d ];
		}
		return new FinalInterval( min, max );
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
	
	public int[] checkAndFillArrays( int[] in, int nd, String kind )
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

	public static int[] fill( int[] in, int ndim )
	{
		int[] out = new int[ ndim ];
		Arrays.fill( out, in[ 0 ] );
		return out;
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
