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
import java.util.regex.Pattern;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import process.RenderTransformed;
import sc.fiji.skeleton.SwcIO;
import sc.fiji.skeleton.SWCPoint;
import tracing.SNT;


public class TransformSwc
{

	@Parameter(names = {"-d", "--directory"}, description = "Directory containing skeletons", required = false )
	private String skeletonDirectory;

	@Parameter(names = {"--include"}, description = "Matching pattern: " +
			"Files not matching the given regular expression will be ignored.  Only relevant when passing a directory",
			required = false )
	private String includeMatcher;

	@Parameter(names = {"--exclude"}, description = "Exclusion pattern: "
			+ "Files matching the given regular expression will be ignored. Only relevant when pass a directory",
			required = false )
	private String excludeMatcher;

	@Parameter(names = {"--suffix"}, description = "Suffix applied to output files when passing a directory.",
			required = false )
	private String suffix = "_transformed";

	@Parameter(names = {"-s", "--skeleton"}, description = "Input skeletons. Can pass multiple skeletons",
		required = false )
	private List<String> skeletonPaths;

	@Parameter(names = {"-o", "--output"}, description = "Output skeleton file names. "
			+ "Must have one output for every input skeleton, and pass output"
			+ "file paths in the same order as the input sketons.",
			required = false )
	private List<String> outputSkeletonPaths;

	@Parameter(names = {"-t", "--transform"}, variableArity = true, description = "List of transforms.  "
			+ "Every transform that is passed will be applied to the skeletons in the order they are passed. "
			+ "-t 'inverse' <transform file path> applies the inverse of the given transform if possible." )
	private List<String> transforms;

//	@Parameter(names = {"-q", "--nThreads"}, description = "Number of threads" )
//	private int nThreads;

	@Parameter(names = {"-h", "--help"}, description = "Print this help message" )
	private boolean help;
	
	private transient JCommander jCommander;
	

	/*
	 * Reads the first argument as a swc file
	 * If the second argument is a directory, will rename the input and write to that directory,
	 * else, will treat the second argument as the path of the output.
	 */
	public static void main( String[] args )
	{
		TransformSwc transformer = parseCommandLineArgs( args );
		if( args.length == 0 || transformer.help )
		{
			transformer.jCommander.usage();
			return;
		}
		transformer.run();
	}

	public static TransformSwc parseCommandLineArgs( final String[] args )
	{
		TransformSwc ob = new TransformSwc();
		ob.initCommander();
		try
		{
			ob.jCommander.parse( args );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
		return ob;
	}

	private void initCommander()
	{
		jCommander = new JCommander( this );
		jCommander.setProgramName( "input parser" );
	}

	public void run()
	{

		if ( skeletonDirectory != null && !skeletonDirectory.isEmpty() )
		{
			populateSkeletonListFromDirectory();
		}

		if ( skeletonPaths.size() != outputSkeletonPaths.size() )
		{
			System.err.println( "Must have the same number of input and output skeleton arguments" );
			return;
		}

		// parse Transform
		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		if ( transforms == null )
		{
			totalXfm.add( new AffineTransform3D() );
		}

		int i = 0;
		while( transforms != null && i < transforms.size() )
		{
			boolean invert = false;
			if( transforms.get( i ).toLowerCase().trim().equals( "inverse" ))
			{
				invert = true;
				i++;
			}
			
			if( invert )
				System.out.println( "loading transform from " + transforms.get( i ) + " AND INVERTING" );
			else
				System.out.println( "loading transform from " + transforms.get( i ));
			
			InvertibleRealTransform xfm = null;
			try
			{
				xfm = RenderTransformed.loadTransform( transforms.get( i ), invert );
			} catch ( Exception e )
			{
				e.printStackTrace();
			}
			
			if( xfm == null )
			{
				System.err.println("  failed to load transform ");
				System.exit( 1 );
			}

			totalXfm.add( xfm );
			i++;
		}
	

		i = 0;
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

	public static ArrayList<SWCPoint> transformSWC( InvertibleRealTransform xfm, ArrayList<SWCPoint> pts )
	{
		ArrayList<SWCPoint> out = new ArrayList<SWCPoint>();
		pts.forEach( pt -> out.add( transform( xfm, pt )) );
		return out;
	}
	
	public static SWCPoint transform( InvertibleRealTransform xfm, SWCPoint pt )
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
