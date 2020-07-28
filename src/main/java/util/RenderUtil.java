package util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import com.google.common.collect.Streams;

import ij.IJ;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;
import net.imglib2.view.composite.CompositeIntervalView;

public class RenderUtil
{
	
	public static void main( String[] args )
	{
		System.out.println( " " + Integer.parseInt("0014"));
	}

	public static FinalInterval transformRealInterval( RealTransform xfm, RealInterval interval )
	{
		int nd = interval.numDimensions();
		double[] pt = new double[ nd ];
		double[] ptxfm = new double[ nd ];

		long[] min = new long[ nd ];
		long[] max = new long[ nd ];

		// transform min		
		for( int d = 0; d < nd; d++ )
			pt[ d ] = interval.realMin( d );
		
		xfm.apply( pt, ptxfm );
		copyToLongFloor( ptxfm, min );


		// transform max
		
		for( int d = 0; d < nd; d++ )
		{
			pt[ d ] = interval.realMax( d );
		}
		
		xfm.apply( pt, ptxfm );
		copyToLongCeil( ptxfm, max );
		
		return new FinalInterval( min, max );
	}
	
	public static FinalInterval transformInterval( RealTransform xfm, Interval interval )
	{
		int nd = interval.numDimensions();
		double[] pt = new double[ nd ];
		double[] ptxfm = new double[ nd ];

		long[] min = new long[ nd ];
		long[] max = new long[ nd ];

		// transform min		
		for( int d = 0; d < nd; d++ )
			pt[ d ] = interval.min( d );
		
		xfm.apply( pt, ptxfm );
		copyToLongFloor( ptxfm, min );


		// transform max
		
		for( int d = 0; d < nd; d++ )
		{
			pt[ d ] = interval.max( d );
		}
		
		xfm.apply( pt, ptxfm );
		copyToLongCeil( ptxfm, max );
		
		return new FinalInterval( min, max );
	}

	public static void copyToLongFloor( final double[] src, final long[] dst )
	{
		for( int d = 0; d < src.length; d++ )
			dst[ d ] = (long)Math.floor( src[d] );
	}

	public static void copyToLongCeil( final double[] src, final long[] dst )
	{
		for( int d = 0; d < src.length; d++ )
			dst[ d ] = (long)Math.ceil( src[d] );
	}

	public static long[] splitPoints( int nThreads, int dim2split, Interval target )
	{
		final long[] splitPoints = new long[ nThreads + 1 ];
		long N = target.dimension( dim2split );
		long del = ( long )( N / nThreads ); 
		splitPoints[ 0 ] = target.min( dim2split );
		splitPoints[ nThreads ] = target.max( dim2split ) + 1;
		for( int i = 1; i < nThreads; i++ )
		{
			splitPoints[ i ] = splitPoints[ i - 1 ] + del; 
		}
		return splitPoints;
	}

	/**
	 * Sets values of the dest vector image by applying a function to corresponding
	 * values of the src vector image.
	 * 
	 * The function's first argument is the source, the second is it's destination. 
	 * 
	 * @param src
	 * @param dest
	 * @param function
	 * @param nCores
	 * @return
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public static < T extends RealType<T>, V extends Composite< T > > boolean copyToImageStackIterOrder( 
			final CompositeIntervalView< T, V > src,
			final CompositeIntervalView< T, V > dest,
			final BiConsumer< V, V > function,
			final int nCores ) throws InterruptedException, ExecutionException
	{

		int nThreads = nCores;
		ExecutorService threadPool;
		if ( nCores < 1 )
			nThreads = Runtime.getRuntime().availableProcessors();

		final int nTasks = nThreads;
		threadPool = Executors.newFixedThreadPool( nThreads );
		LinkedList< Callable< Boolean > > jobs = new LinkedList< Callable< Boolean > >();
		for ( int i = 0; i < nThreads; i++ )
		{

			final int index = i;
			jobs.add( new Callable< Boolean >()
			{
				public Boolean call()
				{
					// start by offset by this jobs' index
					Cursor< V > c = Views.flatIterable( dest ).cursor();
					final RandomAccess< V > threadRa = src.randomAccess().copyRandomAccess();
					c.jumpFwd( index );

					while ( c.hasNext() )
					{
						c.jumpFwd( nTasks );
						threadRa.setPosition( c );
						function.accept( threadRa.get(), c.get() );
					}

					return true;
				}
			} );
		}

		List< Future< Boolean > > futures = threadPool.invokeAll( jobs );
		List< Boolean > results = new ArrayList< Boolean >();
		for ( Future< Boolean > f : futures )
			results.add( f.get() );

		System.out.println( "done copying" );
		return results.stream().reduce( true, ( a, b ) -> a && b );
	}

	public static < T extends NumericType< T > > boolean copyToImageStackIterOrder( 
			final RandomAccessible< T > ra, 
			final IterableInterval< T > target, 
			final int nCores ) throws InterruptedException, ExecutionException
	{

		int nThreads = nCores;
		ExecutorService threadPool;
		if ( nCores < 1 )
			nThreads = Runtime.getRuntime().availableProcessors();

		final int nTasks = nThreads;
		threadPool = Executors.newFixedThreadPool( nThreads );
		LinkedList< Callable< Boolean > > jobs = new LinkedList< Callable< Boolean > >();
		for ( int i = 0; i < nThreads; i++ )
		{

			final int index = i;
			jobs.add( new Callable< Boolean >()
			{
				public Boolean call()
				{
					// start by offset by this jobs' index
					Cursor< T > c = target.cursor();
					final RandomAccess< T > threadRa = ra.randomAccess().copyRandomAccess();
					c.jumpFwd( index );

					while ( c.hasNext() )
					{
						c.jumpFwd( nTasks );
						threadRa.setPosition( c );
						c.get().set( threadRa.get() );
					}

					return true;
				}
			} );
		}

		List< Future< Boolean > > futures = threadPool.invokeAll( jobs );
		List< Boolean > results = new ArrayList< Boolean >();
		for ( Future< Boolean > f : futures )
			results.add( f.get() );

		System.out.println( "done copying" );
		return results.stream().reduce( true, ( a, b ) -> a && b );
	}

	public static < T extends NumericType<T> > RandomAccessibleInterval<T> copyToImageStack( 
			final RandomAccessible< T > raible,
			final RandomAccessibleInterval<T> target,
			final int nThreads )
	{
		// what dimension should we split across?
		int nd = raible.numDimensions();
		int tmp = nd - 1;
		while( tmp >= 0 )
		{
			if( target.dimension( tmp ) > 1 )
				break;
			else
				tmp--;
		}
		final int dim2split = tmp;

		final long[] splitPoints = new long[ nThreads + 1 ];
		long N = target.dimension( dim2split );
		long del = ( long )( N / nThreads ); 
		splitPoints[ 0 ] = target.min( dim2split );
		splitPoints[ nThreads ] = target.max( dim2split ) + 1;
		for( int i = 1; i < nThreads; i++ )
		{
			splitPoints[ i ] = splitPoints[ i - 1 ] + del;
			System.out.println( "splitPoints[i]: " + splitPoints[ i ] ); 
		}
//		System.out.println( "dim2split: " + dim2split );
//		System.out.println( "split points: " + XfmUtils.printArray( splitPoints ));

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
						final FinalInterval subItvl = getSubInterval( target, dim2split, start, end );
						final IntervalView< T > subTgt = Views.interval( target, subItvl );
						final Cursor< T > c = subTgt.cursor();
						final RandomAccess< T > ra = raible.randomAccess();
						while ( c.hasNext() )
						{
							c.fwd();
							ra.setPosition( c );
							c.get().set( ra.get() );
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
		catch ( Exception e1 )
		{
			e1.printStackTrace();
		}

		IJ.showProgress( 1.1 );
		return target;
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
