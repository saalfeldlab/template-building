package process;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.DfieldIoHelper;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.iterator.RealIntervalIterator;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class DfieldComparisons implements Callable< Void >
{
	@Option( names = { "-a" }, description = "Input displacement Field A" )
	private String dfieldA;

	@Option( names = { "-b" }, description = "Input displacement Field B" )
	private String dfieldB;

	@Option( names = { "-n", "--min" }, split = ",", required = false, description = "Minimum of field of view. Defaults to the origin." )
	private double[] min;

	@Option( names = { "-x", "--max" }, split = ",", description = "Maximum of field of view. Defaults to the origin." )
	private double[] max;

	@Option( names = { "-s", "--spacing" }, required = false, split = ",", description = "The spacing at which to compute the comparison" )
	private double[] spacing;

	@Option( names = { "-j", "--nThreads" }, required = false, description = "Number of threads (default=1)" )
	private int nThreads = 1;

	public static void main( String[] args ) throws Exception
	{
		CommandLine.call( new DfieldComparisons(), args );
	}

	public void setup()
	{
		if( min == null )
		{
			min = new double[ max.length ];
		}
	}

	@Override
	public Void call() throws Exception
	{
		setup();

		final DfieldIoHelper dfieldIo = new DfieldIoHelper();
		final ANTSDeformationField obj1 = dfieldIo.readAsAntsField( dfieldA );
		final ANTSDeformationField obj2 = dfieldIo.readAsAntsField( dfieldB );

		final RandomAccessibleInterval< FloatType > dfield1 = obj1.getImg();
		final RandomAccessibleInterval< FloatType > dfield2 = obj2.getImg();
	
		// the interval (in physical space) on which to measure
		final RealInterval itvl = new FinalRealInterval( min, max );
		final RealRandomAccessible< FloatType > df1 = obj1.getDefField();
		final RealRandomAccessible< FloatType > df2 = obj2.getDefField();

		final ExecutorService exec = Executors.newFixedThreadPool( nThreads );
		ArrayList<Future<Stats>> futures = new ArrayList<>();
		for( int i = 0; i < nThreads; i++ )
		{
			final int j = i;
			futures.add( exec.submit( () -> {
				return compute(itvl, spacing, j, nThreads, df1, df2 );
			}));
		}

		exec.shutdown();
		exec.awaitTermination( 5, TimeUnit.DAYS );

		final Stats stats = aggregate( futures.stream().map( f -> {
			try
			{
				return f.get();
			}
			catch ( InterruptedException e )
			{
				e.printStackTrace();
			}
			catch ( ExecutionException e )
			{
				e.printStackTrace();
			}
			
			return null;
		} ).collect( Collectors.toList() ));

		System.out.println( "min error magnitude : " + stats.min );
		System.out.println( "avg error magnitude : " + stats.mean );
		System.out.println( "max error magnitude : " + stats.max );
		
		return null;
	}
	
	public static Stats compute( final RealInterval processingInterval, final double[] spacing,
			final int processIdx, final int numProcesses,
			final RealRandomAccessible<FloatType> df1, final RealRandomAccessible<FloatType> df2  )
	{
		final RealIntervalIterator it = new RealIntervalIterator( processingInterval, spacing );
		final RealRandomAccess< FloatType > ra1 = df1.realRandomAccess();
		final RealRandomAccess< FloatType > ra2 = df2.realRandomAccess();

		double avgErrMag = 0;
		double minErrMag = Double.MAX_VALUE;
		double maxErrMag = Double.MIN_VALUE;
		
		it.jumpFwd( processIdx );

		long i = 0 ;
		double[] errV = new double[ 3 ];
		while( it.hasNext() )
		{
			for( int j = 0; j < numProcesses && it.hasNext(); j++ )
			{
				it.fwd();
			}

			setPosition( it, ra1 );
			ra1.setPosition( 0, 3 );

			setPosition( it, ra2 );
			ra2.setPosition( 0, 3 );
			
			err( errV, ra1, ra2 );
			double errMag = Math.sqrt( errV[0]*errV[0] + errV[1]*errV[1] + errV[2]*errV[2] );

			avgErrMag += errMag;
	
			if( errMag < minErrMag )
				minErrMag = errMag;
			
			if( errMag > maxErrMag )
				maxErrMag = errMag;


			i++;
		}
		avgErrMag /= i;	
		
		return new Stats( avgErrMag, minErrMag, maxErrMag );
	}

	private static void setPosition( RealLocalizable l, RealPositionable p )
	{
		for( int i = 0; i < l.numDimensions(); i++ )
			p.setPosition( l.getDoublePosition( i ), i );
	}

	public static Stats aggregate( List<Stats> stats )
	{
		double mean = 0;
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;

		for( Stats s : stats )
		{
			System.out.println( s );
			if( s != null )
			{
				mean += s.mean;
				min = s.min < min ? s.min : min;
				max = s.max > max ? s.max : max;
			}
		}

		mean = mean / stats.size();
		return new Stats( mean, min, max );
	}
	
	public static class Stats{
		public final double mean;
		public final double min;
		public final double max;
		
		public Stats( final double mean, final double min, final double max )
		{
			this.mean = mean;
			this.min = min;
			this.max = max;
		}
		
		public String toString()
		{
			return String.format( "stats: %f %f %f", min, mean, max );
		}
	}

	public static <T extends RealType<T>,S extends RealType<S>> void err( double[] err, RealRandomAccess<T> truth, RealRandomAccess<S> approx )
	{
		for( int d = 0; d < 3; d++ )
		{
			truth.setPosition( d, 3 );
			approx.setPosition( d, 3 );
			err[ d ] = truth.get().getRealDouble() - approx.get().getRealDouble();
		}
	}
}
