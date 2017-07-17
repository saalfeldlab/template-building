package net.imglib2.posField;

import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.type.numeric.RealType;

public class PositionRandomAccessible<T extends RealType< T >>
		implements RandomAccessible< T >
{
	protected final int nd;
	protected final T t;

	public PositionRandomAccessible( int nd, T t )
	{
		this.nd = nd + 1;
		this.t = t;
		t.setZero();
	}

	public class PositionRandomAccess extends Point implements RandomAccess< T >
	{

		public PositionRandomAccess( int nd )
		{
			super( nd );
		}

		@Override
		public T get()
		{
			int dim = this.getIntPosition( this.numDimensions() - 1 );

//			t.setReal( this.getDoublePosition( dim ) );
//			return t;

			T result = t.copy();
			result.setReal( this.getDoublePosition( dim ) );
			return result;
		}

		@Override
		public Sampler< T > copy()
		{
			return new PositionRandomAccess( this.numDimensions() );
		}

		@Override
		public RandomAccess< T > copyRandomAccess()
		{
			return new PositionRandomAccess( this.numDimensions() );
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
		return new PositionRandomAccess( nd );
	}

	@Override
	public RandomAccess< T > randomAccess( Interval arg0 )
	{
		return new PositionRandomAccess( nd );
	}
}
