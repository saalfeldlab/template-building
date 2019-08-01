package process;

import java.util.Arrays;
import java.util.concurrent.Callable;

import org.janelia.utility.parse.ParseUtils;

import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealPoint;
import net.imglib2.iterator.IntervalIterator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.1.1-SNAPSHOT" )
public class PointSamples implements Callable<Void>
{
	@Option( names = { "-l", "--min" }, required = false, split = ",", 
			description = "Lowest, or minimum coordinate. Default (0,..0)" )
	private double[] min;

	@Option( names = { "-h", "--max" }, required = true, split = ",", 
			description = "Highest, or maximum coordinate. Default (0,..0)" )
	private double[] max;

	@Option( names = { "-s", "--spacing" }, required = false, split = ",", 
			description = "Spacing. Default: (1,..1)" )
	private double[] spacing;

	@Option( names = { "-d", "--delimeter" }, required = false, description = "Delimeter" )
	private String delimiter = ",";

	public static void main( String[] args )
	{
		CommandLine.call( new PointSamples(), args );
	}

	/**
	 * Generates point samples.
	 * 
	 * java process.PointSamples
	 */
	public Void call()
	{
		int nd = max.length;
		
		if( min == null )
		{
			min = new double[ nd ];
		}
		
		if( spacing == null )
		{
			spacing = new double[ nd ];
			Arrays.fill( spacing, 1.0 );
		}

		FinalRealInterval realInterval = new FinalRealInterval( min, max );


		long[] sampledim = new long[ nd ];
		for ( int i = 0; i < nd; i++ )
		{
			sampledim[ i ] = Math.round( ( realInterval.realMax( i ) - realInterval.realMin( i ) ) / spacing[ i ] );
		}

		FinalInterval sampleInterval = new FinalInterval( sampledim );

		IntervalIterator it = new IntervalIterator( sampleInterval );
		RealPoint p = new RealPoint( nd );

		while ( it.hasNext() )
		{
			StringBuffer str = new StringBuffer();
			it.fwd();
			p.setPosition( it );
			for ( int i = 0; i < nd; i++ )
			{
				p.setPosition( realInterval.realMin( i ) + it.getDoublePosition( i ) * spacing[ i ], i );

				str.append( Double.toString( p.getDoublePosition( i ) ) );
				if ( i < nd - 1 )
					str.append( delimiter );

			}
			System.out.println( str.toString() );

		}

		return null;
	}

}
