package net.imglib2.realtransform;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.concatenate.Concatenable;
import net.imglib2.concatenate.PreConcatenable;

public class AffineTransform4DRepeated3D implements AffineGet, Concatenable< AffineGet >, PreConcatenable< AffineGet >
{
	final AffineTransform3D xfm;
	
	public AffineTransform4DRepeated3D( final AffineTransform3D xfm )
	{
		this.xfm = xfm;
	}
	
	public AffineTransform3D getTransform()
	{
		return xfm;
	}

	@Override
	public void applyInverse( double[] source, double[] target )
	{
		xfm.applyInverse( source, target );
		source[ 3 ] = target[ 3 ];
	}

	@Override
	public void applyInverse( float[] source, float[] target )
	{
		xfm.applyInverse( source, target );
		source[ 3 ] = target[ 3 ];
	}

	@Override
	public void applyInverse( RealPositionable source, RealLocalizable target )
	{
		xfm.applyInverse( source, target );
		source.setPosition( target.getDoublePosition( 3 ), 3 );
	}

	@Override
	public InvertibleRealTransform copy()
	{
		return this;
	}

	@Override
	public int numSourceDimensions()
	{
		return 4;
	}

	@Override
	public int numTargetDimensions()
	{
		return 4;
	}

	@Override
	public void apply( double[] source, double[] target )
	{
		xfm.apply( source, target );
		target[ 3 ] = source[ 3 ];
	}

	@Override
	public void apply( float[] source, float[] target )
	{
		xfm.apply( source, target );
		target[ 3 ] = source[ 3 ];
	}

	@Override
	public void apply( RealLocalizable source, RealPositionable target )
	{
		xfm.apply( source, target );
		target.setPosition( source.getDoublePosition( 3 ), 3 );
	}

	@Override
	public int numDimensions()
	{
		return 3;
	}

	@Override
	public PreConcatenable< AffineGet > preConcatenate( AffineGet a )
	{
		return new AffineTransform4DRepeated3D( xfm.preConcatenate( a ) );
	}

	@Override
	public Class< AffineGet > getPreConcatenableClass()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Concatenable< AffineGet > concatenate( AffineGet a )
	{
		return new AffineTransform4DRepeated3D( xfm.concatenate( a ) );
	}

	@Override
	public Class< AffineGet > getConcatenableClass()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double get( int row, int column )
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double[] getRowPackedCopy()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RealLocalizable d( int d )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AffineGet inverse()
	{
		return new AffineTransform4DRepeated3D( xfm.inverse() );
	}
	
}
