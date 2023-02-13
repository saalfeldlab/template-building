package process;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import ij.ImagePlus;
import io.DfieldIoHelper;
import io.IOHelper;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.iterator.RealIntervalIterator;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Option;

public class DfieldComparisons implements Callable< Void >
{
	// TODO combine functionality with evaluation.TransformComparison


	@Option( names = { "-a" }, description = "Input displacement Field A" )
	private String dfieldA;

	@Option( names = { "-b" }, description = "Input displacement Field B" )
	private String dfieldB;

	@Option( names = { "-n", "--min" }, split = ",", required = false, description = "Minimum of field of view. Defaults to the origin." )
	private double[] min;

	@Option( names = { "-x", "--max" }, split = ",", required = false, description = "Maximum of field of view. Defaults to the origin." )
	private double[] max;

	@Option( names = { "-s", "--spacing" }, required = false, split = ",", description = "The spacing at which to compute the comparison" )
	private double[] spacing;

	@Option( names = { "-d", "--difference" }, required = false, description = "" )
	private String differenceOutput;

	@Option( names = { "-j", "--nThreads" }, required = false, description = "Number of threads (default=1)" )
	private int nThreads = 1;

	@Option( names = { "-h", "--help" }, required = false, description = "Prints a help message." )
	private boolean help;

	private ANTSDeformationField obj1;
	private ANTSDeformationField obj2;

	private FinalRealInterval rItvl;
	private Interval itvl;

	public static void main( String[] args ) throws Exception
	{
		new CommandLine( new DfieldComparisons() ).execute( args );
	}

	public void setup() throws Exception
	{
		final DfieldIoHelper dfieldIo = new DfieldIoHelper();
		obj1 = dfieldIo.readAsAntsField( dfieldA );
		obj2 = dfieldIo.readAsAntsField( dfieldB );
		
		if( max == null )
		{
			spacing = obj1.getResolution();
			
			final long[] dimsTmp = obj1.getDefInterval().dimensionsAsLongArray();
			max = new double[ spacing.length ];
			for( int i = 0; i < max.length; i++ )
			{
				max[ i ] = spacing[ i ] * dimsTmp[ i ];
			}
		}

		if( min == null )
		{
			min = new double[ max.length ];
		}
		System.out.println( "min: " + Arrays.toString( min ));
		System.out.println( "max: " + Arrays.toString( max ));
		System.out.println( "spc: " + Arrays.toString( spacing ));

		// the interval (in physical space) on which to measure
		rItvl = new FinalRealInterval( min, max );
		itvl = containingDiscreteInterval( rItvl, spacing );
	}
	
	public Interval containingDiscreteInterval( RealInterval rItvl, double[] spacing )
	{
		final long[] dims = new long[ rItvl.numDimensions() ];
		for( int i = 0; i < rItvl.numDimensions(); i++ )
		{
			dims[ i ] = (long) Math .floor( (rItvl.realMax( i ) - rItvl.realMin( i )) / spacing[ i ] );
		}
		return new FinalInterval( dims );
	}

	@Override
	public Void call() throws Exception
	{
		setup();

		final RealRandomAccessible< FloatType > df1 = obj1.getDefField();
		final RealRandomAccessible< FloatType > df2 = obj2.getDefField();
		
		final Img< FloatType > diffImg;
		if( differenceOutput != null && !differenceOutput.isEmpty())
		{
//			final Interval diffItvl = Intervals.smallestContainingInterval( itvl );
			final Interval diffItvl = containingDiscreteInterval( itvl, spacing );
			diffImg = Util.getSuitableImgFactory( diffItvl , new FloatType() ).create( diffItvl );
			System.out.println( "diffImg: " + Intervals.toString(diffImg));
		}
		else
			diffImg = null;

		final ExecutorService exec = Executors.newFixedThreadPool( nThreads );
		ArrayList<Future<Stats>> futures = new ArrayList<>();
		for( int i = 0; i < nThreads; i++ )
		{
			final int j = i;
			futures.add( exec.submit( () -> {
				return compute(itvl, spacing, j, nThreads, df1, df2, diffImg );
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
		
		if( diffImg != null )
		{
			final ImagePlus diffImp = ImageJFunctions.wrap( diffImg, "diff" );
			IOHelper.write( diffImp, differenceOutput );
		}
		
		return null;
	}
	
	public static <T extends RealType<T>> Stats compute( final Interval processingInterval, final double[] spacing,
			final int processIdx, final int numProcesses,
			final RealRandomAccessible<T> df1, final RealRandomAccessible<T> df2,
			final RandomAccessibleInterval<T> diff )
	{
//		final RealIntervalIterator it = new RealIntervalIterator( processingInterval, spacing );
		final IntervalIterator it = new IntervalIterator( processingInterval );
		
		final RealRandomAccess< T > ra1 = df1.realRandomAccess();
		final RealRandomAccess< T > ra2 = df2.realRandomAccess();
		
		boolean doDiff = diff != null;
		RandomAccess< T > diffRa = null;
		if( doDiff )
			diffRa = diff.randomAccess();

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

			setPosition( it, spacing, ra1 );
			ra1.setPosition( 0, 3 );

			setPosition( it, spacing, ra2 );
			ra2.setPosition( 0, 3 );
			
			err( errV, ra1, ra2 );
			double errMag = Math.sqrt( errV[0]*errV[0] + errV[1]*errV[1] + errV[2]*errV[2] );
			if( doDiff )
			{
				diffRa.setPosition( it );
				diffRa.get().setReal( errMag );
			}

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
	
	private static void setPosition( Localizable l, double[] scale, RealPositionable p )
	{
		for( int i = 0; i < p.numDimensions(); i++ )
		{
			p.setPosition( scale[ i ] * l.getDoublePosition( i ), i );
		}
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
