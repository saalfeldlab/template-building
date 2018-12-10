package net.imglib2.realtransform.cpd;

import jitk.spline.XfmUtils;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.InvertibleRealTransform;

public class CoherentPointDriftTransform implements InvertibleRealTransform
{

	final int ndims;
	final double[][] W; // weight matrix
	final double[][] pts; // knots
	final double sigma;
	final double[] mean;
	final double scale;
	final double[] ymean;
	final double yscale;

	final int nKnots;

	public CoherentPointDriftTransform( final int ndims, final double[][] W,
			final double[][] pts, final double sigma, final double[] ymean,
			final double yscale, final double[] mean, final double scale )
	{
		this.ndims = ndims;
		this.W = W;
		this.pts = normalizePointsIfNecessary( pts, ymean, yscale );
		this.sigma = sigma;
		this.mean = mean;
		this.scale = scale;
		this.ymean = ymean;
		this.yscale = yscale;

		nKnots = pts[ 0 ].length;
	}

	public static double[][] normalizePointsIfNecessary( final double[][] pts,
			final double[] mean, final double scale )
	{
		if ( mean == null )
			return pts;

		int nd = pts.length;
		int np = pts[ 0 ].length;
		double[][] out = new double[ nd ][ np ];

		for ( int i = 0; i < np; i++ )
			for ( int d = 0; d < nd; d++ )
				out[ d ][ i ] = (pts[ d ][ i ] - mean[ d ]) / scale;

		return out;
	}

	public void computeDeformation( final double[] thispt, final double[] result )
	{

		final double[] tmpDisplacement = new double[ ndims ];
		for ( int i = 0; i < ndims; ++i )
		{
			result[ i ] = 0;
			tmpDisplacement[ i ] = 0;
		}

		for ( int lnd = 0; lnd < nKnots; lnd++ )
		{
			displacement( lnd, thispt, tmpDisplacement );

			final double gaussianAffinity = gaussian( normSqrd( tmpDisplacement ), sigma );
			for ( int d = 0; d < ndims; d++ )
				result[ d ] += gaussianAffinity * W[ d ][ lnd ];

		}
	}

	public double gaussian( double displacement_squared, double sigma )
	{
		return Math.exp( -displacement_squared / (2 * sigma * sigma) );
	}

	/**
	 * Computes the displacement between the i^th source point and the input
	 * point.
	 *
	 * Stores the result in the input array 'res'. Does not validate inputs.
	 * 
	 * @param i index
	 * @param pt point
	 * @param res result
	 */
	protected void displacement( final int i, final double[] pt, final double[] res )
	{
		for ( int d = 0; d < ndims; d++ )
		{
			res[ d ] = pts[ d ][ i ] - pt[ d ];
		}
	}

	protected double normSqrd( final double[] v )
	{
		double nrm = 0;
		for ( int i = 0; i < v.length; i++ )
		{
			nrm += v[ i ] * v[ i ];
		}
		return nrm;
	}

	@Override
	public int numSourceDimensions()
	{
		return ndims;
	}

	@Override
	public int numTargetDimensions()
	{
		return ndims;
	}

	@Override
	public void apply( double[] source, double[] target )
	{
		// System.out.println( "W:\n" + XfmUtils.printArray( W ));
		// System.out.println( "pts:\n" + XfmUtils.printArray( this.pts ));

		double[] sourceToUse;
		if ( mean != null )
		{
			// normalize
			sourceToUse = new double[ source.length ];
			for ( int d = 0; d < numSourceDimensions(); d++ )
				sourceToUse[ d ] = (source[ d ] - ymean[ d ]) / yscale;
		} else
			sourceToUse = source;

		// System.out.println( "src after nrm:\n" + XfmUtils.printArray(
		// sourceToUse ));

		computeDeformation( sourceToUse, target );
		for ( int d = 0; d < numSourceDimensions(); d++ )
			target[ d ] += sourceToUse[ d ];

		// System.out.println( "res raw:\n" + XfmUtils.printArray( target ));

		// un-normalize
		if ( mean != null )
		{
			for ( int d = 0; d < numSourceDimensions(); d++ )
				target[ d ] = (scale * target[ d ]) + mean[ d ];
		}

		// System.out.println( "res after nrm:\n" + XfmUtils.printArray( target
		// ));
	}

	@Override
	public void apply( float[] source, float[] target )
	{
		double[] srcd = new double[ source.length ];
		XfmUtils.copy( source, srcd );
		double[] outd = applyCreate( srcd );
		XfmUtils.copy( outd, target );
	}

	public double[] applyCreate( double[] source )
	{
		double[] out = new double[ source.length ];
		apply( source, out );
		return out;
	}

	@Override
	public void apply( RealLocalizable source, RealPositionable target )
	{
		double[] src = new double[ source.numDimensions() ];
		double[] tgt = new double[ target.numDimensions() ];

		source.localize( src );
		apply( src, tgt );
		target.setPosition( tgt );
	}

	@Override
	public InvertibleRealTransform copy()
	{
		// returning *this* is okay since this object is immutable.
		return this;
	}

	@Override
	public void applyInverse( double[] source, double[] target )
	{
		apply( source, target );
	}

	@Override
	public void applyInverse( float[] source, float[] target )
	{
		apply( source, target );
	}

	@Override
	public void applyInverse( RealPositionable source, RealLocalizable target )
	{
		apply( target, source );
	}

	@Override
	public InvertibleRealTransform inverse()
	{
		// TODO Auto-generated method stub
		return null;
	}

}
