package evaluation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.janelia.saalfeldlab.transform.io.TransformReader;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import sc.fiji.skeleton.SwcIO;
import sc.fiji.skeleton.SWCPoint;
import tracing.SNT;


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

	@Option(names = {"-h", "--help"}, description = "Print this help message" )
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

			ArrayList< SWCPoint > res = transformSWC( totalXfm, SwcIO.loadSWC( in ) );
			System.out.println( "Reading from: " + in );
			System.out.println( "Exporting to " + out );
			try
			{
				final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream( out ), "UTF-8"));
				flushSWCPoints( res, pw);
			}
			catch (final IOException ioe)
			{
				System.err.println("Saving to " + out + " failed");
			}
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

	public static void flushSWCPoints(final List<SWCPoint> swcPoints, final PrintWriter pw) {
		pw.println("# Exported from \"Simple Neurite Tracer\" version " + SNT.VERSION + " on "
				+ LocalDateTime.of(LocalDate.now(), LocalTime.now()));
		pw.println("# https://imagej.net/Simple_Neurite_Tracer");
		pw.println("#");
//		pw.println("# All positions and radii in " + spacing_units);
//		if (usingNonPhysicalUnits())
//			pw.println("# WARNING: Usage of pixel coordinates does not respect the SWC specification");
//		else
//			pw.println("# Voxel separation (x,y,z): " + x_spacing + ", " + y_spacing + ", " + z_spacing);
		pw.println("#");

		for (final SWCPoint p : swcPoints)
			p.println(pw);

		pw.close();
	}

	public static ArrayList<SWCPoint> transformSWC( RealTransform xfm, ArrayList<SWCPoint> pts )
	{
		ArrayList<SWCPoint> out = new ArrayList<SWCPoint>();
		pts.forEach( pt -> out.add( transform( xfm, pt )) );
		return out;
	}
	
	public static SWCPoint transform( RealTransform xfm, SWCPoint pt )
	{
		SwcIO.StringPrintWriter spw = new SwcIO.StringPrintWriter();
		//System.out.println( pt.toString() );
		pt.println( spw );
		
		
		String[] s = spw.toString().split( " " );
		int id = Integer.parseInt( s[ 0 ] );
		int type = Integer.parseInt( s[ 1 ] );
		double radius = Double.parseDouble( s[ 5 ] );
		int previous = Integer.parseInt( s[ 6 ] );
		
		double[] p = new double[] { 
				pt.getPointInImage().x,
				pt.getPointInImage().y,
				pt.getPointInImage().z };

		double[] pxfm = new double[ 3 ];
		xfm.apply( p, pxfm );

		return new SWCPoint( 
				id, type, 
				pxfm[ 0 ], pxfm[ 1 ], pxfm[ 2 ], 
				radius, previous );
	}

}
