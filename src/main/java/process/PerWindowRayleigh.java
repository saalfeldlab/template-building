package process;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.janelia.utility.parse.ParseUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import ij.IJ;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.neighborhood.RectangleShape.NeighborhoodsIterableInterval;
import net.imglib2.algorithm.stats.RayleighDistribution;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.img.list.ListImg;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import util.RenderUtil;


/**
 * Makes a sorted plot of image intensities in the given interval
 *
 */
public class PerWindowRayleigh
{
	protected transient JCommander jCommander;

	@Parameter(names = {"--width", "-w"}, description = "Width of interval", 
			converter = ParseUtils.LongArrayConverter.class )
	protected long[] widthIn = new long[]{ 1 };
	
	@Parameter(names = {"--output", "-o"}, description = "Outputfile", required=true)
	protected String outputPath;

	@Parameter(names = {"--nThreads", "-q"}, description = "Number of threads" )
	protected int nThreads;

	@Parameter( description="input images")
	protected List<String> imagePathList;

	protected Interval window;

	public void process() throws IOException, ImgLibException
	{
		System.out.println( "inputs: " + imagePathList );
		System.out.println( "output: " + outputPath );

		int i = 0;
		ListImg<RayleighDistribution> distImg = null;
		for( String imgPath : imagePathList )
		{
			Img<FloatType> img = ImageJFunctions.convertFloat( IJ.openImage( imgPath ));
			if( i == 0 )
			{
				System.out.println( "initializing" );
				ArrayList<RayleighDistribution> list = new ArrayList<RayleighDistribution>( (int)Intervals.numElements( img ) ); 
				for( int j = 0; j < Intervals.numElements( img ); j++ )
					list.add( new RayleighDistribution());
				
				System.out.println( "creating img" );
				distImg = new ListImg<RayleighDistribution>( list, Intervals.dimensionsAsLongArray( img ));
			}
			System.out.println( String.format( "image %d of %d", (i+1), imagePathList.size() ));
			System.out.println( imgPath );
			update( img, distImg );
			i++;
		}
		System.out.println( "finalizing" );
		FloatImagePlus< FloatType > ipImgOut = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( distImg ) );
		getSigmaField( distImg, ipImgOut );
		System.out.println( "writing" );
		IJ.save( ipImgOut.getImagePlus(), outputPath );
	}
	
	/**
	 * Going to leave this as a float, since writing this into a byte or short would be silly
	 */
	public void getSigmaField( 
			RandomAccessibleInterval< RayleighDistribution > localDist,
			RandomAccessibleInterval< FloatType > target )
	{
		// what dimension should we split across?
		int nd = localDist.numDimensions();
		int tmp = nd - 1;
		while( tmp >= 0 )
		{
			if( localDist.dimension( tmp ) > 1 )
				break;
			else
				tmp--;
		}
		final int dim2split = tmp;
		final long[] splitPoints = RenderUtil.splitPoints( nThreads, dim2split, localDist );

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
						final FinalInterval subItvl = RenderUtil.getSubInterval( target, dim2split, start, end );
						final IntervalView< FloatType > subTgt = Views.interval( target, subItvl );
						final Cursor< FloatType > c = subTgt.cursor();
						RandomAccess< RayleighDistribution > distRa = localDist.randomAccess();
						while ( c.hasNext() )
						{
							c.fwd();
							distRa.setPosition( c );
							c.get().set( (float) distRa.get().getSigma() );
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
	
	public <T extends RealType<T>> void update( 
			RandomAccessibleInterval<T> img,
			RandomAccessibleInterval< RayleighDistribution > localDist )
	{
		// what dimension should we split across?
		int nd = localDist.numDimensions();
		int tmp = nd - 1;
		while( tmp >= 0 )
		{
			if( localDist.dimension( tmp ) > 1 )
				break;
			else
				tmp--;
		}
		final int dim2split = tmp;
		final long[] splitPoints = RenderUtil.splitPoints( nThreads, dim2split, localDist );

		ExecutorService threadPool = Executors.newFixedThreadPool( nThreads );
		LinkedList<Callable<Boolean>> jobs = new LinkedList<Callable<Boolean>>();
		for( int i = 0; i < nThreads; i++ )
		{
			final long start = splitPoints[ i ];
			final long end   = splitPoints[ i+1 ];

			final RectangleShape shape = new RectangleShape( (int)widthIn[0], false );
			

			jobs.add( new Callable<Boolean>()
			{
				public Boolean call()
				{
					try
					{
						
						final FinalInterval subItvl = RenderUtil.getSubInterval( img, dim2split, start, end );
						final NeighborhoodsIterableInterval< T > neighborhoods = shape.neighborhoods( 
								Views.interval( Views.extendZero( img ), subItvl ));
						
						Cursor< Neighborhood< T > > c = neighborhoods.cursor();
						RandomAccess< RayleighDistribution > distRa = localDist.randomAccess();
						
						while ( c.hasNext() )
						{
							c.fwd();
							distRa.setPosition( c );
							distRa.get().onlineUpdateFit( c.get() );
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

	protected void initCommander()
	{
		jCommander = new JCommander( this );
		jCommander.setProgramName( "input parser" ); 
	}

	public static PerWindowRayleigh parseCommandLineArgs( final String[] args )
	{
		PerWindowRayleigh ds = new PerWindowRayleigh();
		ds.initCommander();
		try 
		{
			ds.jCommander.parse( args );
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		return ds;
	}

	/**
	 * Makes 
	 * @param args
	 * @throws IOException 
	 * @throws ImgLibException 
	 */
	public static void main( String[] args ) throws IOException, ImgLibException
	{
		PerWindowRayleigh plotter = PerWindowRayleigh.parseCommandLineArgs( args );
		plotter.process();
	}
}
