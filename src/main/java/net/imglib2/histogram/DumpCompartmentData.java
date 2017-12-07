package net.imglib2.histogram;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ByteImagePlus;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ShortImagePlus;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * Dumps image data to a csv file.
 * Arguments:
 * 	outF 			- output file
 * 	imF				- input image file (.nii or readable by imagej)
 * 	compartmentF 	- compartment image file
 *  maskF			- mask image file
 * 
 * @author John Bogovic
 *
 */
public class DumpCompartmentData
{

	public static void main(String[] args) throws FormatException, IOException
	{
		final String outF = args[ 0 ];
		final String imF = args[ 1 ];
		final String compartmentF = args[ 2 ];
		final String maskF = args[ 3 ];

		boolean separate = false;
		if( args.length >= 5 )
		{
			String sepArg = args[ 4 ];
			separate = ( Boolean.parseBoolean( sepArg ) );
		}

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

		run( imp, maskImg, compartmentImg, outF, null, separate );

	}

	public static List<Integer> parseLabels( String label )
	{
		return Arrays.stream( label.split( "," ))
				.map( Integer::parseInt )
				.collect( Collectors.toList() );
	}

	public static <T extends RealType<T>> IterableInterval<BoolType> getLabelMask( IterableInterval<T> img, T i )
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

	public static void run( ImagePlus imgImp, IterableInterval< BoolType > maskImg, ImagePlus compartmentImp, 
			String outF, List<Integer> labellist, boolean separate )
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

			if( separate )
				writeDataSeparate( img, maskImg, labels, uniques, outF );
			else
				writeData( img, maskImg, labels, uniques, outF );

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

			if( separate )
				writeDataSeparate( img, maskImg, labels, uniques, outF );
			else
				writeData( img, maskImg, labels, uniques, outF );

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

			if( separate )
				writeDataSeparate( img, maskImg, labels, uniques, outF );
			else
				writeData( img, maskImg, labels, uniques, outF );
		}
		else
		{
			System.err.println( "invalid image type" );
		}
	}
	
	public static <I extends RealType<I>, T extends RealType<T>> void writeDataSeparate( 
			Img<T> img, IterableInterval<BoolType> mask, Img<I> labels, Collection<I> uniques,
			String outF )
	{
		T t = img.firstElement().copy();
		System.out.println( uniques );

		boolean error = false;

		// setup
		HashMap< I, BufferedWriter > writerMap = new HashMap< I, BufferedWriter >();
		for ( I i : uniques )
		{
			if( i.getRealDouble() <= 0 )
			{
				System.out.println( "skipping: " + i );
				continue;
			}

			String labelString = i.toString();
			String outputPath = outF + "_" + labelString + ".csv";

			try
			{
				writerMap.put( i, Files.newBufferedWriter( Paths.get( outputPath ) ) );
			} catch ( IOException e )
			{
				e.printStackTrace();
				error = true;
				break;
			}

		}

		if ( !error )
		{
			Cursor< BoolType > c = mask.cursor();
			RandomAccess< T > rai = Views.extendZero( img ).randomAccess();
			RandomAccess< I > rac = Views.extendZero( labels ).randomAccess();
			while ( c.hasNext() )
			{
				if ( c.next().get() )
				{
					rac.setPosition( c );

					I label = rac.get();
					if ( label.getRealDouble() > 0 )
					{
						rai.setPosition( c );
						try
						{
							writerMap.get( label ).write(
									Double.toString( rai.get().getRealDouble() ) + "\n" );
						} catch ( IOException e )
						{
							e.printStackTrace();
						}
					}
				}
			}
		}

		for ( I i : uniques )
		{
			if( i.getRealDouble() <= 0 )
				continue;

			try
			{
				writerMap.get( i ).flush();
				writerMap.get( i ).close();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
	}

	public static <I extends RealType<I>, T extends RealType<T>> void writeData( 
			Img<T> img, IterableInterval<BoolType> mask, Img<I> labels, Collection<I> uniques,
			String outF )
	{
		System.out.println("writing single");
		System.out.println( uniques );

		String outPath = outF;
		if( ! outF.endsWith( "csv" ))
			outPath = outF + ".csv";

		BufferedWriter writer;
		try
		{
			writer = Files.newBufferedWriter( Paths.get( outPath ));
		} catch ( IOException e1 )
		{
			e1.printStackTrace();
			return;	
		}

		Cursor< BoolType > c = mask.cursor();
		RandomAccess< T > rai = Views.extendZero( img ).randomAccess();
		RandomAccess< I > rac = Views.extendZero( labels ).randomAccess();
		while ( c.hasNext() )
		{
			if ( c.next().get() )
			{
				rac.setPosition( c );
				I label = rac.get();
				if ( label.getRealDouble() > 0 )
				{
					rai.setPosition( c );
					try
					{
						writer.write( label.toString() + "," +
								Double.toString( rai.get().getRealDouble() ) + "\n" );
					} catch ( IOException e )
					{
						e.printStackTrace();
					}
				}
			}
		}

		try
		{
			writer.flush();
			writer.close();
		} catch ( IOException e )
		{
			e.printStackTrace();
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
	
	public static <T extends RealType<T>> boolean writeData( String outPath, Iterator<T> it )
	{
		try
		{
			BufferedWriter writer = Files.newBufferedWriter(Paths.get(outPath));
			while( it.hasNext() )
			{
				T t = it.next();
				String line = Double.toString( t.getRealDouble() );
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
