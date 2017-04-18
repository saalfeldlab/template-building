package process;

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
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class PerPixelMeanVariance
{

	public static void main( String[] args ) throws FormatException, IOException, ImgLibException
	{
		int nThreads = Integer.parseInt( args[ 0 ] );
		String outPath = args[ 1 ];
	
		String[] subjects = new String[ args.length - 2 ];
		System.arraycopy( args, 2, subjects, 0, subjects.length );


//		// create the output image
		FloatImagePlus< FloatType > meanOut = null;
		FloatImagePlus< FloatType > stdOut = null;
		
//		FinalInterval testInterval = new FinalInterval( 16, 16, 4 );
		
		int i = 0;
		for ( String s : subjects )
		{
			System.out.println( String.format( "subject %d of %d", ( i + 1 ), subjects.length ));
			ImagePlus ip = read( new File( s ) );
			Img<FloatType> img = ImageJFunctions.wrap( ip );
			
//			RandomAccessibleInterval< FloatType > img = getTestImg( i, testInterval);

			if( i == 0 )
			{
				long[] dims = Intervals.dimensionsAsLongArray( img );
				meanOut = ImagePlusImgs.floats( dims );
				stdOut = ImagePlusImgs.floats( dims );
			}

			onlineMeanVarianceUpdate( meanOut, stdOut, img, i, nThreads );
			i++;

			img = null;
			ip = null;
		}

		BuildPopulationAverage.divide( stdOut, new FloatType( (float)subjects.length ) );

		IJ.save( meanOut.getImagePlus(), outPath + "_mn.tif" );
		IJ.save( stdOut.getImagePlus(), outPath + "_var.tif"  );

		System.out.println( "finished" );
	}
	
	public static RandomAccessibleInterval< FloatType > getTestImg( int i, Interval interval )
	{
		return ConstantUtils.constantRandomAccessibleInterval( new FloatType( (float)i ), interval.numDimensions(), interval );
	}
	
	public static ImagePlus read( File f ) throws FormatException, IOException
	{
		ImagePlus ip;
		if( f.getName().endsWith( "nii" ))
			ip = NiftiIo.readNifti( f );
		else
			ip = IJ.openImage( f.getAbsolutePath() );

		return ip;
	}

	/**
	 * Updates mean and variance estimates online.
	 * 
	 * See 
	 * https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Online_algorithm
	 * 
	 * @param mean current mean estimate
	 * @param var current variance estimate
	 * @param x the current data
	 * @param index of the current subject ( 0-index )
	 */
	public static < T extends RealType<T>, S extends RealType<S>> void onlineMeanVarianceUpdate( 
			final RandomAccessibleInterval< T > mean,
			final RandomAccessible< T > var, 
			final RandomAccessible< S > x,
			final int i )
	{
		Cursor< T > c = Views.flatIterable( mean ).cursor();
		RandomAccess< T > vra = var.randomAccess();
		RandomAccess< S > xra = x.randomAccess();
		while ( c.hasNext() )
		{
			c.fwd();
			vra.setPosition( c );
			xra.setPosition( c );
			
			double mn = c.get().getRealDouble();
			double vr = vra.get().getRealDouble();
			double xval = xra.get().getRealDouble();
			double del = xval - mn;
			
			// update the mean
			mn += del / ( i + 1 );

			double del2 = xval - mn;

			c.get().setReal( mn );
			vra.get().setReal( vr + ( del * del2 ));
		}
	}
	
	/**
	 * Updates mean and variance estimates online.
	 * 
	 * See 
	 * https://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Online_algorithm
	 * 
	 * @param mean current mean estimate
	 * @param var current variance estimate
	 * @param x the current data
	 * @param index of the current subject ( 0-index )
	 */
	public static < T extends RealType<T>, S extends RealType<S>> void onlineMeanVarianceUpdate( 
			final RandomAccessibleInterval< T > mean,
			final RandomAccessible< T > var, 
			final RandomAccessible< S > x,
			final int i, final int nThreads )
	{
		System.out.println( String.format( "  updating with %d threads", nThreads ));
		ExecutorService threadPool = Executors.newFixedThreadPool( nThreads );
		
		// what dimension should we split across?
		int nd = mean.numDimensions();
		int tmp = nd - 1;
		while( tmp >= 0 )
		{
			if( mean.dimension( tmp ) > 1 )
				break;
			else
				tmp--;
		}
		final int dim2split = tmp;

		final long[] splitPoints = new long[ nThreads + 1 ];
		long N = mean.dimension( dim2split );
		long del = ( long )( N / nThreads ); 
		splitPoints[ 0 ] = mean.min( dim2split );
		splitPoints[ nThreads ] = mean.max( dim2split ) + 1;
		for( int j = 1; j < nThreads; j++ )
		{
			splitPoints[ j ] = splitPoints[ j - 1 ] + del;
//			System.out.println( "splitPoints[j]: " + splitPoints[ j ] ); 
		}

		LinkedList<Callable<Boolean>> jobs = new LinkedList<Callable<Boolean>>();
		
		for( int n = 0; n < nThreads; n++ )
		{
			final long start = splitPoints[ n ];
			final long end   = splitPoints[ n + 1 ];
			
			jobs.add( new Callable<Boolean>()
			{
				public Boolean call()
				{
					try
					{
						final FinalInterval subItvl = getSubInterval( mean, dim2split, start, end );
						final IntervalView< T > subMean = Views.interval( mean, subItvl );
						final IntervalView< T > subVar = Views.interval( var, subItvl );
						final IntervalView< S > subX = Views.interval( x, subItvl );

						Cursor< T > c = Views.flatIterable( subMean ).cursor();
						RandomAccess< T > vra = subVar.randomAccess();
						RandomAccess< S > xra = subX.randomAccess();
						while ( c.hasNext() )
						{
							c.fwd();
							vra.setPosition( c );
							xra.setPosition( c );
							
							double mn = c.get().getRealDouble();
							double vr = vra.get().getRealDouble();
							double xval = xra.get().getRealDouble();
							double del = xval - mn;

							// update the mean
							mn += del / ( i + 1 );				
							double del2 = xval - mn;

							c.get().setReal( mn );
							vra.get().setReal( vr + ( del * del2 ));
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

	public static < T extends RealType<T>, S extends RealType<S>, R extends RealType<R>> void addSquaredDiffTo( 
			RandomAccessibleInterval< T > add2me,
			RandomAccessible< S > sub, 
			RandomAccessible< R > mean)
	{
		Cursor< T > c = Views.flatIterable( add2me ).cursor();
		RandomAccess< S > sbra = sub.randomAccess();
		RandomAccess< R > mnra = mean.randomAccess();
		while ( c.hasNext() )
		{
			c.fwd();
			sbra.setPosition( c );
			mnra.setPosition( c );
			double diff = sbra.get().getRealDouble() - mnra.get().getRealDouble();
			double sqrdDiff = diff * diff;
			c.get().setReal( c.get().getRealDouble() + sqrdDiff );
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