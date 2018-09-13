package io;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.utility.parse.ParseUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import sc.fiji.io.Nrrd_Reader;
import util.RenderUtil;


public class WriteH5DisplacementField {
	
	public static final String SHORTTYPE = "SHORT";
	public static final String BYTETYPE = "BYTE";
	
	public static class Options implements Serializable
	{

		private static final long serialVersionUID = -5666039337474416226L;

		@Option( name = "-d", aliases = {"--dfield"}, required = true, usage = "" )
		private String field;
		
		@Option( name = "-a", aliases = {"--affine"}, required = false, usage = "" )
		private String affine;
		
		@Option( name = "-o", aliases = {"--output"}, required = true, usage = "" )
		private String output;

		@Option( name = "-i", aliases = {"--inverse"}, required = false, usage = "" )
		private boolean isInverse = false;
		
		@Option( name = "-f", aliases = {"--factors"}, required = false, usage = "" )
		private String subsampleFactorsArg;
		
		@Option( name = "-t", aliases = {"--type"}, required = false, usage = "" )
		private String toShort;
		
		@Option( name = "-b", aliases = {"--blockSize"}, required = false, usage = "" )
		private String blockSizeArg;
		
		@Option( name = "-m", aliases = {"--maxValue"}, required = false, usage = "" )
		private double maxValue = Double.NaN;
		
		
		private int[] blockSizeDefault = new int[]{ 3, 32, 32, 32 };

		public Options(final String[] args)
		{

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);
			} catch (final CmdLineException e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}

		/**
		 * @return is this an inverse transform
		 */
		public boolean isInverse()
		{
			return isInverse;
		}
		
		/**
		 * @return is this an inverse transform
		 */
		public String convertType()
		{
			return toShort;
		}

		/**
		 * @return output path
		 */
		public String getOutput()
		{
			return output;
		}
		
		/**
		 * @return output path
		 */
		public String getField()
		{
			return field;
		}
		
		/**
		 * @return hdf5 block size
		 */
		public int[] getBlockSize()
		{
			if( blockSizeArg == null )
				return blockSizeDefault;
			else
				return ParseUtils.parseIntArray( blockSizeArg );
		}
		
		/**
		 * @return subsample factors
		 */
		public double[] getSubsampleFactors()
		{
			if( subsampleFactorsArg == null )
				return null;
			else
				return ParseUtils.parseDoubleArray( subsampleFactorsArg );
		}

		/**
		 * @return maximum value
		 */
		public double getMaxValue()
		{
			return maxValue;
		}
	}

	public static void main(String[] args) throws FormatException, IOException
	{
		final Options options = new Options(args);

		String imF = options.getField();
		String fout = options.getOutput();

		int[] blockSize = options.getBlockSize();
		double[] subsample_factors = options.getSubsampleFactors();
		String convertType = options.convertType();
		double maxValue = options.getMaxValue();
				
		System.out.println( "block size: " + Arrays.toString( blockSize ));
//		System.out.println( "subsample_factors: " + Arrays.toString( subsample_factors ));

//		System.out.println( "m: " + s  );
//		convertLinear( f, s, m );
//		System.out.println( "s: " + s );
		
		
		int[][] permutation = null;
		ImagePlus baseIp = null;
		if( imF.endsWith( "nii" ))
		{
			permutation = new int[][]{{0,3},{1,3},{2,3}};
			try
			{
				baseIp =  NiftiIo.readNifti( new File( imF ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if( imF.endsWith( "nrrd" ))
		{
			// This will never work since the Nrrd_Reader can't handle 4d volumes, actually
			Nrrd_Reader nr = new Nrrd_Reader();
			File imFile = new File( imF );
			baseIp = nr.load( imFile.getParent(), imFile.getName());
		}
		else
		{
			baseIp = IJ.openImage( imF );
		}
		System.out.println("ip: " + baseIp);
		
		Img<FloatType> img = ImageJFunctions.convertFloat( baseIp );
		System.out.println("img: " + Util.printInterval(img));
		
		RandomAccessibleInterval<FloatType> imgToPermute;
		double[] spacing;
		if( subsample_factors != null )
		{
			boolean isDiscrete = true;
			long[] subsample_discretes = new long[ subsample_factors.length ];
			for( int i = 0; i < subsample_factors.length; i++ )
			{
				if( Math.abs( subsample_factors[i] % 1 ) > 0.0001 )
				{
					isDiscrete = false;
				}
				subsample_discretes[ i ] = (long) subsample_factors[ i ];
			}

			AffineTransform resamplingXform =  new AffineTransform( 4 );
			resamplingXform.set( subsample_factors[ 0 ], 0, 0 );
			resamplingXform.set( subsample_factors[ 1 ], 1, 1 );
			resamplingXform.set( subsample_factors[ 2 ], 2, 2 );

			if( isDiscrete )
			{
				imgToPermute = Views.subsample( img, subsample_discretes );
				
			}
			else
			{
				imgToPermute = Views.interval( Views.raster( RealViews.affine(
						Views.interpolate( Views.extendZero(img), new NLinearInterpolatorFactory<>()), 
						resamplingXform.inverse())),
						RenderUtil.transformInterval( resamplingXform.inverse(), img) );
			}
		}
		else
		{
			imgToPermute = img;
		}
		
		if( subsample_factors == null )
		{
			spacing = new double[]{
					baseIp.getCalibration().pixelWidth,
					baseIp.getCalibration().pixelHeight,
					baseIp.getCalibration().pixelDepth
			};
		}
		else
		{
			spacing = new double[]{
					subsample_factors[0] * baseIp.getCalibration().pixelWidth,
					subsample_factors[1] * baseIp.getCalibration().pixelHeight,
					subsample_factors[2] * baseIp.getCalibration().pixelDepth
			};	
		}

		System.out.println("img_2_perm: " + Util.printInterval(imgToPermute));
		
		RandomAccessibleInterval<FloatType> img_perm = Views.permute( Views.permute( Views.permute(imgToPermute, 0, 3 ), 1, 3 ), 2, 3 );
		System.out.println("img_perm: " + Util.printInterval(img_perm));
		
		if( convertType != null && !convertType.isEmpty() )
		{
			if( Double.isNaN( maxValue ))
			{
				maxValue = getMaxAbs( Views.iterable( img_perm ));
			}
			
			
			if ( convertType.toUpperCase().equals( SHORTTYPE ) ){
				ShortType t = new ShortType();
				final double m = getMultiplier( t, maxValue );
				write( convert(img_perm, m, t ), fout, blockSize, spacing, m );
				
			}
			else if ( convertType.toUpperCase().equals( BYTETYPE ) )
			{
				ByteType t = new ByteType();
				final double m = getMultiplier( t, maxValue );
				write( convert(img_perm, m, t ), fout, blockSize, spacing, m );
			}
		}
		else
		{
			write( img_perm, fout, blockSize, spacing, 1 );
		}
	}
	
	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> convert( 
			RandomAccessibleInterval<FloatType> img_perm, double m, T t )
	{

		RandomAccessibleInterval<T> write_me = Converters.convert(
				img_perm, 
				new Converter<FloatType, T>()
				{
					@Override
					public void convert(FloatType input, T output) {
						output.setReal( input.getRealDouble() * m );
					}
				}, 
				t.copy());
		
		return write_me;
	}
	
	public static <T extends NativeType<T>> void write( 
			RandomAccessibleInterval<T> write_me,
			String fout,
			int[] blockSize,
			double[] spacing,
			double m ) throws IOException
	{
		System.out.println("write dfield size: " + Util.printInterval( write_me ));
		
		N5Writer n5writer = new N5HDF5Writer( fout, blockSize );
		N5Utils.save( write_me, n5writer, "dfield", blockSize, new GzipCompression(5));

		n5writer.setAttribute("dfield", "spacing", spacing );
		n5writer.setAttribute("dfield", "multiplier", 1/m );

	}

	public static double getMultiplier( final RealType<?> t, final double valueAtMax )
	{
		return t.getMaxValue() / valueAtMax;
	}
	
	public static <F extends RealType<F>, I extends RealType<I> > void convertLinear( F src, I tgt, double m )
	{
		tgt.setReal( src.getRealDouble() * m );
	}
	
	public static <T extends RealType<T>> double getMaxAbs( IterableInterval<T> img )
	{
		double max = 0;
		Cursor<T> c = img.cursor();
		while( c.hasNext() )
		{
			double v = Math.abs( c.next().getRealDouble());
			if( v > max )
				max = v;
		}
		return max;
	}
	
}
