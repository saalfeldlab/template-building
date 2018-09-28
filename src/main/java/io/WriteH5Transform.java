package io;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.utility.parse.ParseUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.quantization.AbstractQuantizer;
import net.imglib2.quantization.GammaQuantizer;
import net.imglib2.quantization.LinearQuantizer;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.transform.integer.MixedTransform;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import sc.fiji.io.Nrrd_Reader;
import util.RenderUtil;


public class WriteH5Transform {
	
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

//		@Option( name = "-i", aliases = {"--inverse"}, required = false, usage = "" )
//		private boolean isInverse = false;
		
		@Option( name = "-i", aliases = {"--invDfield"}, required = false, usage = "" )
		private String invDfield;
		
		@Option( name = "-t", aliases = {"--type"}, required = false, usage = "" )
		private String convertType = "";
		
		@Option( name = "-b", aliases = {"--blockSize"}, required = false, usage = "" )
		private String blockSizeArg;
		
		@Option( name = "-m", aliases = {"--maxValue"}, required = false, usage = "" )
		private double maxValue = Double.NaN;
		
		@Option( name = "-e", aliases = {"--maxErr"}, required = false, usage = "" )
		private double maxErr = Double.NaN;
		
		
		private int[] blockSizeDefault = new int[]{ 3, 32, 32, 32 };
//		private int[] blockSizeDefault = new int[]{ 32, 32, 32, 3 };

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

//		/**
//		 * @return is this an inverse transform
//		 */
//		public boolean isInverse()
//		{
//			return isInverse;
//		}
		
		/**
		 * @return is this an inverse transform
		 */
		public String convertType()
		{
			return convertType;
		}

		/**
		 * @return output path
		 */
		public String getOutput()
		{
			return output;
		}
		
		/**
		 * @return forward displacement field
		 */
		public String getField()
		{
			return field;
		}

		/**
		 * @return inverse displacement field
		 */
		public String getInverseField()
		{
			return invDfield;
		}
		
		/**
		 * @return affine path
		 */
		public String getAffine()
		{
			return affine;
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
		 * @return maximum value
		 */
		public double getMaxValue()
		{
			return maxValue;
		}
		
		/**
		 * @return maximum error
		 */
		public double getMaxError()
		{
			return maxErr;
		}
		
	}

	public static void main(String[] args) throws FormatException, IOException
	{
		final Options options = new Options(args);

		String imF = options.getField();
		String affineF = options.getAffine();
		String fout = options.getOutput();
		
//		boolean inv = options.isInverse;
		String invDfield = options.getInverseField();

		int[] blockSize = options.getBlockSize();
		String convertType = options.convertType();
		double maxValue = options.getMaxValue();
		double maxErr = options.getMaxError();
		
//		AffineTransform3D affineXfm = loadAffine( affineF, inv );
		AffineTransform3D affineXfm = loadAffine( affineF, false );


//		System.out.println( "block size: " + Arrays.toString( blockSize ));
//		System.out.println( "subsample_factors: " + Arrays.toString( subsample_factors ));

//		System.out.println( "m: " + s  );
//		convertLinear( f, s, m );
//		System.out.println( "s: " + s );
		
		
		N5Writer n5Writer = new N5HDF5Writer( fout, blockSize );
		write( imF, n5Writer, "dfield", blockSize, convertType, maxErr, affineXfm );

		if( invDfield != null )
			write( invDfield, n5Writer, "invdfield", blockSize, convertType, maxErr, affineXfm.inverse() );

	}
	
	public static void write(
			final String imF,
			final N5Writer n5Writer,
			final String dataset,
			final int[] blockSize,
			final String convertType,
			final double maxErr,
			final AffineTransform3D affineXfm )
	{
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
		double[] spacing = new double[]{ 
				baseIp.getCalibration().pixelWidth,
				baseIp.getCalibration().pixelHeight,
				baseIp.getCalibration().pixelDepth
		};
		
		Img< FloatType > img = ImageJFunctions.convertFloat( baseIp );
		
		Compression compression = new GzipCompression();
		try
		{

			if ( convertType.toUpperCase().equals( SHORTTYPE ))
			{
				N5DisplacementField.save( affineXfm, img, spacing, n5Writer, dataset, blockSize, compression, new ShortType(), maxErr );
			}
			else if ( convertType.toUpperCase().equals( BYTETYPE ) )
			{
				N5DisplacementField.save( affineXfm, img, spacing, n5Writer, dataset, blockSize, compression, new ByteType(), maxErr );
			}
			else
			{
				N5DisplacementField.save( affineXfm, img, spacing, n5Writer, dataset, blockSize, compression );
			}

		}
		catch ( Exception e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static AffineTransform3D loadAffine( final String filePath, boolean invert ) throws IOException
	{
		if( filePath.endsWith( "mat" ))
		{
			System.out.println("Reading mat transform file");
			try
			{
				AffineTransform3D xfm = ANTSLoadAffine.loadAffine( filePath );
				if( invert )
				{
//					System.out.println("inverting");
//					System.out.println( "xfm: " + xfm );
					return xfm.inverse().copy();
				}
				System.out.println( "xfm: " + xfm );
				return xfm;
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if( filePath.endsWith( "txt" ))
		{
			if( Files.readAllLines( Paths.get( filePath ) ).get( 0 ).startsWith( "#Insight Transform File" ))
			{
				System.out.println("Reading itk transform file");
				try
				{
					AffineTransform3D xfm = ANTSLoadAffine.loadAffine( filePath );
					if( invert )
					{
//						System.out.println("inverting");
						return xfm.inverse().copy();
					}
					return xfm;
				} catch ( IOException e )
				{
					e.printStackTrace();
				}
			}
			else
			{
//				System.out.println("Reading imglib2 transform file");
//				try
//				{
//					AffineTransform xfm = AffineImglib2IO.readXfm( 3, new File( filePath ) );
//					System.out.println( Arrays.toString(xfm.getRowPackedCopy() ));
//					if( invert )
//					{
////						System.out.println("inverting");
//						return xfm.inverse().copy();
//					}
//					return xfm;
//				} catch ( IOException e )
//				{
//					e.printStackTrace();
//				}
			}
		}
		return null;
	}

}
