package net.imglib2.histogram;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
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
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;


public class BuildCompartmentHistograms
{

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
		ImagePlus imp = null;
		if( imF.endsWith( "nii" ))
		{
			imp = NiftiIo.readNifti( new File( imF ));
		}
		else
		{
			imp = IJ.openImage( imF );
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

		findAllHistograms( imp, maskImp, binMapper, outF );

	}

	public static <T extends IntegerType<T>> IterableInterval<BoolType> getMask( IterableInterval<T> img, T i )
	{
		return Converters.convert( img, 
				new Converter<T,BoolType>()
				{
					@Override
					public void convert( T input, BoolType output) {
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

	public static void findAllHistograms( ImagePlus imgImp, ImagePlus maskimp, Real1dBinMapper<FloatType> mapper, String outF )
	{
		Img< FloatType > img = ImageJFunctions.convertFloat( imgImp );
		if( maskimp.getType() == ImagePlus.GRAY8 )
		{
			ByteImagePlus< UnsignedByteType > labels = ImagePlusAdapter.wrapByte( maskimp );
			Collection<UnsignedByteType> uniques = uniqueValues( labels );
			findAllHistograms( img, labels, uniques, mapper, outF );
		}
		else if( maskimp.getType() == ImagePlus.GRAY16 )
		{
			ShortImagePlus< UnsignedShortType > labels = ImagePlusAdapter.wrapShort( maskimp );
			Collection<UnsignedShortType> uniques = uniqueValues( labels );
			findAllHistograms( img, labels, uniques, mapper, outF );
		}
		else
		{
			System.err.println( "mask must be byte or short image" );
		}
	}
	
	public static <I extends IntegerType<I>, T extends RealType<T>> void findAllHistograms( 
			Img<T> img, Img<I> labels, Collection<I> uniques, Real1dBinMapper<T> binMapper, String outF )
	{
		System.out.println( uniques );
		for( I i : uniques )
		{
			System.out.println( i );
			IterableInterval<BoolType> mask = getMask( labels, i );

			final Histogram1d<T> hist = new Histogram1d<>( binMapper );
			MaskedIterableFilter<T,BoolType> mit = 
					new MaskedIterableFilter<T,BoolType>( mask.iterator(), img );

			hist.countData( mit );
			MaskedIterator<FloatType,BoolType> mi = (MaskedIterator<FloatType,BoolType>)mit.iterator();

			System.out.println( "MI total   : " + mi.getNumTotal() );
			System.out.println( "mi valid   : " + mi.getNumValid() );
			System.out.println( "mi invalid : " + mi.getNumInvalid() );
			System.out.println( "hist total : " + hist.totalCount() );

			String outputPath = outF + "_" + i.toString() + ".csv";
			System.out.println( "outputPath: " + outputPath );

			writeHistogram( outputPath, hist );
		}
	}
	
	public static <T extends IntegerType<T>> Collection<T> uniqueValues( IterableInterval<T> img )
	{
		Set<T> uniqueList = new HashSet<T>();
		Cursor< T > c = img.cursor();
		while( c.hasNext())
		{
			uniqueList.add( c.next().copy());
		}
		System.out.println( "uniqueList " + uniqueList );
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
