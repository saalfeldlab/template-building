package net.imglib2.histogram;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

import ij.IJ;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.filter.MaskedIterableFilter;
import net.imglib2.filter.MaskedIterableFilter.MaskedIterator;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.FloatType;


public class BuildHistogram {

	public static void main(String[] args)
	{
		final String outF = args[ 0 ];
		final String imF = args[ 1 ];
		final float histmin = Float.parseFloat( args[ 2 ]);
		final float histmax = Float.parseFloat( args[ 3 ]);
		final int numBins = Integer.parseInt( args[ 4 ]);
		final String maskF = args[ 5 ];
		final float maskthresh = Float.parseFloat( args[ 6 ]);
		
		boolean tailBins = false;
		if( args.length >= 8 )
			tailBins = Boolean.parseBoolean( args[ 7 ] );

		System.out.println( "tailBins: " + tailBins );
		Real1dBinMapper<FloatType> binMapper = new Real1dBinMapper<FloatType>(
				histmin, histmax, numBins, tailBins );

		IterableInterval<FloatType> img = ImageJFunctions.convertFloat( IJ.openImage( imF ));
		IterableInterval<FloatType> maskRaw = null;
		if( maskF.equals( imF ))
		{
			System.out.println( "mask is same as input image");
			maskRaw = img;
		}
		else
			maskRaw = ImageJFunctions.convertFloat( IJ.openImage( maskF ));
		
		Converter<FloatType,BoolType> maskConv = new Converter<FloatType,BoolType>()
		{
			@Override
			public void convert(FloatType input, BoolType output) {
				if( input.get() < maskthresh )
				{
					output.set( false );
				}
				else
				{
					output.set( true );
				}
			}
		};
		IterableInterval<BoolType> mask = Converters.convert( maskRaw, maskConv, new BoolType());
		
		final Histogram1d<FloatType> hist = new Histogram1d<>( binMapper );
		MaskedIterableFilter<FloatType,BoolType> mit = 
				new MaskedIterableFilter<FloatType,BoolType>( mask.iterator() );
		mit.set( img );
		hist.countData( mit );
		MaskedIterator<FloatType,BoolType> mi = (MaskedIterator<FloatType,BoolType>)mit.iterator();

		System.out.println( "MI total   : " + mi.getNumTotal() );
		System.out.println( "mi valid   : " + mi.getNumValid() );
		System.out.println( "mi invalid : " + mi.getNumInvalid() );
		System.out.println( "hist total : " + hist.totalCount() );

		writeHistogram( outF, hist );
	}
	
	public static <T extends RealType<T>> boolean writeHistogram( String outPath, Histogram1d<T> hist )
	{
		T t = hist.firstDataValue().copy();
		RandomAccess<LongType> hra = hist.randomAccess();
		try
		{
			BufferedWriter writer = Files.newBufferedWriter(Paths.get(outPath));
			for( int i = 0; i < hist.getBinCount(); i++ )
			{
				hist.getCenterValue( i, t );
				hra.setPosition( i, 0 );
				String line = Double.toString( t.getRealDouble() ) + "," + 
						Long.toString( hra.get().get() );
				writer.write( line + "\n" );
			}
			writer.close();
		} catch( Exception e )
		{
			e.printStackTrace();
			return false;
		}
        return true;
	}

}
