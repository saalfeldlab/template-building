package process;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import loci.formats.FormatException;
import net.imglib2.realtransform.RealTransformSequence;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Transform a Wavefront obj file. 
 * Only changes vertex positions.
 * 
 * @author John Bogovic
 *
 */
@Command( version = "0.2.0-SNAPSHOT" )
public class TransformObj implements Callable<Void>
{

	@Option( names = { "-i", "--input" }, required = true, description = "Ob file to transform." )
	private String input;

	@Option( names = { "-o", "--output" }, required = false, description = "Output file. Prints to standard out if no output argument given." )
	private String output;

	@Option( names = { "-t", "--transform" }, required = false, description = "Transformation files." )
	private List< String > transformFiles = new ArrayList<>();

	@Option( names = { "-v", "--version" }, required = false, versionHelp = true, description = "Prints version information and exits." )
	private boolean version;

	private RealTransformSequence totalTransform;

	final Logger logger = LoggerFactory.getLogger( TransformObj.class );

	public static void main( String[] args ) throws FormatException, Exception
	{
		new CommandLine( new TransformObj() ).execute( args );
	}

	public void setup()
	{
		totalTransform = TransformReader.readTransforms( transformFiles );
	}

	public Void call()
	{
		setup();

		final double[] p = new double[ 3 ];
		final double[] pXfm = new double[ 3 ];

		Charset cs = Charset.defaultCharset();

		int j = 0;
		try ( final Stream< String > lines =  Files.newBufferedReader( Paths.get( input ) ).lines();
			  final PrintWriter pw = new PrintWriter(output, cs.name()))
		{
			lines.map( line -> 
			{
				if( line.startsWith( "v " ))
				{
					parseVertexLine( line, p );
					totalTransform.apply( p, pXfm );
					return vertexLine( pXfm );
				}
				else 
					return line;
			})
			.forEach( pw::println );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}

		return null;
	}

	public static void parseVertexLine( String line, double[] v )
	{
		String[] split = line.split( " " );
		if ( !split[ 0 ].equals( "v" ) )
		{
			Arrays.fill( v, Double.NaN );
			return;
		}

		for ( int i = 1; i < split.length; i++ )
			v[ i - 1 ] = Double.parseDouble( split[ i ] );
	}

	public static String vertexLine( final double[] v )
	{
		StringBuffer s = new StringBuffer();
		s.append( "v " );
		for ( int i = 0; i < v.length; i++ )
		{
			s.append( v[ i ] );
			if ( i < v.length - 1 )
				s.append( " " );
		}
		return s.toString();
	}

}
