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
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import transforms.AffineHelper;

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
		double[] translation = new double[]{ -50, -50, -50 };

		// upsample factors that make the final volume isotropic:
		// original data are at [ 0.1882689 x 0.1882689 x 0.38 ]um
		double[] factors2Iso = new double[]{ 1.0, 1.0, 2.018389654 };

		// TODO expose as parameter
		// This is necessary for now until I can reliably 
		// determine the interval after transformation to canonical orientation
		long[] renderMin = new long[]{ -700,  700,  250 };
		long[] renderMax = new long[]{ 3200, 2600, 1500 };
		FinalInterval renderInterval = new FinalInterval( renderMin, renderMax );

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
		
		String toNormalF = "";
		AffineTransform3D toNormal = null;
		if( args.length >= 7 )
		{
			toNormalF = args[6];
			System.out.println("tonormal: " + toNormalF );
			AffineTransform3D tmp = AffineHelper.to3D( AffineImglib2IO.readXfm( 3, new File( toNormalF )));
			toNormal = AffineHelper.centeredRotation( tmp , destInterval );
		}

		AffineTransform toIso = new AffineTransform( 3 );
		toIso.set( factors2Iso[ 0 ], 0, 0 );
		toIso.set( factors2Iso[ 1 ], 1, 1 );
		toIso.set( factors2Iso[ 2 ], 2, 2 );

		AffineTransform up3dNoShift = new AffineTransform( 3 );
		up3dNoShift.set( factors[ 0 ], 0, 0 );
		up3dNoShift.set( factors[ 1 ], 1, 1 );
		up3dNoShift.set( factors[ 2 ], 2, 2 );

		AffineTransform up3d = new AffineTransform( 3 );
		up3d.set( factors[ 0 ], 0, 0 );
		up3d.set( factors[ 1 ], 1, 1 );
		up3d.set( factors[ 2 ], 2, 2 );
		up3d.set( 4, 0, 3 );
		up3d.set( 4, 1, 3 );
		up3d.set( 2, 2, 3 );

		AffineTransform translate = new AffineTransform( 3 );
		translate.set( translation[ 0 ], 0, 3 );
		translate.set( translation[ 1 ], 1, 3 );
		translate.set( translation[ 2 ], 2, 3 );

		AffineTransform down3d = up3d.inverse();

		/*
		 * READ THE TRANSFORM
		 */
		AffineTransform totalAffine = null;
		if ( !flipF.equals( "none" ) )
		{
			System.out.println( "flip affine" );
			AffineTransform flipAffine = AffineImglib2IO.readXfm( 3, new File( flipF ) );
			totalAffine = flipAffine.inverse().copy();
		}
		else
		{
			totalAffine = down3d.copy();
		}

		// The affine part
		AffineTransform3D affine = null;
		if ( affineF != null )
		{
			affine = ANTSLoadAffine.loadAffine( affineF );
			affine.concatenate( translate );
		}
		totalAffine.preConcatenate( affine.inverse() );

		ImagePlus ip = NiftiIo.readNifti( new File( warpF ) );
		Img< FloatType > defLowImg = ImageJFunctions.convertFloat( 
				ip );

		System.out.println( defLowImg );

		// the deformation
		ANTSDeformationField df = null;
		if ( warpF != null )
		{
			System.out.println( "loading warp - factors 1 1 1" );
			df = new ANTSDeformationField( defLowImg, new double[]{1,1,1}, ip.getCalibration().getUnit() );
			System.out.println( df.getDefInterval() );
		}

		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		if ( affine != null )
			totalXfm.add( totalAffine );

		if ( df != null )
			totalXfm.add( df );

		totalXfm.add( up3dNoShift );
		if( toNormal != null )
		{
			totalXfm.add( toNormal );
		}
		totalXfm.add( toIso );

		// LOAD THE IMAGE
		ImagePlus bip = IJ.openImage( imF );
		Img< ShortType > baseImg = ImageJFunctions.wrap( bip );

//		IntervalView< ShortType > imgHiXfm =
//			Views.interval( Views.raster( 
//					RealViews.transform(
//							Views.interpolate( Views.extendZero( baseImg ), new NLinearInterpolatorFactory<ShortType>() ),
//							totalXfm )),
//					destInterval );
		
//		Bdv bdv = BdvFunctions.show( imgHiXfm, "img hi xfm" );
//		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 0 ).setRange( 0, 1200 );

//		System.out.println("saving to: " + outF );
//		IJ.save( ImageJFunctions.wrap( imgHiXfm, "imgLoXfm" ), outF );
		
		

//		System.out.println("transforming");
//		RandomAccessibleOnRealRandomAccessible< ShortType > imgHiXfm = Views.raster( 
//				RealViews.transform(
//						Views.interpolate( Views.extendZero( baseImg ), new NLinearInterpolatorFactory<ShortType>() ),
//						totalXfm ));
//		
//		System.out.println("allocating");
//		ShortImagePlus< ShortType > out = ImagePlusImgs.shorts( destInterval.dimension( 0 ),
//				destInterval.dimension( 1 ),
//				destInterval.dimension( 2 ));
//		
//		IntervalView< ShortType > outTranslated = Views.translate( out,
//				destInterval.min( 0 ),
//				destInterval.min( 1 ),
//				destInterval.min( 2 ));
//		
//		System.out.println("copying with 8 threads");
//		render( imgHiXfm, outTranslated, 8 );
//
//		try
//		{
//			System.out.println("saving to: " + outF );
//			IJ.save( out.getImagePlus(), outF );
//		}
//		catch ( ImgLibException e )
//		{
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
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


}
