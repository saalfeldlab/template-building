package evaluation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.swc.Swc;
import org.janelia.saalfeldlab.swc.SwcPoint;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.1.1-SNAPSHOT" )
public class SwcProcess implements Callable<Void>
{

	@Option(names = {"-s"}, description = "Input skeletons. You can provide multiple -s options" )
	private List<String> skeletonPaths;

	@Option(names = {"-o"}, description = "Output skeletons. You can  " )
	private List<String> outputSkeletonPaths;

	@Option(names = {"-c"}, description = "Coordinate scaling", split=",")
	private double[] coordinateScaling;

	@Option(names = {"-r"}, description = "Radius scaling", split="," )
	private double radiusScaling = 1.0;

	@Option(names = {"--set-radius"}, description = "Set radius. "
			+ "The radius at every point will be set to this value. "
			+ "This takes precedence over radius scaling." )
	private double radiusValue = Double.NaN;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Print this help message" )
	private boolean help;
	

	/*
	 * Reads the first argument as a swc file
	 * If the second argument is a directory, will rename the input and write to that directory,
	 * else, will treat the second argument as the path of the output.
	 */
	public static void main( String[] args )
	{
		CommandLine.call( new SwcProcess(), args );
	}

	public Void call()
	{
		if( skeletonPaths.size() != outputSkeletonPaths.size() )
		{
			System.err.println("Must have the same number of input and output skeleton arguments");
			return null;
		}
		
		int i = 0;
		while( i < skeletonPaths.size())
		{
			final File in = new File( skeletonPaths.get( i ));
			final File out = new File( outputSkeletonPaths.get( i ));
			
			Swc current = Swc.read( in );

			Swc res = current; 
			if ( coordinateScaling != null )
				current = scale( current, coordinateScaling );

			if( Double.isNaN( radiusValue ))
			{
				if ( radiusScaling != 1.0 )
					res = scaleRadius( current, radiusScaling );
				else
					res = current;
			}
			else
			{
				res = setRadius( current, radiusValue );
			}

			System.out.println( "Exporting to " + out );
			Swc.write( res, out );

			i++;
		}
		return null;
	}

	public static Swc setRadius( Swc pts, double value )
	{
		ArrayList<SwcPoint> out = new ArrayList<SwcPoint>();
		pts.getPoints().forEach( pt -> out.add( setRadius( pt, value )) );
		return new Swc( out );
	}

	public static SwcPoint setRadius( SwcPoint pt, double value )
	{
		return pt.setRadius( value );
	}

	public static Swc scaleRadius( Swc pts, double scale )
	{
		ArrayList<SwcPoint> out = new ArrayList<SwcPoint>();
		pts.getPoints().forEach( pt -> out.add( scaleRadius( pt, scale )) );
		return new Swc( out );
	}

	public static SwcPoint scaleRadius( SwcPoint pt, double scale )
	{
		return pt.setRadius( pt.radius * scale );
	}

	public static Swc scale( Swc pts, double[] scale )
	{
		ArrayList<SwcPoint> out = new ArrayList<SwcPoint>();
		pts.getPoints().forEach( pt -> out.add( scale( pt, scale )) );
		return new Swc( out );
	}
	
	public static SwcPoint scale( SwcPoint pt, double[] scale )
	{
		double[] p = new double[] { 
				scale[0] * pt.x,
				scale[1] * pt.y,
				scale[2] * pt.z };

		return pt.setPosition( p[0], p[1], p[2] );
	}
}
