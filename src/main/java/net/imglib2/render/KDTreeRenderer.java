package net.imglib2.render;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.DoubleUnaryOperator;

import net.imglib2.render.RenderPointsPsf.TransformAndBox;
import org.janelia.TbarPrediction;
import org.janelia.TbarPrediction.TbarCollection;

import bigwarp.BigWarpExporter;
import ij.IJ;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.neighborsearch.RBFInterpolator;
import net.imglib2.neighborsearch.RadiusNeighborSearch;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class KDTreeRenderer<P extends RealLocalizable>
{
	
	static final double searchDist = 15;
	
	final KDTree< DoubleType > tree;
	
	public KDTreeRenderer( List<DoubleType> vals, List<P> pts )
	{
		tree = new KDTree< DoubleType >( vals, pts );
	}

	public RandomAccessibleInterval<DoubleType> getImage( final Interval interval,
			final double searchDist,
			final DoubleUnaryOperator rbf )
	{
		RadiusNeighborSearch< DoubleType > search =
	            new RadiusNeighborSearchOnKDTree<DoubleType>( tree );

		RBFInterpolator.RBFInterpolatorFactory<DoubleType> interp = 
				new RBFInterpolator.RBFInterpolatorFactory<DoubleType>( 
						rbf, searchDist, false, new DoubleType() );

		RealRandomAccessible< DoubleType > realRandomAccessible =
	            Views.interpolate( search, interp );
		
		return Views.interval( 
				Views.raster( realRandomAccessible ), interval );
	}
	
	public static double linearRbf( final double r )
	{
		if( r > searchDist )
			return 0;
		else
			return 1 -  (r / searchDist );
	}
	
	public static < T extends RealType<T>, S extends RealType<S> > void copyToImageStack( 
			final RandomAccessible< T > raible,
			final Interval itvl,
			final RandomAccessibleInterval< S > target,
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
		splitPoints[ 0 ] = 0;
		splitPoints[ nThreads ] = target.dimension( dim2split );
		for( int i = 1; i < nThreads; i++ )
		{
			splitPoints[ i ] = splitPoints[ i - 1 ] + del;
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
						final FinalInterval subItvl = BigWarpExporter.getSubInterval( target, dim2split, start, end );
						final IntervalView< S > subTgt = Views.interval( target, subItvl );
						final Cursor< S > c = subTgt.cursor();
						final RandomAccess< T > ra = raible.randomAccess();
						while ( c.hasNext() )
						{
							c.fwd();
							ra.setPosition( c );
							c.get().setReal( ra.get().getRealDouble() );
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

	public static void main( String[] args ) throws ImgLibException
	{

		String path = "/data-ssd/john/flyem/synapses.ser";
//		String path = "/data-ssd/john/flyem/small_synapses.ser";

		System.out.println("reading tbars + synapses" );
//		TbarCollection tbars = TbarPrediction.loadAll( path );
		TbarCollection tbars = TbarPrediction.loadSerialized( path );
		long[] min = tbars.min;
		long[] max = tbars.max;
		
		// in microns
		AffineTransform3D fibsemResolution = new AffineTransform3D();
		fibsemResolution.scale( 0.008 ); // 8 nm
		
		AffineTransform3D lmResolution = new AffineTransform3D();
//		lmResolution.set( 0.1882689, 0, 0 );
//		lmResolution.set( 0.1882689, 1, 1 );
//		lmResolution.set( 0.38, 2, 2 );
		
		lmResolution.set( 0.2, 0, 0 );
		lmResolution.set( 0.2, 1, 1 );
		lmResolution.set( 0.2, 2, 2 );

		TransformAndBox xfmAndBox = RenderPointsPsf.transformAndBox( fibsemResolution, lmResolution, 
				min, max );
		AffineTransform3D pt2imageXfm = xfmAndBox.pt2imageXfm;
		long[] outputDims = xfmAndBox.outputDims;
		
		System.out.println("building the tree" );

		List< RealPoint > pts = TbarPrediction.transformPoints( tbars.getPoints(), pt2imageXfm );
		KDTreeRenderer<RealPoint> treeRenderer = new KDTreeRenderer<RealPoint>( tbars.getValues( 0 ), pts );
		
		System.out.println( pts.get( 0 ));
		RandomAccessibleInterval< DoubleType > img = treeRenderer.getImage( 
				new FinalInterval( outputDims ),
				searchDist, 
				KDTreeRenderer::linearRbf );
		
		RandomAccess< DoubleType > ra = img.randomAccess();
		ra.setPosition( 
				new int[]{ 
						(int)pts.get(0).getDoublePosition( 0 ),
						(int)pts.get(0).getDoublePosition( 1 ),
						(int)pts.get(0).getDoublePosition( 2 ) } );
		
		System.out.println( ra.get() );
		
//		RandomAccessibleInterval< FloatType > floatimg = Converters.convert( 
//				 img, 
//				 new RealFloatConverter< DoubleType >(),
//				 new FloatType());
		
		System.out.println("copying" );
		FloatImagePlus< FloatType > result = ImagePlusImgs.floats( 
				Intervals.dimensionsAsLongArray( img ) );
		copyToImageStack( img, result, result, 1 );

		System.out.println("writing" );
		IJ.save( result.getImagePlus(), "/data-ssd/john/flyem/testbig_r" + searchDist + ".tif" );
		
		System.out.println("done" );
	}

}
