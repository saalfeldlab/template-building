package io;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;
import org.janelia.utility.parse.ParseUtils;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import sc.fiji.io.Dfield_Nrrd_Reader;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import process.TransformImage;

@Command( version = "0.2.0-SNAPSHOT" )
public class WriteH5Transform implements Callable<Void>
{

	public static final String SHORTTYPE = "SHORT";
	public static final String BYTETYPE = "BYTE";
	public static final String FLOATTYPE = "FLOAT";
	public static final String DOUBLETYPE = "DOUBLE";


	@Option( names = { "-d",  "--dfield" }, required = true)
	private String field;

	@Option( names = { "-a",  "--affine" }, required = false)
	private String affine;

	@Option( names = { "-o",  "--output" }, required = true)
	private String output;

	@Option( names = { "-i",  "--invDfield" }, required = false)
	private String invDfield;

	@Option( names = { "-t",  "--type" }, required = false)
	private String convertType = "";

	@Option( names = { "-b",  "--blockSize" }, split=",", required = false)
	private int[] blockSize;

	@Option( names = { "-m",  "--maxValue" }, required = false)
	private double maxValue = Double.NaN;

	@Option( names = { "-e",  "--maxErr" }, required = false)
	private double maxErr = Double.NaN;

	private int[] blockSizeDefault = new int[] { 3, 32, 32, 32 };

	public static void main(String[] args) throws FormatException, IOException
	{
		new CommandLine( new WriteH5Transform()).execute(args);
		System.exit(0);	
	}

	public Void call() throws FormatException, IOException
	{
		AffineTransform3D affineXfm = loadAffine( affine, false );
	
		N5Writer n5Writer = new N5HDF5Writer( output, blockSize );
		write( field, n5Writer, "dfield", blockSize, convertType, maxErr, affineXfm );

		if( invDfield != null )
			write( invDfield, n5Writer, "invdfield", blockSize, convertType, maxErr, affineXfm.inverse() );

		return null;
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
			Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
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
				N5DisplacementField.save( n5Writer, dataset, affineXfm, img, spacing, blockSize, compression, new ShortType(), maxErr );
			}
			else if ( convertType.toUpperCase().equals( BYTETYPE ) )
			{
				N5DisplacementField.save( n5Writer, dataset, affineXfm, img, spacing, blockSize, compression, new ByteType(), maxErr );
			}
			else
			{
				N5DisplacementField.save( n5Writer, dataset, affineXfm, img, spacing, blockSize, compression );
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
					return xfm.inverse().copy();
				}
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
						return xfm.inverse().copy();
					}
					return xfm;
				} catch ( IOException e )
				{
					e.printStackTrace();
				}
			}
		}
		return null;
	}

}
