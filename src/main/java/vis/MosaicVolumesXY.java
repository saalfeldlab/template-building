package vis;

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
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 * Creates a mosaic of N image volumes, tiling them in a X-Y grid.
 * 
 * Assumes all volumes are of the same size.
 * 
 */
public class MosaicVolumesXY
{

	public static void main( String[] args ) throws FormatException, IOException, ImgLibException
	{
		File outFile = new File( args[ 0 ] );
		List<File> volumeFileList = new LinkedList<File>();
		for( int i = 1; i < args.length; i++ )
		{
			File f = new File( args[i] );
			if( !f.exists() )
			{
				System.out.println("file " +  f + " does not exist ");
			}
			volumeFileList.add( f );
		}
		
		ImagePlusImg< FloatType, ? > bigimgout = run( volumeFileList );

		IJ.save( bigimgout.getImagePlus(), outFile.getAbsolutePath() );
	}

	public static  ImagePlusImg< FloatType, ? > run( List<File> volumeFileList ) throws FormatException, IOException
	{
		Img< FloatType > img = load( volumeFileList.get( 0 ) );
		 
		 int N = volumeFileList.size();

		 // allocate the big image
		 System.out.println("allocating...");

		 Interval interval = determineBigInterval( N, img );
		 System.out.println( Util.printInterval( interval ));
		 
		 ImagePlusImg< FloatType, ? > bigimg = allocate( determineBigInterval( N, img ), new FloatType() );

		 long[] offset = new long[ bigimg.numDimensions() ];
		 for( int i = 0; i < N; i++ )
		 {
			 if( i > 0 )
			 {
				 img = load( volumeFileList.get( i ) );
			 }
			 System.out.println("offset: " + offset[ 0 ] + " " + 
					 offset[ 1 ] + " " + 
					 offset[ 2 ] + " " + 
					 offset[ 3 ]);

			 System.out.println("copying image " + i + " ...");
			 copyToImageStack( img, bigimg, offset, 8 );
			 incrementOffsetXY( offset, bigimg, img );
		 }
		 return bigimg;
	}
	
	public static void incrementOffsetXY( long[] offset, Interval big, Interval small )
	{
		offset[ 0 ] += small.dimension( 0 );

		if( offset[ 0 ] >= big.dimension( 0 ))
		{
			offset[ 0 ] = 0;
			offset[ 1 ] += small.dimension( 1 );
		}
		// offset 1 should not ever exceed bounds
		// let's check explicitly
		if( offset[ 1 ] >= big.dimension( 1 ))
		{
			System.out.println( "We have a problem!" );
		}
	}
	
	public static Img< FloatType > load( File f ) throws FormatException, IOException
	{
		if( f.getAbsolutePath().endsWith( "nii" ) || 
			f.getAbsolutePath().endsWith( "nii.gz" ))
		{
			return ImageJFunctions.wrap( NiftiIo.readNifti( f ) );
		}
		else
		{
			return ImageJFunctions.wrap( IJ.openImage( f.getAbsolutePath() ));
		}
	}
	
	public static Interval determineBigInterval( int N, Interval interval )
	{
		long nx = interval.dimension( 0 );
		long ny = interval.dimension( 1 );
		long nz = interval.dimension( 2 );
		long nc = interval.dimension( 3 );
		long nt = interval.dimension( 4 );

		int tilesPerSide = (int)Math.ceil( Math.sqrt( (double) N ));

		int nd = 2;
		if( nz > 1 ){ nd++; }
		if( nc > 1 ){ nd++; }
		if( nt > 1 ){ nd++; }

		long[] dim = new long[ nd ];
		dim[ 0 ] = nx * tilesPerSide;
		dim[ 1 ] = ny * tilesPerSide;

		int d = 2;
		if( nz > 1 ){ dim[ d++ ] = nz; }
		if( nc > 1 ){ dim[ d++ ] = nc; }
		if( nt > 1 ){ dim[ d++ ] = nt; }

		return new FinalInterval( dim );
	}

	public static Interval determineBigInterval( int N, ImagePlus ip )
	{
		 int nx = ip.getWidth();
		 int ny = ip.getHeight();
		 int nz = ip.getNSlices();
		 int nc = ip.getNChannels();
		 int nt = ip.getNFrames();

		 int tilesPerSide = (int)Math.ceil( Math.sqrt( (double) N ));

		 int nd = 2;
		 if( nz > 1 ){ nd++; }
		 if( nc > 1 ){ nd++; }
		 if( nt > 1 ){ nd++; }

		 long[] dim = new long[ nd ];
		 dim[ 0 ] = nx * tilesPerSide;
		 dim[ 1 ] = ny * tilesPerSide;

		 int d = 2;
		 if( nz > 1 ){ dim[ d++ ] = nz; }
		 if( nc > 1 ){ dim[ d++ ] = nz; }
		 if( nt > 1 ){ dim[ d++ ] = nz; }
		 
		 return new FinalInterval( dim );
	}

	public static <T extends NativeType<T>> ImagePlusImg<T,?> allocate( Dimensions dim, T t )
	{
		 ImagePlusImgFactory< T > factory = new ImagePlusImgFactory< T >(); 
		 return factory.create( dim, t );
	}

	public static < T extends NumericType<T> >void copyToImageStack( 
			final RandomAccessibleInterval< T > src,
			final RandomAccessible<T> target,
			long[] offset,
			final int nThreads )
	{
		// what dimension should we split across?
		int nd = src.numDimensions();
		int tmp = nd - 1;
		while( tmp >= 0 )
		{
			if( src.dimension( tmp ) > 1 )
				break;
			else
				tmp--;
		}
		final int dim2split = tmp;

		final long[] splitPoints = new long[ nThreads + 1 ];
		long N = src.dimension( dim2split );
		long del = ( long )( N / nThreads ); 
		splitPoints[ 0 ] = src.min( dim2split );
		splitPoints[ nThreads ] = src.max( dim2split ) + 1;
		for( int i = 1; i < nThreads; i++ )
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
						final FinalInterval subItvl = getSubInterval( src, dim2split, start, end );
						final IntervalView< T > subTgt = Views.interval( target, subItvl );
						final Cursor< T > c = Views.flatIterable( src ).cursor();
						final RandomAccess< T > ra = target.randomAccess();
						while ( c.hasNext() )
						{
							c.fwd();
							ra.setPosition( c );
							ra.setPosition( ra.getLongPosition( 0 ) + offset[ 0 ], 0 );
							ra.setPosition( ra.getLongPosition( 1 ) + offset[ 1 ], 1 );
							ra.get().set( c.get() );
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