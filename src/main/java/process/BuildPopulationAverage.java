package process;

import java.io.File;
import java.io.IOException;

import org.janelia.utility.parse.ParseUtils;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import loci.formats.in.DefaultMetadataOptions;
import loci.formats.in.MetadataLevel;
import loci.formats.in.NiftiReader;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class BuildPopulationAverage
{

	public static void main( String[] args ) throws FormatException, IOException, ImgLibException
	{
		String outPath = args[ 0 ];
		long[] padAmt = ParseUtils.parseLongArray( args[ 1 ] );
		System.out.println( "pad amt: " + padAmt[ 0 ] + " " + padAmt[ 1 ] + " " + padAmt[ 2 ] );

		String[] subjects = new String[ args.length - 2 ];
		System.arraycopy( args, 2, subjects, 0, subjects.length );

		long[] maxDims = new long[ 3 ];
		for ( String s : subjects )
		{
			System.out.println( s );

			// read the heads only to get max dimensions
			NiftiReader reader = new NiftiReader();
			reader.setMetadataOptions(
					new DefaultMetadataOptions( MetadataLevel.MINIMUM ) );
			reader.setId( s );

			int nx = reader.getSizeX();
			int ny = reader.getSizeY();
			int nz = reader.getImageCount();

			if ( nx > maxDims[ 0 ] )
				maxDims[ 0 ] = nx;

			if ( ny > maxDims[ 1 ] )
				maxDims[ 1 ] = ny;

			if ( nz > maxDims[ 2 ] )
				maxDims[ 2 ] = nz;

		}

		System.out.println( "maxDims before pad: " + maxDims[ 0 ] + " " + maxDims[ 1 ] + " " + maxDims[ 2 ] );

		for ( int i = 0; i < maxDims.length; i++ )
			maxDims[ i ] += padAmt[ i ];

		System.out.println( "maxDims after pad: " + maxDims[ 0 ] + " " + maxDims[ 1 ] + " " + maxDims[ 2 ] );

		// create the output image
		FloatImagePlus< FloatType > out = ImagePlusImgs.floats( maxDims );

		for ( String s : subjects )
		{
			ImagePlus ip = NiftiIo.readNifti( new File( s ) );
			Img<ShortType> img = ImageJFunctions.wrap( ip );

			// shift the subject image so that its in the center of the 
			// padded output image
			long[] translation = new long[]{ 
					-( out.dimension( 0 ) - img.dimension( 0 ) ) / 2,
					-( out.dimension( 1 ) - img.dimension( 1 ) ) / 2,
					-( out.dimension( 2 ) - img.dimension( 2 ) ) / 2 };

			IntervalView< ShortType > imgOffset = Views.interval( 
					Views.offset( Views.extendZero( img ), translation ), 
					out );

			addTo( out, imgOffset );

			ip = null;
			img = null;
		}

		divide( out,  new FloatType( (float)subjects.length ) );

		IJ.save( out.getImagePlus(), outPath );

		System.out.println( "finished" );
	}

	public static < T extends RealType<T>, S extends RealType<S>> void addTo( 
			RandomAccessibleInterval< T > add2me,
			RandomAccessibleInterval< S > other )
	{
		Cursor< T > c = Views.flatIterable( add2me ).cursor();
		RandomAccess< S > ra = other.randomAccess();
		while ( c.hasNext() )
		{
			c.fwd();
			ra.setPosition( c );
			c.get().setReal( c.get().getRealDouble() + ra.get().getRealDouble() );
		}
	}

	public static< T extends RealType<T>> void divide( RandomAccessibleInterval< T > img,
			T d  )
	{
		Cursor< T > c = Views.flatIterable( img ).cursor();
		while ( c.hasNext() )
		{
			c.next().div( d );
		}
	}
}