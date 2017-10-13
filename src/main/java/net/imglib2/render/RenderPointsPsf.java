package net.imglib2.render;

import java.util.*;

import org.janelia.TbarPrediction;
import org.janelia.TbarPrediction.TbarCollection;

import ij.IJ;
import net.imglib2.*;
import net.imglib2.RandomAccess;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.imageplus.*;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.*;
import net.imglib2.type.numeric.real.*;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class RenderPointsPsf
{

	public static final long padAmt = 15;
	public static final double confidenceThresh = 0.85;
	
	public static void main( String[] args ) throws ImgLibException
	{
		// the psf (at the resolution of the lm space)
		FloatImagePlus< FloatType > psf = ImagePlusImgs.floats( 9, 9, 9 );
		IntervalView< FloatType > centeredPsf = Views.offset( psf, 
				( psf.dimension( 0 ) - 1) / 2,
				( psf.dimension( 1 ) - 1) / 2,
				( psf.dimension( 2 ) - 1) / 2 );
		
		long[] psfIntervalMin = new long[] {
					-( psf.dimension( 0 ) - 1) / 2,
					-( psf.dimension( 0 ) - 1) / 2,
					-( psf.dimension( 0 ) - 1) / 2 };
		
		long[] psfIntervalMax = new long[] {
					( psf.dimension( 0 ) - 1) / 2,
					( psf.dimension( 0 ) - 1) / 2,
					( psf.dimension( 0 ) - 1) / 2 };
		
		// the interval over which to apply the psf
		FinalInterval psfInterval = new FinalInterval( psfIntervalMin, psfIntervalMax );
				
		
		gaussianPsf( centeredPsf, 50, new double[]{ 2, 2, 2 });
//		quadraticPsf( centeredPsf, 50 );
//		linearPsf( centeredPsf, 50 );
		
//		psf.getImagePlus().show();
		IJ.save( psf.getImagePlus(), "/groups/saalfeld/home/bogovicj/tmp/psf_gaus2.tif" );

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


//		RealPoint p = new RealPoint( 13247, 2557, 50 );
//		RealPoint q = new RealPoint( 3 );
//		
//		pt2imageXfm.apply( p, q );
//		System.out.println( q );
		
//		String path = "/data-ssd/john/flyem/small_tbars2.json";
//		String path = "/data-ssd/john/flyem/test_tbars2.json";

//		String path = "/data-ssd/john/flyem/test_tbars2.ser";
		String path = "/data-ssd/john/flyem/synapses.ser";

		System.out.println("reading tbars + synapses" );
//		TbarCollection tbars = TbarPrediction.loadAll( path );
		TbarCollection tbars = TbarPrediction.loadSerialized( path );
		final long[] min = tbars.min;
		final long[] max = tbars.max;
		System.out.println("done" );
		
		TransformAndBox xfmAndBox = transformAndBox( fibsemResolution, lmResolution, 
				min, max );
		AffineTransform3D pt2imageXfm = xfmAndBox.pt2imageXfm;
		long[] outputDims = xfmAndBox.outputDims;
		
		System.out.println( pt2imageXfm );
		
//		// add the translation such that the point at the min value maps to [padAmt, padAmt, patAmt]
//		double[] res = new double[ 3 ];
//		
//		pt2imageXfm.apply( minMicrons, res );
//		pt2imageXfm.set( padAmt - tmp[ 0 ], 0, 3 );
//		pt2imageXfm.set( padAmt - tmp[ 1 ], 1, 3 );
//		pt2imageXfm.set( padAmt - tmp[ 2 ], 2, 3 );
//		
//		toDouble( min, tmp );
//		pt2imageXfm.apply( tmp, res );
//		System.out.println( "min res: " + Arrays.toString( res ));
//		
//		toDouble( max, tmp );
//		pt2imageXfm.apply( tmp, res );
//		System.out.println( "max res: " + Arrays.toString( res ));
		
		// allocate the output image
		FloatImagePlus< FloatType > img = ImagePlusImgs.floats( outputDims );
		
		// the point list
		ArrayList<TbarPrediction> ptlist = tbars.list;
		
		// render
		System.out.println("rendering");
		render( ptlist, pt2imageXfm, img,
				Views.interpolate( Views.extendZero( centeredPsf ), new NLinearInterpolatorFactory< FloatType >() ), 
				psfInterval );
		
//		img.getImagePlus().show();
		IJ.save( img.getImagePlus(), "/data-ssd/john/flyem/test_tbars2_render-iso-psf-conf85-sig2.tif" );

	}

	public static TransformAndBox transformAndBox(
			AffineTransform3D fibsemResolution, 
			AffineTransform3D lmResolution,
			long[] min,
			long[] max )
	{
		AffineTransform3D pt2imageXfm = new AffineTransform3D();
		pt2imageXfm.preConcatenate( fibsemResolution );
		pt2imageXfm.preConcatenate( lmResolution.inverse() );

		/* 
		 * compute the bounding box in pixels given that the min and max above are
		 * in fibsem space, and and output pixels are in light-microscopy space
		 */
		double[] minMicrons = new double[]{ 
				min[ 0 ] * fibsemResolution.get( 0, 0 ) / lmResolution.get( 0, 0 ),
				min[ 1 ] * fibsemResolution.get( 1, 1 ) / lmResolution.get( 1, 1 ),
				min[ 2 ] * fibsemResolution.get( 2, 2 ) / lmResolution.get( 2, 2 ) };

		double[] maxMicrons = new double[]{ 
				max[ 0 ] * fibsemResolution.get( 0, 0 ) / lmResolution.get( 0, 0 ),
				max[ 1 ] * fibsemResolution.get( 1, 1 ) / lmResolution.get( 1, 1 ),
				max[ 2 ] * fibsemResolution.get( 2, 2 ) / lmResolution.get( 2, 2 ) };

		System.out.println( "minMicrons: " + Arrays.toString( minMicrons ));
		System.out.println( "maxMicrons: " + Arrays.toString( maxMicrons ));
		
//		double[] tmp = new double[ 3 ];

//		toDouble( min, tmp );
//		pt2imageXfm.apply( tmp, minMicrons );
//		
//		toDouble( max, tmp );
//		pt2imageXfm.apply( tmp, maxMicrons );
//		
//		System.out.println( "minMicrons: " + Arrays.toString( minMicrons ));
//		System.out.println( "maxMicrons: " + Arrays.toString( maxMicrons ));

		long minMicronsx = (long)Math.floor(minMicrons[ 0 ]);
		long minMicronsy = (long)Math.floor(minMicrons[ 1 ]);
		long minMicronsz = (long)Math.floor(minMicrons[ 2 ]);
		
		long[] outputDims = new long[]{
					( 2 * padAmt ) + (long)Math.ceil( maxMicrons[ 0 ] - minMicronsx + 1),
					( 2 * padAmt ) + (long)Math.ceil( maxMicrons[ 1 ] - minMicronsy + 1),
					( 2 * padAmt ) + (long)Math.ceil( maxMicrons[ 2 ] - minMicronsz + 1 ) };

		System.out.println( "outputDims: " + Arrays.toString( outputDims ));


		AffineTransform3D tmpTranslations = new AffineTransform3D();
		tmpTranslations.setTranslation( -min[0], -min[1], -min[2] );
		pt2imageXfm.concatenate( tmpTranslations );
		
		tmpTranslations.setTranslation( padAmt, padAmt, padAmt );
		pt2imageXfm.preConcatenate( tmpTranslations );
		
		return new TransformAndBox( pt2imageXfm, outputDims );
	}
	
	public static class TransformAndBox
	{
		public AffineTransform3D pt2imageXfm;
		public long[] outputDims;
		
		public TransformAndBox( final AffineTransform3D xfm, final long[] dims )
		{
			this.pt2imageXfm = xfm;
			this.outputDims = dims;
		}
	}
	
	public static void toDouble( final long[] src, final double[] dst )
	{
		for( int i = 0; i < dst.length; i++ )
			dst[ i ] = src[ i ];
	}

	public static <T extends RealType<T>> void linearPsf( RandomAccessibleInterval<T> psf, double peakVal )
	{
		Cursor< T > c = Views.flatIterable( psf ).cursor();
		while( c.hasNext() )
		{
			c.fwd();
			double v = peakVal;
			for( int d = 0; d < psf.numDimensions(); d++ )
			{
				double u = c.getDoublePosition( d ) * c.getDoublePosition( d );
				v = v / u;
			}
			c.get().setReal( v );
		}
	}
	
	public static <T extends RealType<T>> void quadraticPsf( RandomAccessibleInterval<T> psf, double peakVal )
	{
		Cursor< T > c = Views.flatIterable( psf ).cursor();
		while( c.hasNext() )
		{
			c.fwd();
			double v = 1;
			for( int d = 0; d < psf.numDimensions(); d++ )
			{
				double u = c.getDoublePosition( d ) * c.getDoublePosition( d );
				v = v / Math.pow( 2, u );
			}
			c.get().setReal( v );
		}
	}
	
	/**
	 * The input psf must be zero-centered
	 * @param psf the rai
	 * @param sigma the sigmas
	 */
	public static <T extends RealType<T>> void gaussianPsf( RandomAccessibleInterval<T> psf, double peak, double[] sigma )
	{
		Cursor< T > c = Views.flatIterable( psf ).cursor();
		while( c.hasNext() )
		{
			c.fwd();
			double v = 0;
			for( int d = 0; d < psf.numDimensions(); d++ )
			{
				v += ( c.getDoublePosition( d ) * c.getDoublePosition( d ) ) / ( 2 * sigma[ d ] * sigma[ d ] );
			}
			c.get().setReal( peak * Math.exp( -v ) );
		}
	}

	public static <T extends RealType<T>, S extends RealType<S>> void render( 
			List<TbarPrediction> ptList,
			AffineTransform3D pt2imageTransform,
			RandomAccessibleInterval<T> rai,
			RealRandomAccessible<S> psf,
			Interval psfInterval )
	{
		RealPoint imgPt = new RealPoint( 3 );
		int i = 0;
		int nskipped = 0;
		for( TbarPrediction pt : ptList )
		{
			i++;
			
			if( pt.confidence < confidenceThresh )
			{
				nskipped++;
				continue;
			}

			pt2imageTransform.apply( pt, imgPt );
			render( imgPt, rai, psf, psfInterval );

			if( i % 5000 == 0 )
			{
				System.out.println( String.format("pt %d of %d ", i, ptList.size()) );
			}
		}
		System.out.println(" " + nskipped + " tbars were under the confidence threshold and skipped");
	}
	
	public static <T extends RealType<T>, S extends RealType<S>> void render( 
			RealLocalizable pt,
			RandomAccessibleInterval<T> rai,
			RealRandomAccessible<S> psf,
			Interval psfInterval )
	{
//		System.out.println( "pt: " + Util.printCoordinates( pt ));

		RealPoint diff = new RealPoint(
				pt.getDoublePosition( 0 ) - Math.round( pt.getDoublePosition( 0 )),
				pt.getDoublePosition( 1 ) - Math.round( pt.getDoublePosition( 1 )),
				pt.getDoublePosition( 2 ) - Math.round( pt.getDoublePosition( 2 )));
		
//		System.out.println( "diff: " + Util.printCoordinates( diff ));

		RandomAccess< T > ra = rai.randomAccess();
		RealRandomAccess< S > psfRa = psf.realRandomAccess();
		
		IntervalIterator it = new IntervalIterator( psfInterval );
		while( it.hasNext() )
		{
			it.fwd();
			offset( ra, pt, it );
			realoffset( psfRa, diff, it );

			ra.get().setReal( ra.get().getRealDouble() + psfRa.get().getRealDouble() );
		}
	}

	public static void offset( Positionable dest, Localizable center, RealLocalizable offset )
	{
		for( int d = 0; d < dest.numDimensions(); d++ )
			dest.setPosition( center.getIntPosition( d ) + (int)Math.round(offset.getDoublePosition( d )), d );
	}
	
	public static void offset( Positionable dest, RealLocalizable center, RealLocalizable offset )
	{
		for( int d = 0; d < dest.numDimensions(); d++ )
			dest.setPosition( (int)Math.round(center.getDoublePosition( d )) + (int)Math.round(offset.getDoublePosition( d )), d );
	}

	public static void offset( Positionable dest, RealLocalizable center, Localizable offset )
	{
		for( int d = 0; d < dest.numDimensions(); d++ )
			dest.setPosition( (int)Math.round(center.getDoublePosition( d )) + offset.getIntPosition( d ), d );
	}
	
	public static void realoffset( RealPositionable dest, RealLocalizable center, RealLocalizable offset )
	{
		for( int d = 0; d < dest.numDimensions(); d++ )
			dest.setPosition( center.getDoublePosition( d ) + offset.getDoublePosition( d ), d );
	}
}

