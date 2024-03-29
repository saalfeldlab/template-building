package io;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.transform.io.TransformReader;

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
import net.imglib2.quantization.AbstractQuantizer;
import net.imglib2.quantization.GammaQuantizer;
import net.imglib2.quantization.LinearQuantizer;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import sc.fiji.io.Nrrd_Reader;
import util.RenderUtil;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.2.0-SNAPSHOT" )
public class WriteH5DisplacementField implements Callable<Void> {
	
	public static final String SHORTTYPE = "SHORT";
	public static final String BYTETYPE = "BYTE";

	@Option(names = { "-d", "--dfield" }, required = true)
	private String field;

	@Option(names = { "-a", "--affine" }, required = false)
	private String affine;

	@Option(names = { "-o", "--output" }, required = true)
	private String output;

	@Option(names = { "-i", "--inverse" }, required = false)
	private boolean isInverse = false;

	@Option(names = { "-f", "--factors" }, split = ",", required = false)
	private double[] subsampleFactors;

	@Option(names = { "-t", "--type" }, required = false)
	private String convertType;

	@Option(names = { "-b", "--blockSize" }, split = ",", required = false)
	private int[] blockSize;

	@Option(names = { "-m", "--maxValue" }, required = false)
	private double maxValue = Double.NaN;

	@Option(names = { "-g", "--gamma" }, required = false)
	private double gamma = Double.NaN;

	private int[] blockSizeDefault = new int[] { 3, 32, 32, 32 };

	public static void main(String[] args) 
	{
		new CommandLine(new WriteH5DisplacementField()).execute(args);
	}

	public Void call() throws FormatException, IOException
	{
//		String imF = options.getField();
//		String fout = options.getOutput();
//
//		int[] blockSize = options.getBlockSize();
//		double[] subsample_factors = options.getSubsampleFactors();
//		String convertType = options.convertType();
//		double maxValue = options.getMaxValue();
//		double gamma = options.getGamma();

		final double[] affineArr = loadAffine( affine );

		blockSize = blockSize == null ? blockSizeDefault : blockSize;
		System.out.println( "block size: " + Arrays.toString( blockSize ));

		int[][] permutation = null;
		ImagePlus baseIp = null;
		RandomAccessibleInterval< ? extends RealType > img = null;

		double[] initialRes = new double[] { 1, 1, 1 };
		if( field.endsWith( "nii" ))
		{
			permutation = new int[][]{{0,3},{1,3},{2,3}};
			try
			{
				baseIp =  NiftiIo.readNifti( new File( field ) );
				initialRes = new double[] {
						baseIp.getCalibration().pixelWidth,
						baseIp.getCalibration().pixelHeight,
						baseIp.getCalibration().pixelDepth };
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if( field.endsWith( "nrrd" ))
		{
			DfieldIoHelper io = new DfieldIoHelper();
			try
			{
				img = io.read( field );
				initialRes = io.spacing;
			}
			catch ( Exception e )
			{
				System.err.println( "Could not read : " + field );
				e.printStackTrace();
				return null;
			}
		}
		else
		{
			baseIp = IJ.openImage( field );
			initialRes = new double[] {
					baseIp.getCalibration().pixelWidth,
					baseIp.getCalibration().pixelHeight,
					baseIp.getCalibration().pixelDepth };
		}

		System.out.println("ip: " + baseIp);
		
		if( img == null )
			img = ImageJFunctions.convertFloat( baseIp );

		System.out.println("img: " + Util.printInterval(img));
		RandomAccessibleInterval imgToPermute;
		double[] spacing;
		if( subsampleFactors != null )
		{
			boolean isDiscrete = true;
			long[] subsample_discretes = new long[ subsampleFactors.length ];
			for( int i = 0; i < subsampleFactors.length; i++ )
			{
				if( Math.abs( subsampleFactors[i] % 1 ) > 0.0001 )
				{
					isDiscrete = false;
				}
				subsample_discretes[ i ] = (long) subsampleFactors[ i ];
			}

			AffineTransform resamplingXform =  new AffineTransform( 4 );
			resamplingXform.set( subsampleFactors[ 0 ], 0, 0 );
			resamplingXform.set( subsampleFactors[ 1 ], 1, 1 );
			resamplingXform.set( subsampleFactors[ 2 ], 2, 2 );

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
		
		if( subsampleFactors == null )
		{
			spacing = initialRes;
		}
		else
		{
			spacing = new double[]{
					subsampleFactors[0] * initialRes[0],
					subsampleFactors[1] * initialRes[1],
					subsampleFactors[2] * initialRes[2]
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

			if ( convertType.toUpperCase().equals( SHORTTYPE ))
			{
				ShortType t = new ShortType();
				AbstractQuantizer<FloatType, ShortType> quantizer =  getQuantizer( new FloatType(),t, maxValue, gamma );
				write( Converters.convert( img_perm, quantizer, t ), affineArr,
						output, blockSize, spacing, quantizer );
			}
			else if ( convertType.toUpperCase().equals( BYTETYPE ) )
			{
				ByteType t = new ByteType();
				AbstractQuantizer<FloatType, ByteType> quantizer =  getQuantizer( new FloatType(), t, maxValue, gamma );
				write( Converters.convert( img_perm, quantizer, t ), affineArr,
						output, blockSize, spacing, quantizer );
				
			}
		}
		else
		{
			write( img_perm, affineArr, output, blockSize, spacing, 1, 1 );
		}

		return null;
	}

	public static double[] loadAffine( String affinePath )
	{
		if( affinePath == null )
			return null;

		final TransformReader reader = new TransformReader();
		final RealTransform tform = reader.read( affinePath );
		if( tform instanceof AffineGet )
		{
			AffineGet a = (AffineGet)tform;
			return a.getRowPackedCopy();
		}
		System.err.println( "could not read " + affinePath + " as affine." );
		return null;
	}
	
	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> convertGamma( 
			RandomAccessibleInterval<FloatType> img_perm, double max, double gamma, T t )
	{
		return Converters.convert(
				img_perm, 
				new Converter<FloatType, T>()
				{
					@Override
					public void convert(FloatType input, T output)
					{
						output.setReal( Math.pow((input.getRealDouble() / max), gamma ) * t.getMaxValue() );
					}
				}, 
				t.copy());
	}

	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> convert( 
			RandomAccessibleInterval<FloatType> img_perm, double m, T t )
	{
		return Converters.convert(
			img_perm, 
			new Converter<FloatType, T>()
			{
				@Override
				public void convert(FloatType input, T output) {
					output.setReal( input.getRealDouble() * m );
				}
			}, 
			t.copy());
	}
	
	public static <T extends NativeType<T>> void write( 
			RandomAccessibleInterval<T> write_me,
			double[] affine,
			String fout,
			int[] blockSize,
			double[] spacing,
			AbstractQuantizer<?,?> quantizer ) throws IOException
	{
		System.out.println("write dfield size: " + Util.printInterval( write_me ));
		
		N5Writer n5writer = new N5HDF5Writer( fout, blockSize );
		N5Utils.save( write_me, n5writer, "dfield", blockSize, new GzipCompression(5));

		if( affine != null )
			n5writer.setAttribute("dfield", "affine", affine );

		n5writer.setAttribute("dfield", "spacing", spacing );
		
		Map< String, Double > qParams = quantizer.parameters();
		for( String k : qParams.keySet() )
			n5writer.setAttribute("dfield", k, qParams.get( k ));
		
//		n5writer.setAttribute("dfield", "maxIn", quantizer.maxIn );
//		n5writer.setAttribute("dfield", "maxOut", quantizer.maxOut );
//		n5writer.setAttribute("dfield", "multiplier", quantizer.m );
//		n5writer.setAttribute("dfield", "gamma", quantizer.gamma );

		n5writer.close();
	}
	
	public static <T extends NativeType<T>> void write( 
			RandomAccessibleInterval<T> write_me,
			double[] affine,
			String fout,
			int[] blockSize,
			double[] spacing,
			double m,
			double gamma ) throws IOException
	{
		System.out.println("write dfield size: " + Util.printInterval( write_me ));
		
		N5Writer n5writer = new N5HDF5Writer( fout, blockSize );
		N5Utils.save( write_me, n5writer, "dfield", blockSize, new GzipCompression(5));

		if( affine != null )
			n5writer.setAttribute("dfield", "affine", affine );

		n5writer.setAttribute("dfield", "spacing", spacing );
		n5writer.setAttribute("dfield", "multiplier", 1/m );
		n5writer.setAttribute("dfield", "gamma", gamma );

		n5writer.close();
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
	
	public static <S extends RealType<S>, T extends RealType<T>> AbstractQuantizer<S,T> getQuantizer( 
			S s, T t, double maxValue, double gamma )
	{
		if( Double.isNaN( gamma ) || gamma == 1  )
		{
			return new LinearQuantizer< S, T >( s, t, ( t.getMaxValue() / maxValue ) , 0 );
		}
		else
		{
			return new GammaQuantizer< S, T >( s, t, t.getMaxValue(), maxValue, gamma );
		}
	}

}
