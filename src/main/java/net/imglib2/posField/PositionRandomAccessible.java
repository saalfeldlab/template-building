package net.imglib2.posField;

import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RealPoint;
import net.imglib2.Sampler;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.RealType;

public class PositionRandomAccessible<T extends RealType< T >, X extends RealTransform >
		implements RandomAccessible< T >
{
	protected final int nd;
	protected final T t;

	protected X xfm;
	protected final RealPoint pt;
	protected final int dim;

	public PositionRandomAccessible( int nd, T t, final X xfm, int dim )
	{
		this.nd = nd + 1;
		this.t = t;
		this.xfm = xfm;
		this.dim = dim;
		t.setZero();
		pt = new RealPoint( nd );
	}

	public PositionRandomAccessible( int nd, T t )
	{
		this( nd, t, null, -1 );
	}

	public class PositionRandomAccess< S extends RealTransform > extends Point implements RandomAccess< T >
	{

		protected S xfm;
		protected final RealPoint pt;
		protected final int dim;

		public PositionRandomAccess( int nd, S xfm, int dim )
		{
			super( nd );
			this.xfm = xfm;
			this.dim = dim;
			pt = new RealPoint( nd );
		}

		@Override
		public T get()
		{
			int d = dim;

			if( d < 0 )
				d = this.getIntPosition( this.numDimensions() - 1 );

			T result = t.copy();

			if( xfm == null )
				result.setReal( this.getDoublePosition( d ) );
			else
			{
				xfm.apply( this, pt );
				result.setReal( pt.getDoublePosition( d ) );
			}
			return result;
		}

		@Override
		public RandomAccess< T > copy()
		{
			return new PositionRandomAccess<S>( this.numDimensions(), xfm, dim );
		}

		@Override
		public RandomAccess< T > copyRandomAccess()
		{
			return new PositionRandomAccess<S>( this.numDimensions(), xfm, dim );
		}
	}

	@Override
	public int numDimensions()
	{
		return nd;
	}

	@Override
	public RandomAccess< T > randomAccess()
	{
		return new PositionRandomAccess<X>( nd, xfm, dim );
	}

	@Override
	public RandomAccess< T > randomAccess( Interval arg0 )
	{
		return new PositionRandomAccess<X>( nd, xfm, dim );
	}

	@Override
	public T getType() {

		return t.createVariable();
	}
}
