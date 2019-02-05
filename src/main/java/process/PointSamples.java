package process;

import org.janelia.utility.parse.ParseUtils;

import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealPoint;
import net.imglib2.iterator.IntervalIterator;

public class PointSamples
{

	public static void main( String[] args )
	{

		String[] minmax = args[ 0 ].split( ":" );
		double[] min = ParseUtils.parseDoubleArray( minmax[ 0 ] );
		double[] max = ParseUtils.parseDoubleArray( minmax[ 1 ] );
		FinalRealInterval realInterval = new FinalRealInterval( min, max );

		double[] spacing = ParseUtils.parseDoubleArray( args[ 1 ] );

		int nd = realInterval.numDimensions();

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
					str.append( "," );

			}
			System.out.println( str.toString() );

		}
	}

}
