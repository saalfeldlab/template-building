package process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.janelia.utility.parse.ParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import loci.formats.FormatException;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransformSequence;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * 
 * @author John Bogovic
 *
 */
@Command( version = "0.0.2-SNAPSHOT" )
public class TransformPoints implements Callable<Void>
{

	@Option( names = { "-i", "--input" }, required = true, description = "Image file to transform." )
	private String input;

	@Option( names = { "-o", "--output" }, required = false, description = "Output file. Prints to standard out if no output argument given." )
	private String output;

	@Option( names = { "-t", "--transform" }, required = false, description = "Transformation file." )
	private List< String > transformFiles = new ArrayList<>();

	@Option( names = { "-d", "--delimeter" }, required = false, description = "Delimeter separating coordinates (default=\"${DEFAULT-VALUE}\")." )
	private String delimeter = ",";

	@Option( names = { "--header" }, required = false, description = "Number of header lines." )
	private int numHeaderLines = 0;

//	@Option( names = { "-j", "--nThreads" }, required = false, description = "Number of rendering threads (default=1)" )
//	private int nThreads = 1;

	@Option( names = { "-v", "--version" }, required = false, versionHelp = true, description = "Prints version information and exits." )
	private boolean version;
	
	private RealTransformSequence totalTransform;

	final Logger logger = LoggerFactory.getLogger( TransformPoints.class );

	public static void main( String[] args ) throws FormatException, Exception
	{
		CommandLine.call( new TransformPoints(), args );
	}

	public void setup()
	{
		totalTransform = TransformReader.readTransforms( transformFiles );
	}

	public Void call()
	{
		setup();

		List< String > lines;
		try
		{
			lines = Files.readAllLines( Paths.get( input ) );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
			return null;
		}

		double[] result = new double[ 3 ];

		int j = 0;
		List< String > linesout = new LinkedList< String >();
		for ( String line : lines )
		{
			if ( j < numHeaderLines )
			{
				linesout.add( line );
			}
			else
			{
				double[] src = ParseUtils.parseDoubleArray( line, delimeter );
				totalTransform.apply( src, result );
				linesout.add( result[ 0 ] + delimeter + result[ 1 ] + delimeter + result[ 2 ] );
			}

			j++;
		}
		try
		{
			Files.write( Paths.get( output ), linesout );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}

		return null;
	}	

}
