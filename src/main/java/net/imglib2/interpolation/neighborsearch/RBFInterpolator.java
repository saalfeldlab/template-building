package net.imglib2.interpolation.neighborsearch;

import java.util.function.DoubleUnaryOperator;

import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.Sampler;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.neighborsearch.RadiusNeighborSearch;
import net.imglib2.type.numeric.RealType;

/**
 * A radial basis function interpolator.
 * @author John Bogovic
 *
 * @param <T>
 */
public class RBFInterpolator<T extends RealType<T>> extends RealPoint implements RealRandomAccess< T >
{
	final static protected double minThreshold = Double.MIN_VALUE * 1000;

	final protected RadiusNeighborSearch< T > search;

	final T value;
	
	final double searchRadius;

	final DoubleUnaryOperator rbf;  // from squaredDistance to weight
	
	final boolean normalize;
	
	public RBFInterpolator(
			final RadiusNeighborSearch< T > search, 
			final DoubleUnaryOperator rbf, 
			final double searchRadius,
			final boolean normalize,
			T t )
	{
		super( search.numDimensions() );

		this.rbf = rbf;
		this.search = search;
		this.normalize = normalize;
		this.searchRadius = searchRadius;

		this.value = t.copy();
	}

	@Override
	public T get()
	{
		// TODO This method is not thread safe at the moment
		search.search( this, searchRadius, false );

		if ( search.numNeighbors() == 0 )
			value.setZero();
		else
		{
			double sumIntensity = 0;
			double sumWeights = 0;

			for ( int i = 0; i < search.numNeighbors(); ++i )
			{
				final Sampler< T > sampler = search.getSampler( i );

				if ( sampler == null )
					break;

				final T t = sampler.get();
				final double weight = rbf.applyAsDouble( search.getSquareDistance( i ) );

				if( normalize )
					sumWeights += weight;

				sumIntensity += t.getRealDouble() * weight;
			}

			if( normalize )
				value.setReal( sumIntensity / sumWeights );
			else
				value.setReal( sumIntensity );
		}

		return value;
	}

	@Override
	public RBFInterpolator< T > copy()
	{
		return new RBFInterpolator< T >( search,
				rbf, searchRadius, normalize, value );
	}

	@Override
	public RBFInterpolator< T > copyRealRandomAccess()
	{
		return copy();
	}

	public static class RBFInterpolatorFactory<T extends RealType<T>> implements InterpolatorFactory< T, RadiusNeighborSearch< T > >
	{
		final double searchRad;
		final DoubleUnaryOperator rbf;
		final boolean normalize;
		T val;

		public RBFInterpolatorFactory( 
				final DoubleUnaryOperator rbf,
				final double sr, 
				final boolean normalize, 
				T t )
		{
			this.searchRad = sr;
			this.rbf = rbf;
			this.normalize = normalize;
			this.val = t;
		}

		@Override
		public RBFInterpolator<T> create( final RadiusNeighborSearch< T > search )
		{
			return new RBFInterpolator<T>( search, rbf, searchRad, false, val );
		}

		@Override
		public RealRandomAccess< T > create( 
				final RadiusNeighborSearch< T > search,
				final RealInterval interval )
		{
			return create( search );
		}
	}
}
