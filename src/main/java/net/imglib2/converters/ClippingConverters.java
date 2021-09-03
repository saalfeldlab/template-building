package net.imglib2.converters;

import net.imglib2.converter.Converter;
import net.imglib2.converter.RealTypeConverters;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class ClippingConverters {

//	private static final Class<UnsignedByteType> ubClass = UnsignedByteType.class;
//	private static final Class<UnsignedShortType> usClass = UnsignedShortType.class;

	@SuppressWarnings("unchecked")
	public static < S extends RealType< ? >, T extends RealType< ? > > Converter< S, T > getConverter( S inputType, T outputType )
	{
//		if( outputType.getClass().equals( ubClass ))
//			return (Converter<S,T>)new ClippedRealUnsignedByteConverter<S,T>();
//		else if ( outputType.getClass().equals( usClass ))
//			return new ClippedRealUnsignedShort<S,T>();
//		else
//			RealTypeConverters.getConverter(inputType, outputType);

		if( outputType instanceof UnsignedByteType )
			return (Converter<S, T>) new ClippedRealUnsignedByteConverter<S>();
		else if( outputType instanceof UnsignedShortType )
			return (Converter<S, T>) new ClippedRealUnsignedShortConverter<S>();
		else
			return RealTypeConverters.getConverter(inputType, outputType);
	}

	public static class ClippedRealUnsignedByteConverter<S extends RealType<?>> implements Converter<S,UnsignedByteType>{
		@Override
		public void convert(S input, UnsignedByteType output) {
			if( input.getRealDouble() < 0 )
				output.setZero();
			else if( input.getRealDouble() > 255 )
				output.set(255);
			else
				output.set( (int)input.getRealDouble() );
		}
	}

	public static class ClippedRealUnsignedShortConverter<S extends RealType<?>> implements Converter<S,UnsignedShortType>{
		@Override
		public void convert(S input, UnsignedShortType output) {
			if( input.getRealDouble() < 0 )
				output.setZero();
			else if( input.getRealDouble() > 65535 )
				output.set(65535);
			else
				output.set( (int)input.getRealDouble() );
		}
	}
}
