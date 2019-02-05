package process;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.janelia.utility.parse.ParseUtils;

import loci.formats.FormatException;

public class PointDifference
{

	public static void main( String[] args ) throws FormatException, Exception
	{

		String pts1 = args[ 0 ];
		String pts2 = args[ 1 ];
		String fout = args[ 2 ];

		String delimeter = " ";
		int numHeaderLines = 0;
		boolean doMagnitude = false;
		
		int i = 3;
		while( i < args.length )
		{

			if( args[ i ].equals( "-d" ))
			{
				i++;
				delimeter = args[ i ];
				i++;
				System.out.println( "argument specifies delimeter " + delimeter );
				continue;
			}
			else if( args[ i ].equals( "-h" ))
			{
				i++;
				numHeaderLines = Integer.parseInt( args[ i ] );
				i++;
				System.out.println( "argument specifies header lines " + numHeaderLines );
				continue;
			}
			else if( args[ i ].equals( "--mag" ) | args[ i ].equals( "-m" ))
			{
				doMagnitude = true;
				System.out.println( "argument specifies compute magnitude ");
				i++;
			}
		}

		List< String > lines1 = Files.readAllLines( Paths.get( pts1 ) );
		List< String > lines2 = Files.readAllLines( Paths.get( pts2 ) );
		
		List< String > linesout = new LinkedList< String >();
		for( int j = 0; j < lines1.size(); j++ )
		{
			if ( j < numHeaderLines )
			{
				continue;
			}

			String line1 = lines1.get( j );
			String line2 = lines2.get( j );

			double[] p = ParseUtils.parseDoubleArray( line1, delimeter );
			double[] q = ParseUtils.parseDoubleArray( line2, delimeter );

			double[] diff = diff( p, q );

			if( doMagnitude )
				linesout.add( "" + mag( diff ));
			else
				linesout.add( Arrays.toString( diff ));
		}
		Files.write( Paths.get( fout ), linesout );
	}
	
	public static double[] diff( final double[] p, final double[] q )
	{
		int nd = Math.min( p.length, q.length );
		double[] out = new double[ nd ];
		for( int i = 0; i < nd; i++ )
			out[ i ] = p[ i ] - q[ i ];

		return out;
	}

	public static double mag( final double[] p )
	{
		double mag = 0;
		for( int i = 0; i < p.length; i++ )
			mag += p[i]*p[i];
	
		return Math.sqrt( mag );
	}
}
