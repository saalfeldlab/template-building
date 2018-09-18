package net.imglib2.quantization;

import java.util.HashMap;
import java.util.Map;

import net.imglib2.type.numeric.RealType;

public class GammaQuantizer<S extends RealType<S>, T extends RealType<T>> implements AbstractQuantizer< S, T >
{
	double a;
	double b;
	double gamma;
	S s;
	T t;
	
	public GammaQuantizer( S s, T t )
	{
		this.s = s.copy();
		this.t = t.copy();
	}
	
	public GammaQuantizer( S s, T t, double a, double b, double gamma )
	{
		this.s = s.copy();
		this.t = t.copy();
		setParameters( a, b, gamma );
	}

	public void setParameters( double a, double b, double gamma )
	{
		this.a = a;
		this.b = b;
		this.gamma = gamma;
	}

	@Override
	public void convert( S input, T output )
	{
//		if( input.getRealDouble() < -b )
//			output.setReal( -a );
//		else if(  input.getRealDouble() > b )
//			output.setReal( a );
//		else if(  input.getRealDouble() < 0 )
//			output.setReal( Math.pow(( -input.getRealDouble() / b ), gamma ) * -a );
//		else
//			output.setReal( Math.pow(( input.getRealDouble() / b ), gamma ) * a );


		if(  input.getRealDouble() < 0 )
			output.setReal( Math.pow(( -input.getRealDouble() / b ), gamma ) * -a );
		else
			output.setReal( Math.pow(( input.getRealDouble() / b ), gamma ) * a );
	}
	
	@Override
	public GammaQuantizer< T, S > inverse()
	{
		return new GammaQuantizer< T, S >( t.copy(), s.copy(), b, a, 1/gamma );
	}

	public String toString()
	{
		return "GammaQuantization : " + a + " " + b + " " + gamma;
	}
	
	@Override
	public Map<String,Double> parameters()
	{
		HashMap<String,Double> map = new HashMap<>();
		map.put( "a", a );
		map.put( "b", b );
		map.put( "gamma", gamma );
		return map;
	}
}
