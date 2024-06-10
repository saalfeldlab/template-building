package io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;
import org.janelia.saalfeldlab.n5.universe.N5Factory;

import loci.formats.FormatException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Deprecated
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

	@Override
	public Void call() throws FormatException, IOException
	{
		final AffineTransform3D affineXfm = loadAffine( affine, false );

		final N5Writer n5Writer = new N5Factory().hdf5DefaultBlockSize(blockSizeDefault).openWriter(output);
		System.out.println(n5Writer.getClass());

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

		final DfieldIoHelper dfIo = new DfieldIoHelper();
		RandomAccessibleInterval<FloatType> img;
		try {
			img = dfIo.readAsRai(imF, new FloatType());
		} catch (final Exception e) {
			System.err.println("Could not read displacement field from: " + imF);
			return;
		}
		final double[] spacing = dfIo.spacing;


		final Compression compression = new GzipCompression();
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
		catch ( final Exception e )
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
				final AffineTransform3D xfm = ANTSLoadAffine.loadAffine( filePath );
				if( invert )
				{
					return xfm.inverse().copy();
				}
				return xfm;
			} catch ( final IOException e )
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
					final AffineTransform3D xfm = ANTSLoadAffine.loadAffine( filePath );
					if( invert )
					{
						return xfm.inverse().copy();
					}
					return xfm;
				} catch ( final IOException e )
				{
					e.printStackTrace();
				}
			}
		}
		return null;
	}

}
