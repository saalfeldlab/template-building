package net.imglib2.quantization;

import java.util.HashMap;
import java.util.Map;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.type.numeric.RealType;

public class LinearQuantizer<S extends RealType<S>, T extends RealType<T>> implements AbstractQuantizer< S, T >
{
	double m;
	double b;
	S s;
	T t;
	
	public LinearQuantizer( S s, T t )
	{
		this.s = s.copy();
		this.t = t.copy();
	}
	
	public LinearQuantizer( S s, T t, double m, double b )
	{
		this.s = s.copy();
		this.t = t.copy();
		setParameters( m, b );
	}

	public void setParameters( double m, double b )
	{
		this.m = m;
		this.b = b;
	}

	@Override
	public void convert( S input, T output )
	{
		output.setReal( m * input.getRealDouble() + b );
	}
	
	public void fit( IterableInterval<S> data, T t )
	{
		S min = s.copy();
		S max = s.copy();
		min.setReal( min.getMaxValue() );
		max.setReal( max.getMinValue() );

		Cursor< S > c = data.cursor();
		while( c.hasNext() )
		{
			c.fwd();
			if( min.compareTo( c.get() ) > 0 )
				min.set( c.get() );
			
			if( max.compareTo( c.get() ) < 0 )
				max.set( c.get() );
		}

		fromMinMax( min, max );
	}

	public void fromMinMax( S min, S max )
	{
		fromMinMax( min.getRealDouble(), max.getRealDouble() );
	}
	
	public void fromMinMax( double min, double max )
	{
		double rangeOut = t.getMaxValue() - t.getMinValue();
		m = ( max - min ) / rangeOut;
	}

	public void inferByType( S s, T t )
	{
		fromMinMax( s.getMinValue(), s.getMaxValue() );
	}

	public String toString()
	{
		return "LinearQuantization : " + m + " " + b;
	}
	
	@Override
	public Map<String,Double> parameters()
	{
		HashMap<String,Double> map = new HashMap<>();
		map.put( "m", m );
		map.put( "b", b );
		return map;
	}

	@Override
	public AbstractQuantizer< T, S > inverse()
	{
		return new LinearQuantizer<T,S>( t, s, (1.0/m), (b/m) );
	}
}
