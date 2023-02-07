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
public class SwcDifference implements Callable<Void>
{

	@Option(names = {"-a"}, description = "Path to first skeleton.", required = true )
	private String skeletonA;

	@Option(names = {"-b"}, description = "Path to second skeleton.", required = true )
	private String skeletonB;

	@Option(names = {"-o"}, description = "Output skeleton." )
	private String outputSkeletonPath;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Print this help message" )
	private boolean help;

	/*
	 * Reads the first argument as a swc file
	 * If the second argument is a directory, will rename the input and write to that directory,
	 * else, will treat the second argument as the path of the output.
	 */
	public static void main( String[] args )
	{
		CommandLine.call( new SwcDifference(), args );
	}

	public Void call()
	{
		if( skeletonA.isEmpty() || skeletonB.isEmpty() )
		{
			System.err.println("Must have the same number of input and output skeleton arguments");
			return null;
		}
		
		final File af = new File( skeletonA );
		final File bf = new File( skeletonB );
		final File out = new File( outputSkeletonPath );
		
		final Swc a = Swc.read( af );
		final Swc b = Swc.read( bf );
		final Swc o = new Swc();

		final List< SwcPoint > apts = a.getPoints();
		final List< SwcPoint > bpts = b.getPoints();
		
		double meanDist = 0;
		double maxDist = 0;
		for( int i = 0; i < apts.size(); i++ )
		{
			final SwcPoint ap = apts.get( i );
			final SwcPoint bp = bpts.get( i );
			final double xd = ap.x - bp.x;
			final double yd = ap.y - bp.y;
			final double zd = ap.z - bp.z;
			final double mag = Math.sqrt((xd * xd) + (yd * yd) + (zd * zd));

			o.add( new SwcPoint( ap.id, ap.type,
					xd, yd, zd, mag, 
					ap.previous ) );
			
			meanDist += mag;

			if( mag > maxDist )
				maxDist = mag;
		}

		meanDist /= apts.size();

		System.out.println( "mean dist: " + meanDist );
		System.out.println( "max dist : " + maxDist );

		System.out.println( "Exporting to " + out );
		Swc.write( o, out );

		return null;
	}
}
