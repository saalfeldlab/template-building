package net.imglib2.quantization;

import java.util.Map;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.RealType;

public interface AbstractQuantizer<S extends RealType<S>, T extends RealType<T>> extends Converter< S, T >
{

	public abstract void convert( S input, T output );

	public abstract Map<String,Double> parameters();
	
	public abstract AbstractQuantizer<T,S> inverse();


}
