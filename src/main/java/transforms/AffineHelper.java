package transforms;

import java.util.Arrays;

import jitk.spline.XfmUtils;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation2D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.util.Util;

public class AffineHelper
{
	
	public static void main( String[] args )
	{
		AffineTransform3D xfm = new AffineTransform3D();
		FinalInterval interval = new FinalInterval( 5, 5, 5 );
		FinalInterval intervalXfm = transformInterval( interval, xfm );

		System.out.println( Util.printInterval( intervalXfm ) );

	}
	
	public static double[] center( Interval interval )
	{
		double[] center = new double[ interval.numDimensions() ];
		for ( int d = 0; d < center.length; d++ )
		{
			center[ d ] = ((double) interval.dimension( d )) / 2;
		}
		return center;
	}
	
	/*
	 * Output a new 2d transform that when given to RealViews.transform 1)
	 * translates the center to the origin. 2) applies the rotation 3) offsets
	 * the result
	 */
	public static AffineTransform2D rotateOffset( AffineTransform2D rotation,
			double[] center, double[] offset )
	{
		Translation2D centeringXfm = new Translation2D();
		Translation2D offsetXfm = new Translation2D();
		for ( int d = 0; d < center.length; d++ )
		{
			centeringXfm.set( center[ d ], d );

			offsetXfm.set( -offset[ d ], d );
		}

		AffineTransform2D centerRotXfm = rotation.copy().preConcatenate( centeringXfm );
		return centerRotXfm.concatenate( offsetXfm ).inverse();
	}

	/*
	 * Output a new 2d transform that when given to RealViews.transform 1)
	 * translates the center to the origin. 2) applies the rotation 3) offsets
	 * the result to the
	 */
	public static AffineTransform3D rotateOffset( AffineTransform3D rotation,
			double[] center, double[] offset )
	{
		Translation3D centeringXfm = new Translation3D();
		Translation3D offsetXfm = new Translation3D();
		for ( int d = 0; d < center.length; d++ )
		{
			centeringXfm.set( center[ d ], d );

			offsetXfm.set( -offset[ d ], d );
		}

		AffineTransform3D centerRotXfm = rotation.copy().preConcatenate( centeringXfm );
		return centerRotXfm.concatenate( offsetXfm ).inverse();
	}

	/*
	 * Output a new 2d transform that when given to RealViews.transform 1)
	 * translates the center of the interval to the origin. 2) applies the
	 * rotation 3) offsets the result to the
	 */
	public static AffineTransform2D rotateOffset( AffineTransform2D rotation,
			Interval interval, double[] offset )
	{
		Translation2D centeringXfm = new Translation2D();
		Translation2D offsetXfm = new Translation2D();
		for ( int d = 0; d < interval.numDimensions(); d++ )
		{
			centeringXfm.set( interval.min( d ) + ((double) interval.dimension( d )) / 2,
					d );

			offsetXfm.set( -offset[ d ], d );
		}

		AffineTransform2D centerRotXfm = rotation.copy().preConcatenate( centeringXfm );
		return centerRotXfm.concatenate( offsetXfm ).inverse();
	}

	public static AffineTransform3D rotateOffset( AffineTransform3D rotation,
			Interval interval, double[] offset )
	{
		Translation3D centeringXfm = new Translation3D();
		Translation3D offsetXfm = new Translation3D();
		for ( int d = 0; d < interval.numDimensions(); d++ )
		{
			centeringXfm.set( interval.min( d ) + ((double) interval.dimension( d )) / 2,
					d );

			offsetXfm.set( -offset[ d ], d );
		}

		AffineTransform3D centerRotXfm = rotation.copy().preConcatenate( centeringXfm );
		return centerRotXfm.concatenate( offsetXfm ).inverse();
	}

	public static AffineTransform3D centeredRotation( AffineTransform3D rotation,
			Interval interval )
	{
		Translation3D centeringXfm = new Translation3D();
		for ( int d = 0; d < interval.numDimensions(); d++ )
		{
			centeringXfm.set( interval.min( d ) + ((double) interval.dimension( d )) / 2,
					d );
		}

		return rotation.concatenate( centeringXfm.inverse() );
	}
	
	public static AffineTransform3D centeredRotationBack( AffineTransform3D rotation,
			Interval interval )
	{
		Translation3D centeringXfm = new Translation3D();
		for ( int d = 0; d < interval.numDimensions(); d++ )
		{
			centeringXfm.set( interval.min( d ) + ((double) interval.dimension( d )) / 2,
					d );
		}
		AffineTransform3D out = rotation.copy();
		out.concatenate( centeringXfm.inverse() );
		out.preConcatenate( centeringXfm );
		return out;
	}
	
	public static AffineTransform2D centeredRotation( AffineTransform2D rotation,
			Interval interval )
	{
		Translation2D centeringXfm = new Translation2D();
		for ( int d = 0; d < interval.numDimensions(); d++ )
		{
			centeringXfm.set( interval.min( d ) + ((double) interval.dimension( d )) / 2,
					d );
		}

		return rotation.concatenate( centeringXfm.inverse() );
	}

	public static AffineTransform2D centeredRotationInv( AffineTransform2D rotation,
			Interval interval )
	{

		Translation2D centeringXfm = new Translation2D();
		for ( int d = 0; d < interval.numDimensions(); d++ )
		{
			centeringXfm.set( interval.min( d ) + ((double) interval.dimension( d )) / 2,
					d );
		}

		return rotation.preConcatenate( centeringXfm );
	}

	public static AffineTransform3D centeredRotation3d( AffineTransform2D rotation,
			Interval interval )
	{
		Translation3D centeringXfm = new Translation3D();
		for ( int d = 0; d < interval.numDimensions(); d++ )
		{
			centeringXfm.set( interval.min( d ) + ((double) interval.dimension( d )) / 2,
					d );
		}
		return to3D( rotation ).concatenate( centeringXfm.inverse() );
	}

	public static AffineTransform3D to3D( AffineTransform2D xfm )
	{
		AffineTransform3D out = new AffineTransform3D();
		out.set( xfm.get( 0, 0 ), xfm.get( 0, 1 ), 0.0, xfm.get( 0, 2 ), xfm.get( 1, 0 ),
				xfm.get( 1, 1 ), 0.0, xfm.get( 1, 2 ), 0.0, 0.0, 1.0, 0.0 );

		return out;
	}

	public static Translation2D toTranslation( double[] translation )
	{
		Translation2D xfm = new Translation2D();
		xfm.set( translation[ 0 ], translation[ 1 ] );
		return xfm;
	}

	public static Translation3D toTranslation3d( double[] translation )
	{
		Translation3D xfm = new Translation3D();
		xfm.set( translation[ 0 ], translation[ 1 ], translation[ 2 ] );
		return xfm;
	}
	
	/**
	 * Apply an affine transformation to every corner to determine
	 * a new interval.
	 * 
	 * @param interval the interval
	 * @param affine the transformation
	 * @return the transformed interval
	 */
	public static FinalInterval transformInterval( Interval interval, AffineGet affine )
	{
		assert( affine.numDimensions() == interval.numDimensions() );

		int nd = interval.numDimensions();
		double[] pt = new double[ nd ];
		double[] tmp = new double[ nd ];

		long[] newmin = new long[ nd ];
		long[] newmax = new long[ nd ];

		int minOrMaxByDim = 0;
		double N = Math.pow(2,nd);
		int[] ts = new int[ nd ];
		for( int i = 0; i < nd; i++ )
		{
			ts[i] = 1 << i;
		}

		while( minOrMaxByDim < N )
		{
			for( int i = 0; i < nd; i++ )
			{
				if( ( minOrMaxByDim & ts[i] ) > 0  )
					pt[ i ] = interval.max( i );
				else
					pt[ i ] = interval.min( i );
			}
			
			affine.apply( pt, tmp );

//			System.out.println( "pt    : " + XfmUtils.printArray( pt ));
//			System.out.println( "pt xfm: " + XfmUtils.printArray( tmp ));
//			System.out.println( " " );

			toLongMin( tmp, newmin );
			toLongMax( tmp, newmax );

			minOrMaxByDim++;
		}
		
		System.out.println( "newmin: " + printLongArray( newmin ));
		System.out.println( "newmax: " + printLongArray( newmax ));

		return new FinalInterval( newmin, newmax );
	}
	
	/**
	 * Apply an affine transformation to the min corner and max corner to determine
	 * a new interval.
	 * 
	 * @param interval the interval
	 * @param affine the transformation
	 * @return the transformed interval
	 */
	public static FinalInterval transformIntervalCorner( Interval interval, AffineGet affine )
	{
		assert( affine.numDimensions() == interval.numDimensions() );
		
		double[] minp = new double[ interval.numDimensions() ];
		double[] maxp = new double[ interval.numDimensions() ];
		double[] tmp = new double[ interval.numDimensions() ];
		interval.realMin( minp );
		interval.realMax( maxp );
		
		long[] newmin = new long[ interval.numDimensions() ];
		long[] newmax = new long[ interval.numDimensions() ];
		
		affine.apply( minp, tmp );
		toLong( tmp, newmin );
		
		affine.apply( maxp, tmp );
		toLong( tmp, newmax );
		
		return new FinalInterval( newmin, newmax );
	}
	
	public static AffineTransform3D to3D( AffineTransform xfm )
	{
		AffineTransform3D out = new AffineTransform3D();
		for ( int i = 0; i < 3; i++ )
		{
			for ( int j = 0; j < 4; j++ )
			{
				out.set( xfm.get( i, j ), i, j );
			}
		}

		return out;
	}
	
	public static void toLong( double[] in, long[] out )
	{
		for( int i = 0; i < in.length; i++ )
		{
			out[ i ] = (long)Math.round( in[i] );
		}
	}
	
	public static void toLongMin( double[] in, long[] out )
	{
		for( int i = 0; i < in.length; i++ )
		{
			long newcoord = (long)Math.floor( in[i] );
			if( newcoord < out[i] )
				out[ i ] = newcoord;
		}
	}
	
	public static void toLongMax( double[] in, long[] out )
	{
		for( int i = 0; i < in.length; i++ )
		{
			long newcoord = (long)Math.ceil( in[i] );
			if( newcoord > out[i] )
				out[ i ] = newcoord;
		}
	}
	
	public static final String printLongArray(long[] in) {
		if (in == null)
			return "null";
		String out = "";
		for (int i = 0; i < in.length; i++) {
			out += in[i] + " ";
		}
		return out;
	}
}
