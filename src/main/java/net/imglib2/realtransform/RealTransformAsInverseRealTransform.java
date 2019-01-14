package net.imglib2.realtransform;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;

public class RealTransformAsInverseRealTransform implements InvertibleRealTransform
{
	private final RealTransform transform;

	
	public RealTransformAsInverseRealTransform(
			final RealTransform transform )
	{
		this.transform = transform;
	}

	@Override
	public int numSourceDimensions()
	{
		return transform.numSourceDimensions();
	}

	@Override
	public int numTargetDimensions()
	{
		return transform.numTargetDimensions();
	}


	@Override
	public void apply( double[] source, double[] target )
	{
		transform.apply( source, target );
	}

	@Override
	public void apply( RealLocalizable source, RealPositionable target )
	{
		transform.apply( source, target );
	}


	@Override
	public void applyInverse( double[] source, double[] target )
	{
		transform.apply( target, source );
	}


	@Override
	public void applyInverse( RealPositionable source, RealLocalizable target )
	{
		transform.apply( target, source );
	}


	@Override
	public InvertibleRealTransform inverse()
	{
		return this;
	}

	@Override
	public InvertibleRealTransform copy()
	{
		return this;
	}
	
}
