package net.imglib2.histogram;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.adobe.xmp.impl.Utils;
import com.google.common.collect.Streams;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.AbstractCursor;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.filter.MaskedIterableFilter;
import net.imglib2.filter.MaskedIterableFilter.MaskedIterator;
import net.imglib2.img.AbstractImg;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ByteImagePlus;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ShortImagePlus;
import net.imglib2.type.BooleanType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;


public class BuildCompartmentHistograms
{

	public static void main(String[] args) throws FormatException, IOException
	{
		final String outF = args[ 0 ];
		final String imF = args[ 1 ];
		final float histmin = Float.parseFloat( args[ 2 ]);
		final float histmax = Float.parseFloat( args[ 3 ]);
		final int numBins = Integer.parseInt( args[ 4 ]);
		final String compartmentF = args[ 5 ];
		final String maskF = args[ 6 ];
		final boolean tailBins = Boolean.parseBoolean( args[ 7 ] );

		List<Integer> labellist = null;
		if( args.length >= 9 )
		{
			labellist = parseLabels( args[ 8 ]);
			System.out.println("labellist: " + labellist );
		}

		System.out.println( "tailBins: " + tailBins );
		Real1dBinMapper<FloatType> binMapper = new Real1dBinMapper<FloatType>(
				histmin, histmax, numBins, tailBins );

		System.out.println( "imF          : " + imF );
		System.out.println( "maskF        : " + maskF );
		System.out.println( "compartmentF : " + compartmentF );
		ImagePlus imp = null;
		if( imF.endsWith( "nii" ))
		{
			imp = NiftiIo.readNifti( new File( imF ));
		}
		else
		{
			imp = IJ.openImage( imF );
		}
	
		IterableInterval<BoolType> maskImg = null;
		if( maskF.endsWith( "nii" ))
		{
			maskImg = getMask( ImageJFunctions.convertFloat( NiftiIo.readNifti( new File( maskF ))), 0.0 );
		}
		else
		{
			maskImg = getMask( ImageJFunctions.convertFloat( IJ.openImage( maskF )), 0.0 );
		}

		ImagePlus compartmentImg = null;
		if( compartmentF.endsWith( "nii" ))
		{
			compartmentImg = ( NiftiIo.readNifti( new File( compartmentF )));
		}
		else
		{
			compartmentImg = ( IJ.openImage( compartmentF ) );
		}

		findAllHistograms( imp, maskImg, compartmentImg, binMapper, outF, labellist );

	}

	public static List<Integer> parseLabels( String label )
	{
		return Arrays.stream( label.split( "," ))
				.map( Integer::parseInt )
				.collect( Collectors.toList() );
	}

	public static <T extends RealType<T>> IterableInterval<BoolType> getMask( IterableInterval<T> img, T i )
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

	public static <T extends RealType<T>> IterableInterval<BoolType> getMask( IterableInterval<T> img, double thresh )
	{
		return Converters.convert( img, 
				new Converter<T,BoolType>()
				{
					@Override
					public void convert( T input, BoolType output) {
						if( input.getRealDouble() > thresh )
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

	public static void findAllHistograms( ImagePlus imgImp, IterableInterval< BoolType > maskImg, ImagePlus compartmentImp, 
			Real1dBinMapper<FloatType> mapper, 
			String outF, List<Integer> labellist )
	{
		Img< FloatType > img = ImageJFunctions.convertFloat( imgImp );
		if( compartmentImp.getType() == ImagePlus.GRAY8 )
		{
			ByteImagePlus< UnsignedByteType > labels = ImagePlusAdapter.wrapByte( compartmentImp );
			Collection<UnsignedByteType> uniques;
			if( labellist == null)
				uniques = uniqueValues( labels );
			else
				uniques = labellist.parallelStream().map( UnsignedByteType::new )
						.collect( Collectors.toList() );

			findAllHistograms( img, maskImg, labels, uniques, mapper, outF, labellist );
		}
		else if( compartmentImp.getType() == ImagePlus.GRAY16 )
		{
			ShortImagePlus< UnsignedShortType > labels = ImagePlusAdapter.wrapShort( compartmentImp );
			Collection<UnsignedShortType> uniques;
			if( labellist == null)
				uniques = uniqueValues( labels );
			else
				uniques = labellist.parallelStream().map( UnsignedShortType::new )
						.collect( Collectors.toList() );

			findAllHistograms( img, maskImg, labels, uniques, mapper, outF, labellist );
		}
		else if( compartmentImp.getType() == ImagePlus.GRAY32 )
		{
			FloatImagePlus< FloatType > labels = ImagePlusAdapter.wrapFloat( compartmentImp );
			Collection<FloatType> uniques;
			if( labellist == null)
				uniques = uniqueValues( labels );
			else
				uniques = labellist.parallelStream().map( FloatType::new )
						.collect( Collectors.toList() );

			findAllHistograms( img, maskImg, labels, uniques, mapper, outF, labellist );
		}
		else
		{
			System.err.println( "mask must be byte or short image" );
		}
	}
	
	public static <I extends RealType<I>, T extends RealType<T>> void findAllHistograms( 
			Img<T> img, IterableInterval<BoolType> mask, Img<I> labels, Collection<I> uniques, Real1dBinMapper<T> binMapper,
			String outF, List<Integer> labellist )
	{
		T t = img.firstElement().copy();
		System.out.println( uniques );
		for( I i : uniques )
		{
			System.out.println( i );
			IterableInterval<BoolType> compartmentAndMask = new And( getMask( labels, i ), mask );

			final Histogram1d<T> hist = new Histogram1d<>( binMapper );
			MaskedIterableFilter<T,BoolType> mit = 
					new MaskedIterableFilter<T,BoolType>( compartmentAndMask.iterator(), img );

			hist.countData( mit );
			MaskedIterator<FloatType,BoolType> mi = (MaskedIterator<FloatType,BoolType>)mit.iterator();

			System.out.println( "MI total   : " + mi.getNumTotal() );
			System.out.println( "mi valid   : " + mi.getNumValid() );
			System.out.println( "mi invalid : " + mi.getNumInvalid() );
			System.out.println( "hist total : " + hist.totalCount() );

			String outputPath = outF + "_" + i.toString() + ".csv";
			System.out.println( "outputPath: " + outputPath );

			writeHistogram( outputPath, hist, t );
		}
	}
	
	public static <T extends RealType<T>> Collection<T> uniqueValues( IterableInterval<T> img )
	{
		Set<T> uniqueList = new HashSet<T>();
		Cursor< T > c = img.cursor();
		while( c.hasNext())
		{
			uniqueList.add( c.next().copy());
		}
		return uniqueList;
	}
	
	public static <T extends RealType<T>> boolean writeHistogram( String outPath, Histogram1d<T> hist, T t )
	{
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

	public static class And extends AbstractImg<BoolType>
	{
		IterableInterval<BoolType> a;
		IterableInterval<BoolType> b;
		public And( IterableInterval<BoolType> a, IterableInterval<BoolType> b )
		{
			super( Intervals.dimensionsAsLongArray( a ));
			this.a = a;
			this.b = b;
		}
		@Override
		public ImgFactory< BoolType > factory()
		{
			return null;
		}
		@Override
		public Img< BoolType > copy()
		{
			return this;
		}
		@Override
		public RandomAccess< BoolType > randomAccess()
		{
			return null;
		}
		@Override
		public Cursor< BoolType > cursor()
		{
			return new AndCursor< BoolType >( a.cursor(), b.cursor(), new BoolType() );
		}
		@Override
		public Cursor< BoolType > localizingCursor()
		{
			return null;
		}
		@Override
		public Object iterationOrder()
		{
			return null;
		}
	};

	public static class AndCursor <T extends BooleanType<T>> extends AbstractCursor<T>
	{
		final Cursor<T> a;
		final Cursor<T> b;
		T t;
		public AndCursor( Cursor<T> a, Cursor<T> b, T t  )
		{
			super( a.numDimensions() );
			this.a = a;
			this.b = b;
			this.t = t;
		}

		@Override
		public T get()
		{
			t.set( a.get() );
			t.and( b.get() );
			return t;
		}

		@Override
		public void fwd()
		{
			a.fwd();
			b.fwd();
		}

		@Override
		public void reset()
		{
			a.reset();
			b.reset();
		}

		@Override
		public boolean hasNext()
		{
			return a.hasNext() && b.hasNext();
		}

		@Override
		public void localize( long[] position )
		{
			a.localize( position );
		}

		@Override
		public long getLongPosition( int d )
		{
			return a.getLongPosition( d );
		}

		@Override
		public AbstractCursor<T> copy()
		{
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public AbstractCursor<T> copyCursor()
		{
			return this;
		}
	};

}
