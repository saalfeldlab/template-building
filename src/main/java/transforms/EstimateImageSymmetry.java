package transforms;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class EstimateImageSymmetry
{
	
	public static enum Parity { EVEN, ODD };
	public static enum Side { HIGH, LOW };

	public static void main(String[] args) throws FormatException, IOException
	{

		String imF = args[ 0 ];
		String testIntervalArg = args[ 1 ];

		
		ImagePlus ip = null;
		if( imF.endsWith( "nii" ))
		{
			ip = NiftiIo.readNifti( new File( imF ));
		}
		else
		{
			ip = IJ.openImage( imF );
		}
		
		int dim = 0;
		
		double center;
		
		System.out.println("setup");
		ImagePlus ipout = null;
		if( ip.getBitDepth() == 8 )
		{
			System.out.println("bytes");
			System.out.println("bytes");
			System.out.println("bytes");

//			center = process( ImageJFunctions.wrapByte( ip ), dim, 
//					minSearch, maxSearch );
		}
		else if( ip.getBitDepth() == 16 )
		{
			System.out.println("shorts");
			System.out.println("shorts");
			System.out.println("shorts");

//			center = process( ImageJFunctions.wrapShort( ip ), dim,
//					minSearch, maxSearch );
		}
		else if( ip.getBitDepth() == 32 )
		{
			System.out.println("floats");
			System.out.println("floats");
			System.out.println("floats");

//			center = process( ImageJFunctions.wrapFloat( ip ), dim,
//					minSearch, maxSearch);
		}
		else{
			return;
		}

//		Interval samplingInterval = new FinalInterval( 
//				new long[]{ 500, 340, 110 },
//				new long[]{ 600, 360, 130 } );
		
		Interval samplingInterval = new FinalInterval( 
				new long[]{ 500, 40, 30 },
				new long[]{ 505, 400, 130 } );

//		Interval samplingInterval = new FinalInterval( 
//				new long[]{ 500, 340, 110 },
//				new long[]{ 505, 342, 112 } );
		
		Interval testInterval = new FinalInterval( 
				new long[]{ 450, 340, 110 },
				new long[]{ 550, 360, 130 } );
		
		Interval lineInterval = new FinalInterval( 
				new long[]{ samplingInterval.min( dim ) },
				new long[]{ samplingInterval.max( dim ) } );
		
		Img<FloatType> rai = ImageJFunctions.wrapFloat( ip );
		System.out.println( Util.printInterval( rai ));
		
		int width = 200;
		process( rai, dim, width, samplingInterval );
		
	}


	public static <T extends RealType<T>> double ssd(
			final RandomAccessibleInterval<T> fwd,
			final RandomAccessibleInterval<T> rev )
	{
		double ssd = 0;
		Cursor<T> c = Views.flatIterable( fwd ).cursor();
		RandomAccess<T> revRa = rev.randomAccess();

		while( c.hasNext() )
		{
			c.fwd();
			revRa.setPosition( c );
			double diff = c.get().getRealDouble() - revRa.get().getRealDouble();
			ssd += ( diff * diff );
		}
		
		return ssd;
	}
	
	public static <T extends RealType<T>>  RandomAccessibleInterval<T> getEvenInterval( 
			final RandomAccessible<T> img,
			int dimension,
			Interval samplingInterval,
			int width,
			Localizable pt,
			Side side,
			Parity parity )
	{

		long[] min = new long[ img.numDimensions() ];
		long[] max = new long[ img.numDimensions() ];
		fillMinExcept( dimension, samplingInterval, min );
		fillMaxExcept( dimension, samplingInterval, max );

		if( side == Side.HIGH )
		{
			// high and ( even or odd ) 
			min[ dimension ] = pt.getLongPosition( dimension ) + 1;
			max[ dimension ] = pt.getLongPosition( dimension ) + width + 1;
		}
		else if( parity == Parity.EVEN )
		{
			// low and even
			min[ dimension ] = pt.getLongPosition( dimension ) - width;
			max[ dimension ] = pt.getLongPosition( dimension );
		}
		else
		{
			// low and odd
			min[ dimension ] = pt.getLongPosition( dimension ) - width - 1;
			max[ dimension ] = pt.getLongPosition( dimension ) - 1;
		}
		FinalInterval itvl = new FinalInterval( min, max );
		System.out.println( "itvl: " + Util.printInterval(itvl));
		if( side == Side.HIGH )
			return Views.zeroMin( Views.interval( img, itvl ));
		else
			return Views.zeroMin( Views.invertAxis( Views.interval( img, itvl ), dimension ));
	}
	
	public static void fillMinExcept( int d, Interval itvl, long[] min )
	{
		for( int i = 0; i < itvl.numDimensions(); i++ )
			if( i != d )
				min[ i ] = itvl.min( i );
	}

	public static void fillMaxExcept( int d, Interval itvl, long[] max )
	{
		for( int i = 0; i < itvl.numDimensions(); i++ )
			if( i != d )
				max[ i ] = itvl.max( i );
	}

	public static void printRAI( final RandomAccessibleInterval<?> img )
	{
		Cursor<?> c = Views.flatIterable( img ).cursor();
		while( c.hasNext() )
			System.out.println( c.next() );
	}

	/**
	 * 
	 * @param img
	 * @param dimension
	 * @param samplingInterval
	 * @return the center along dimension
	 */
	public static <T extends RealType<T>> double process( final RandomAccessibleInterval<T> img, final int dimension,
			final int width, Interval samplingInterval )
	{
	
		FinalInterval dimTestInterval = new FinalInterval( 
				new long[]{ samplingInterval.min( dimension )}, 
				new long[]{ samplingInterval.max( dimension )} );
		
		IntervalIterator it = new IntervalIterator( dimTestInterval );
		
		ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval<T>> ra = Views.extendZero( img );
		
		// even
		// iterate over the lower-index
		while( it.hasNext() )
		{
			it.fwd();
			RandomAccessibleInterval<T> fwd = getEvenInterval( ra, dimension, samplingInterval, width, it, Side.HIGH, Parity.EVEN );
			RandomAccessibleInterval<T> rev = getEvenInterval( ra, dimension, samplingInterval, width, it, Side.LOW, Parity.EVEN );
			double ssd = ssd( fwd, rev );


			System.out.println( "fwd itvl : " + Util.printInterval( fwd ));
			System.out.println( "rev itvl : " + Util.printInterval( rev ));
			System.out.println( "ssd      : " + ssd );
			
			break;
		}
		
		// odd
		// iterate over the "center"-index
//		RandomAccessibleInterval<T> fwd = getEvenInterval( ra, dimension, samplingInterval, width, it, Side.HIGH, Parity.ODD );
//		RandomAccessibleInterval<T> rev = getEvenInterval( ra, dimension, samplingInterval, width, it, Side.LOW, Parity.ODD );
		
		return 0;
	}
}
