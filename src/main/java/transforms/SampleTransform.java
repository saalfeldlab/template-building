package transforms;

import net.imglib2.Interval;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.util.Intervals;

/**
 * Draws samples of source and target points from an InvertibleRealTransform
 * using a grid of points.
 * 
 * @author John Bogovic
 *
 */
public class SampleTransform
{
	final long N; // num samples
	final Interval interval;
	final InvertibleRealTransform xfm;

	private double[][] p;
	private double[][] q;

	public SampleTransform( Interval interval, final InvertibleRealTransform xfm )
	{
		this.interval = interval;
		this.N = Intervals.numElements( interval );

		this.xfm = xfm;
	}

	public double[][] getP()
	{
		return p;
	}

	public double[][] getQ()
	{
		return q;
	}

	/**
	 * Allocates a new p and q array. This
	 */
	public void sample()
	{
		int nd = interval.numDimensions();

		// the order in which mpicbg expects
		p = new double[ nd ][ (int) N ];
		q = new double[ nd ][ (int) N ];

		double[] src = new double[ nd ];
		double[] tgt = new double[ nd ];

		int i = 0;
		IntervalIterator it = new IntervalIterator( interval );
		while ( it.hasNext() )
		{
			it.fwd();
			it.localize( src );
			xfm.apply( src, tgt );

			for ( int d = 0; d < nd; d++ )
			{
				p[ d ][ i ] = src[ d ];
				q[ d ][ i ] = tgt[ d ];
			}
			i++;
		}
	}
}
