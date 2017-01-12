package util;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.img.imageplus.ShortImagePlus;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class AverageAllTallyParallel
{

	public static final ShortType ONE = new ShortType( (short) 1 );

	public static void main( String[] args ) throws ImgLibException, FormatException, IOException
	{

		String out = args [ 0 ];
		FloatImagePlus< FloatType > outimg = null;
		ShortImagePlus< ShortType > tallyimg = null;
		int N = args.length - 1;

		System.out.println( "Will write output to: " + out );
		System.out.println( "Total volumes: " + N );

		for ( int i = 1; i < args.length; i++ )
		{
			System.out.println( "i: " + i );
			ImagePlus ip = null;
			String arg = args[ i ];
			if( arg.endsWith( "nii" ) || arg.endsWith( "nii.gz" ))
			{
				ip = NiftiIo.readNifti( new File( arg ) );
			}
			else
			{
				ip = IJ.openImage( args[ i ] );
			}

			RandomAccessibleInterval< UnsignedShortType > thisimg = ImageJFunctions.wrap( ip );
			System.out.println( thisimg );
			if ( outimg == null )
			{
				System.out.println( "nslices: " +  ip.getNSlices() );
				System.out.println( "nframes: " +  ip.getNFrames() );
				System.out.println( "nchannels: " +  ip.getNChannels() );
				int nslices = ip.getNSlices();
				int nframes = ip.getNFrames();
				int nchannels = ip.getNChannels();

				int nz = 1;
				if( nslices > 1 )
					nz = nslices;

				if( nframes > nz )
					nz = nframes;

				if( nchannels > nz )
					nz = nchannels;

				outimg = ImagePlusImgs.floats( ip.getWidth(), ip.getHeight(), nz );
				tallyimg = ImagePlusImgs.shorts( ip.getWidth(), ip.getHeight(), nz );
				System.out.println( "outimg: " + outimg );

			}
			addParallel( outimg, tallyimg, thisimg, 16 );

			thisimg = null;
			ip = null;
		}

		System.out.println( "dividing" );
		System.out.println( outimg );

		divideParallel( outimg, tallyimg, 16 );

		ImagePlus ipout = ImageJFunctions.wrap( outimg, "averaged output" );
		System.out.println( ipout );

		System.out.println( "saving output" );
		IJ.save( ImageJFunctions.wrap( outimg, "averaged output" ), out );

		System.out.println("finished");
	}

	public static void divide( 
			RandomAccessibleInterval< FloatType > num,
			RandomAccessibleInterval< ShortType > denom)
	{
		Cursor< FloatType > c = Views.flatIterable( num ).cursor();
		RandomAccess< ShortType > ra = denom.randomAccess();
		while ( c.hasNext() )
		{
			c.fwd();
			ra.setPosition( c );
			double n = c.get().getRealDouble();
			double d = ra.get().getRealDouble();
			if( d > 0 )
			{
				c.get().setReal( n / d );
			}
		}
	}

	public static void addTo( 
			RandomAccessibleInterval< FloatType > add2me,
			RandomAccessibleInterval< ShortType > tally, 
			RandomAccessibleInterval< UnsignedShortType > theimage )
	{
		Cursor< FloatType > c = Views.flatIterable( add2me ).cursor();
		RandomAccess< ShortType > tra = tally.randomAccess();
		RandomAccess< UnsignedShortType > ra = theimage.randomAccess();
		while ( c.hasNext() )
		{
			c.fwd();
			ra.setPosition( c );
			tra.setPosition( c );
			c.get().setReal( c.get().getRealDouble() + ra.get().getRealDouble() );
			if( ra.get().getRealDouble() > 0 )
			{
				tra.get().add( ONE );
			}
		}
	}

	public static void addParallel( 
			RandomAccessibleInterval< FloatType > add2me,
			RandomAccessibleInterval< ShortType > tally, 
			RandomAccessibleInterval< UnsignedShortType > theimage,
			int nThreads)
	{
		// what dimension should we split across?
		int nd = add2me.numDimensions();
		int tmp = nd - 1;
		while ( tmp >= 0 )
		{
			if ( add2me.dimension( tmp ) > 1 )
				break;
			else
				tmp--;
		}
		final int dim2split = tmp;

		final long[] splitPoints = new long[ nThreads + 1 ];
		long N = add2me.dimension( dim2split );
		long del = (long) (N / nThreads);
		splitPoints[ 0 ] = 0;
		splitPoints[ nThreads ] = add2me.dimension( dim2split );
		for ( int i = 1; i < nThreads; i++ )
		{
			splitPoints[ i ] = splitPoints[ i - 1 ] + del;
		}
		ExecutorService threadPool = Executors.newFixedThreadPool( nThreads );

		LinkedList<Callable<Boolean>> jobs = new LinkedList<Callable<Boolean>>();
		for( int i = 0; i < nThreads; i++ )
		{
			final long start = splitPoints[ i ];
			final long end   = splitPoints[ i+1 ];

			jobs.add( new Callable<Boolean>()
			{
				public Boolean call()
				{
					try
					{
						final FinalInterval subItvl = getSubInterval( add2me, dim2split, start, end );
						final IntervalView< FloatType > subTgt = Views.interval( add2me, subItvl );
						final Cursor< FloatType > c = subTgt.cursor();
						final RandomAccess< ShortType > tra = tally.randomAccess();
						final RandomAccess< UnsignedShortType > ra = theimage.randomAccess();
						while ( c.hasNext() )
						{
							c.fwd();
							ra.setPosition( c );
							tra.setPosition( c );
							c.get().setReal( c.get().getRealDouble() + ra.get().getRealDouble() );
							if( ra.get().getRealDouble() > 0 )
							{
								tra.get().add( ONE );
							}
						}
						return true;
					}
					catch( Exception e )
					{
						e.printStackTrace();
					}
					return false;
				}
			});
		}
		try
		{
			List< Future< Boolean > > futures = threadPool.invokeAll( jobs );
			threadPool.shutdown(); // wait for all jobs to finish
		}
		catch ( InterruptedException e1 )
		{
			e1.printStackTrace();
		}
	}

	public static void divideParallel( 
			RandomAccessibleInterval< FloatType > num,
			RandomAccessibleInterval< ShortType > denom, 
			int nThreads)
	{

		// what dimension should we split across?
		int nd = num.numDimensions();
		int tmp = nd - 1;
		while ( tmp >= 0 )
		{
			if ( num.dimension( tmp ) > 1 )
				break;
			else
				tmp--;
		}
		final int dim2split = tmp;

		final long[] splitPoints = new long[ nThreads + 1 ];
		long N = num.dimension( dim2split );
		long del = (long) (N / nThreads);
		splitPoints[ 0 ] = 0;
		splitPoints[ nThreads ] = num.dimension( dim2split );
		for ( int i = 1; i < nThreads; i++ )
		{
			splitPoints[ i ] = splitPoints[ i - 1 ] + del;
		}
		ExecutorService threadPool = Executors.newFixedThreadPool( nThreads );

		LinkedList<Callable<Boolean>> jobs = new LinkedList<Callable<Boolean>>();
		for( int i = 0; i < nThreads; i++ )
		{
			final long start = splitPoints[ i ];
			final long end   = splitPoints[ i+1 ];

			jobs.add( new Callable<Boolean>()
			{
				public Boolean call()
				{
					try
					{
						final FinalInterval subItvl = getSubInterval( num, dim2split, start, end );
						final IntervalView< FloatType > subTgt = Views.interval( num, subItvl );
						final Cursor< FloatType > c = subTgt.cursor();
						final RandomAccess< ShortType > tra = denom.randomAccess();
						while ( c.hasNext() )
						{
							c.fwd();
							tra.setPosition( c );
							double n = c.get().getRealDouble();
							double d = tra.get().getRealDouble();
							if( d > 0 )
							{
								c.get().setReal( n / d );
							}
						}
						return true;
					}
					catch( Exception e )
					{
						e.printStackTrace();
					}
					return false;
				}
			});
		}
		try
		{
			List< Future< Boolean > > futures = threadPool.invokeAll( jobs );
			threadPool.shutdown(); // wait for all jobs to finish
		}
		catch ( InterruptedException e1 )
		{
			e1.printStackTrace();
		}
	}

	public static FinalInterval getSubInterval( Interval interval, int d, long start, long end )
	{
		int nd = interval.numDimensions();
		long[] min = new long[ nd ];
		long[] max = new long[ nd ];
		for( int i = 0; i < nd; i++ )
		{
			if( i == d )
			{
				min[ i ] = start;
				max[ i ] = end - 1;
			}
			else
			{
				min[ i ] = interval.min( i );
				max[ i ] = interval.max( i );
			}
		}
		return new FinalInterval( min, max );
	}
}