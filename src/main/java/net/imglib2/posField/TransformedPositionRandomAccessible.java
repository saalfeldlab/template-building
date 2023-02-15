package net.imglib2.posField;

import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RealPoint;
import net.imglib2.Sampler;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.RealType;

public class TransformedPositionRandomAccessible<T extends RealType< T >, X extends RealTransform >
	extends PositionRandomAccessible< T, X >
{
	private final RealTransform xfm;

	public TransformedPositionRandomAccessible( int nd, T t, RealTransform xfm )
	{
		super( nd, t );
		this.xfm = xfm;
	}

	public class TransformedPositionRandomAccess extends PositionRandomAccess
	{
		RealTransform xfm;

		RealPoint pt;
		RealPoint ptxfm;
		public TransformedPositionRandomAccess( int nd, RealTransform xfm )
		{
			super( nd, xfm, -1 ); // -1 defaults to using the last dimension
			this.xfm = xfm;
			pt = new RealPoint( xfm.numTargetDimensions() );
			ptxfm = new RealPoint( xfm.numTargetDimensions() );
		}

		@Override
		public T get()
		{
			int dim = this.getIntPosition( this.numDimensions() - 1 );
			
			for( int d = 0; d < pt.numDimensions(); d++ )
				pt.setPosition( this.getDoublePosition( d ), d );

			xfm.apply( pt, ptxfm );

			T result = t.copy();
			result.setReal( ptxfm.getDoublePosition( dim ) );
			return result;
		}

		@Override
		public RandomAccess copy()
		{
			return new TransformedPositionRandomAccess( this.numDimensions(), xfm.copy() );
		}

		@Override
		public RandomAccess< T > copyRandomAccess()
		{
			return new TransformedPositionRandomAccess( this.numDimensions(), xfm.copy() );
		}
	}

	@Override
	public RandomAccess< T > randomAccess()
	{
		return new TransformedPositionRandomAccess( nd, xfm );
	}

	@Override
	public RandomAccess< T > randomAccess( Interval arg0 )
	{
		return new TransformedPositionRandomAccess( nd, xfm );
	}
}
