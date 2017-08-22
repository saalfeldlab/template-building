package net.imglib2.histogram;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import mpicbg.imglib.type.numeric.NumericType;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.filter.MaskedIterableFilter;
import net.imglib2.filter.MaskedIterableFilter.MaskedIterator;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ByteImagePlus;
import net.imglib2.img.imageplus.ShortImagePlus;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;


public class BuildCompartmentHistograms {

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws FormatException, IOException
	{
		final String outF = args[ 0 ];
		final String imF = args[ 1 ];
		final float histmin = Float.parseFloat( args[ 2 ]);
		final float histmax = Float.parseFloat( args[ 3 ]);
		final int numBins = Integer.parseInt( args[ 4 ]);
		final String maskF = args[ 5 ];
		
		boolean tailBins = false;
		if( args.length >= 8 )
			tailBins = Boolean.parseBoolean( args[ 7 ] );

		System.out.println( "tailBins: " + tailBins );
		Real1dBinMapper<FloatType> binMapper = new Real1dBinMapper<FloatType>(
				histmin, histmax, numBins, tailBins );

		System.out.println( "imF   : " + imF );
		System.out.println( "maskF : " + maskF );
		IterableInterval<FloatType> img;
		if( imF.endsWith( "nii" ))
		{
			img = ImageJFunctions.convertFloat( NiftiIo.readNifti( new File( imF )));
		}
		else
		{
			img = ImageJFunctions.convertFloat( IJ.openImage( imF ) );	
		}
	
		ImagePlus maskImp = null;
		if( maskF.endsWith( "nii" ))
		{
			maskImp = ( NiftiIo.readNifti( new File( maskF )));
		}
		else
		{
			maskImp = ( IJ.openImage( maskF ) );	
		}
		
		Set<IntType> uniqueList = uniqueValues( (IterableInterval<? extends IntegerType<?>>) maskRaw );
		
	
		for( IntType i : uniqueList )
		{
			IterableInterval<BoolType> mask = getMask( maskImp, i );
			
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

			writeHistogram( outF + "_" + i.get() + ".csv", hist );
		}
	}
	public static <T extends IntegerType<T>> IterableInterval<BoolType> getMask( ImagePlus imp, T i )
	{
		if( imp.getType() == ImagePlus.GRAY8 )
		{
			//ByteImagePlus<UnsignedByteType> img = ImagePlusAdapter.wrapByte(imp);
			IterableInterval<UnsignedByteType> img = ImagePlusAdapter.wrapByte(imp);
			return Converters.convert( img, 
					new Converter<UnsignedByteType,BoolType>()
					{
						@Override
						public void convert( UnsignedByteType input, BoolType output) {
							if( input.equals( i ) )
							{
								output.set( true );
							}
							else
							{
								output.set( false );
							}
						}
					}, 
					new BoolType());
		}
		else if( imp.getType() == ImagePlus.GRAY16 )
		{
			IterableInterval<UnsignedShortType> img = ImagePlusAdapter.wrapShort(imp);
			return Converters.convert( img, 
					new Converter<UnsignedShortType,BoolType>()
					{
						@Override
						public void convert( UnsignedShortType input, BoolType output) {
							if( input.equals( i ) )
							{
								output.set( true );
							}
							else
							{
								output.set( false );
							}
						}
					}, 
					new BoolType());
		}
		return null;
	}
	
	public static <T extends NumericType<T>, S extends NumericType<S>> Converter<T, BoolType> getConverter( IterableInterval<T> img , S i )
	{
		return new Converter<T,BoolType>()
		{
			@Override
			public void convert(T input, BoolType output) {
				if( input.equals( i ) )
				{
					output.set( true );
				}
				else
				{
					output.set( false );
				}
			}
		};
	}
	
	public static Set<IntType> uniqueValues( IterableInterval<? extends IntegerType<?>> img )
	{
		Set<T> uniqueList = new HashSet<T>();
		Cursor<T> c = img.cursor();
		while( c.hasNext())
			uniqueList.add( c.next());

		return uniqueList;
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
