package process;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.janelia.utility.parse.ParseUtils;

import ij.IJ;
import ij.ImagePlus;
import io.AffineImglib2IO;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.img.imageplus.ShortImagePlus;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;

public class RenderRegAtHiResDefField
{

	public static void main( String[] args ) throws FormatException, IOException
	{

		String imF = args[ 0 ];
		String affineF = args[ 1 ];
		String flipF = args[ 2 ];
		String warpF = args[ 3 ];
		String outSz = args[ 4 ];
		String outF = args[ 5 ];

		// TODO expose this as a parameter
		double[] factors = new double[]{ 8, 8, 4 };

		
		FinalInterval destInterval =  null;
		if( outSz.contains( ":" ))
		{
			String[] minMax = outSz.split( ":" );
			System.out.println( " " + minMax[ 0 ] );
			System.out.println( " " + minMax[ 1 ] );

			long[] min = ParseUtils.parseLongArray( minMax[ 0 ] );
			long[] max = ParseUtils.parseLongArray( minMax[ 1 ] );
			destInterval = new FinalInterval( min, max );
		}
		else
		{
			long[] outputSize = ParseUtils.parseLongArray( outSz );
			destInterval = new FinalInterval( outputSize );	
		}
		

		AffineTransform up3d = new AffineTransform( 3 );
		up3d.set( factors[ 0 ], 0, 0 );
		up3d.set( factors[ 1 ], 1, 1 );
		up3d.set( factors[ 2 ], 2, 2 );
		up3d.set( 4, 0, 3 );
		up3d.set( 4, 1, 3 );
		up3d.set( 2, 2, 3 );

		AffineTransform down3d = up3d.inverse();

		/*
		 * READ THE TRANSFORM
		 */
		AffineTransform totalAffine = null;
		if ( !flipF.equals( "none" ) )
		{
			System.out.println( "flip affine" );
			AffineTransform flipAffine = AffineImglib2IO.readXfm( 3, new File( flipF ) );
			totalAffine = flipAffine.inverse();
		} else
		{
			System.out.println( "raw down" );
			totalAffine = down3d.copy();
		}

		// The affine part
		AffineTransform3D affine = null;
		if ( affineF != null )
		{
			affine = ANTSLoadAffine.loadAffine( affineF );
		}
		totalAffine.preConcatenate( affine.inverse() );

		Img< FloatType > defLowImg = ImageJFunctions.wrap( 
				NiftiIo.readNifti( new File( warpF ) ) );
		System.out.println( defLowImg );

		// the deformation
		ANTSDeformationField df = null;
		if ( warpF != null )
		{
			System.out.println( "loading warp - factors 1 1 1" );
			df = new ANTSDeformationField( defLowImg, new double[]{1,1,1} );
			System.out.println( df.getDefInterval() );
		}

		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		if ( affine != null )
			totalXfm.add( totalAffine );

		if ( df != null )
			totalXfm.add( df );

		totalXfm.add( up3d );

		// LOAD THE IMAGE
		ImagePlus bip = IJ.openImage( imF );
		Img< ShortType > baseImg = ImageJFunctions.wrap( bip );

//		IntervalView< FloatType > imgHiXfm =
//			Views.interval( Views.raster( 
//					RealViews.transform(
//							Views.interpolate( Views.extendZero( baseImg ), new NLinearInterpolatorFactory<FloatType>() ),
//							totalXfm )),
//					destInterval );
		
//		Bdv bdv = BdvFunctions.show( imgHiXfm, "img hi xfm" );
//		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 0 ).setRange( 0, 1200 );

//		System.out.println("saving to: " + outF );
//		IJ.save( ImageJFunctions.wrap( imgHiXfm, "imgLoXfm" ), outF );
		
		System.out.println("transforming");
		RandomAccessibleOnRealRandomAccessible< ShortType > imgHiXfm = Views.raster( 
				RealViews.transform(
						Views.interpolate( Views.extendZero( baseImg ), new NLinearInterpolatorFactory<ShortType>() ),
						totalXfm ));
		
		System.out.println("allocating");
		ShortImagePlus< ShortType > out = ImagePlusImgs.shorts( destInterval.dimension( 0 ),
				destInterval.dimension( 1 ),
				destInterval.dimension( 2 ));
		
		IntervalView< ShortType > outTranslated = Views.translate( out,
				destInterval.min( 0 ),
				destInterval.min( 1 ),
				destInterval.min( 2 ));
		
		System.out.println("copying with 8 threads");
		render( imgHiXfm, outTranslated, 8 );

		try
		{
			System.out.println("saving to: " + outF );
			IJ.save( out.getImagePlus(), outF );
		}
		catch ( ImgLibException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static < T extends NumericType<T> > RandomAccessibleInterval<T> copyToImageStack( 
			final RandomAccessible< T > ra,
			final Interval itvl,
			final RandomAccessibleInterval<T> target,
			final int nThreads )
	{
		// TODO I wish I didn't have to do this inside this method
//		MixedTransformView< T > raible = Views.permute( ra, 2, 3 );
		RandomAccessible< T > raible = ra;

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
		catch ( InterruptedException e1 )
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
	
	public static <T extends RealType<T>> void render( 
			RandomAccessible<T> src,
			RandomAccessibleInterval<T> tgt, 
			int nThreads )
	{
		copyToImageStack( src, tgt, tgt, nThreads );
	}

	@SuppressWarnings("unchecked")
	public static <T extends RealType<T>> RandomAccessibleInterval<T> defField3dUp( 
			RandomAccessibleInterval<T> defField, 
			double[] factors )
	{
		T t = Views.flatIterable( defField ).firstElement().copy();

		Converter< T, T > convX = new Converter< T, T >()
		{
			@Override
			public void convert( T input, T output )
			{
				output.set( input );
				output.mul( factors[ 0 ] );
			}
		};

		Converter< T, T > convY = new Converter< T, T >()
		{
			@Override
			public void convert( T input, T output )
			{
				output.set( input );
				output.mul( factors[ 1 ] );
			}
		};

		Converter< T, T > convZ = new Converter< T, T >()
		{
			@Override
			public void convert( T input, T output )
			{
				output.set( input );
				output.mul( factors[ 2 ] );
			}
		};

		RandomAccessibleInterval< T > xpos = Converters.convert( 
					Views.hyperSlice( defField, 3, 0 ), convX, t.copy());

		RandomAccessibleInterval< T > ypos = Converters.convert( 
				Views.hyperSlice( defField, 3, 1 ), convY, t.copy());

		RandomAccessibleInterval< T > zpos = Converters.convert( 
				Views.hyperSlice( defField, 3, 2 ), convZ, t.copy());

		return Views.stack( xpos, ypos, zpos );
	}

}
