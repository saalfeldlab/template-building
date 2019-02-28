package evaluation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.swc.Swc;
import org.janelia.saalfeldlab.swc.SwcPoint;
import org.janelia.saalfeldlab.transform.io.TransformReader;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;


@Command( version = "0.0.2-SNAPSHOT" )
public class TransformSwc implements Callable< Void >
{

	@Option(names = {"-d", "--directory"}, description = "Directory containing skeletons", required = false )
	private String skeletonDirectory;

	@Option(names = {"--include"}, description = "Matching pattern: " +
			"Files not matching the given regular expression will be ignored.  Only relevant when passing a directory",
			required = false )
	private String includeMatcher;

	@Option(names = {"--exclude"}, description = "Exclusion pattern: "
			+ "Files matching the given regular expression will be ignored. Only relevant when pass a directory",
			required = false )
	private String excludeMatcher;

	@Option(names = {"--suffix"}, description = "Suffix applied to output files when passing a directory.",
			required = false )
	private String suffix = "_transformed";

	@Option(names = {"-s", "--skeleton"}, description = "Input skeletons. Can pass multiple skeletons",
		required = false )
	private List<String> skeletonPaths;

	@Option(names = {"-o", "--output"}, description = "Output skeleton file names. "
			+ "Must have one output for every input skeleton, and pass output"
			+ "file paths in the same order as the input sketons.",
			required = false )
	private List<String> outputSkeletonPaths;

	@Option(names = {"-t", "--transform"},
			description = "List of transforms.  "
			+ "Every transform that is passed will be applied to the skeletons in the order they are passed. "
			+ "-t <transform file path> applies the inverse of the given transform if possible." )
	private List<String> transforms;

	@Option( names = { "-v", "--version" }, required = false, versionHelp = true, description = "Prints version information and exits." )
	private boolean version;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Print this help message" )
	private boolean help;
	

	/*
	 * Reads the first argument as a swc file
	 * If the second argument is a directory, will rename the input and write to that directory,
	 * else, will treat the second argument as the path of the output.
	 */
	public static void main( String[] args )
	{
		CommandLine.call( new TransformSwc(), args );
		System.exit(0);
	}

	public Void call()
	{

		if ( skeletonDirectory != null && !skeletonDirectory.isEmpty() )
		{
			populateSkeletonListFromDirectory();
		}

		if ( skeletonPaths.size() != outputSkeletonPaths.size() )
		{
			System.err.println( "Must have the same number of input and output skeleton arguments" );
			return null;
		}

		// parse Transform
		// Concatenate all the transforms
		RealTransformSequence totalXfm = new RealTransformSequence();
		if ( transforms == null )
			totalXfm.add( new AffineTransform3D() );
		else
			totalXfm = TransformReader.readTransforms( transforms );

		int i = 0;
		while ( i < skeletonPaths.size() )
		{
			final File in = new File( skeletonPaths.get( i ) );
			final File out = new File( outputSkeletonPaths.get( i ) );

			if ( !in.exists() )
			{
				System.err.println( "input file does not exist: " + in );
				i++;
				continue;
			}

			if ( out.exists() )
			{
				System.err.println( "output file does already exists, skipping: " + in );
				i++;
				continue;
			}

			Swc res = transformSWC( totalXfm, Swc.read( in ));
			System.out.println( "Reading from: " + in );
			System.out.println( "Exporting to " + out );
			Swc.write( res, out );
			System.out.println( " " );

			i++;
		}

		return null;
	}

	public void populateSkeletonListFromDirectory()
	{
		skeletonPaths = new ArrayList< String >();
		outputSkeletonPaths = new ArrayList< String >();

		File dir = new File( skeletonDirectory );

		if ( !dir.isDirectory() )
			System.err.println( "Not a directory: " + skeletonDirectory );

		String[] fullFileList = dir.list();
		for ( String f : fullFileList )
		{
			if ( f.endsWith( "swc" ) )
			{
				if ( includeMatcher != null && !includeMatcher.isEmpty() )
				{
					if ( !Pattern.matches( includeMatcher, f ) )
						continue;
				}

				if ( excludeMatcher != null && !excludeMatcher.isEmpty() )
				{
					if ( Pattern.matches( excludeMatcher, f ) )
						continue;
				}

				skeletonPaths.add( dir + File.separator + f );
				outputSkeletonPaths.add( dir + File.separator + f.replaceAll( ".swc$", suffix + ".swc" ) );
			}
		}
	}

	public static Swc transformSWC( final RealTransform xfm, final Swc swc )
	{
		ArrayList<SwcPoint> out = new ArrayList<>();
		swc.getPoints().forEach( pt -> out.add( transform( xfm, pt )) );
		return new Swc( out );
	}
	
	public static SwcPoint transform( RealTransform xfm, SwcPoint pt )
	{
		double[] p = new double[] { pt.x, pt.y, pt.z }; 
		double[] pxfm = new double[ 3 ];
		xfm.apply( p, pxfm );

		// a copy of pt with the new position
		return pt.setPosition( pxfm[ 0 ], pxfm[ 1 ], pxfm[ 2 ] );
	}

}
