package net.imglib2.calibrated;

import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.RealRandomAccessibleRealInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.AffineTransform4DRepeated3D;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;

public class CalibratedRai4d<T extends RealType<T> & NativeType<T>>
{ 

	private final RandomAccessibleInterval<T> img;
	
	private final RealInterval scaledInterval;

	private final AffineTransform4DRepeated3D scaleXfm;
	
	public CalibratedRai4d( ImagePlus imp, T t )
	{
		this( ImageJFunctions.wrap( imp ), resolutions(imp) );
	}

	public CalibratedRai4d( RandomAccessibleInterval<T> img, double[] resolutions )
	{
		scaleXfm = new AffineTransform4DRepeated3D( getScaleXfm( resolutions ) );
		this.img = img;
		scaledInterval = largestIntervalFromRealInterval( img, scaleXfm.getTransform() );
	}
	
	public static double[] resolutions( ImagePlus imp )
	{
		return new double[]{ 
				imp.getCalibration().pixelWidth,
				imp.getCalibration().pixelHeight,
				imp.getCalibration().pixelDepth
		}; 
	}
	
	public RealTransformRandomAccessible< T, InverseRealTransform > getRealRandomAccessible()
	{
		return RealViews.transform(
				Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory< T >()),
				scaleXfm );
	}
	
	public RandomAccessibleInterval<T> toOriginalResolution( RealRandomAccessible<T> rra )
	{
		return Views.interval( Views.raster( 
					RealViews.transform( rra, scaleXfm.inverse() )), 
				img);
	}
	
	public static AffineTransform3D getScaleXfm( double[] resolutions )
	{
		AffineTransform3D scaleXfm = new AffineTransform3D();
		scaleXfm.set( resolutions[ 0 ],  0, 0 );
		scaleXfm.set( resolutions[ 1 ], 1, 1 );
		scaleXfm.set( resolutions[ 2 ],  2, 2 );
		return scaleXfm;
	}
	
	public static AffineTransform3D getScaleXfm( ImagePlus imp )
	{
		AffineTransform3D scaleXfm = new AffineTransform3D();
		scaleXfm.set( imp.getCalibration().pixelWidth,  0, 0 );
		scaleXfm.set( imp.getCalibration().pixelHeight, 1, 1 );
		scaleXfm.set( imp.getCalibration().pixelDepth,  2, 2 );
		return scaleXfm;
	}
	
	public static Interval largestIntervalFromRealInterval( RealInterval interval, AffineTransform3D scaleXfm )
	{
		int nd = interval.numDimensions();
		long[] min = new long[ nd ];
		long[] max = new long[ nd ];

		for( int d = 0; d < 3; d++ )
		{
			min[ d ] = (long)Math.ceil( scaleXfm.get( d, d ) * interval.realMin( d ));
			max[ d ] = (long)Math.floor( scaleXfm.get( d, d ) * interval.realMax( d ));
		}
		return new FinalInterval( min, max );
	}
	
	public static Interval largestIntervalFromRealInterval( RealInterval interval, double[] resolutions )
	{
		int nd = interval.numDimensions();
		long[] min = new long[ nd ];
		long[] max = new long[ nd ];

		for( int d = 0; d < resolutions.length; d++ )
		{
			min[ d ] = (long)Math.ceil( resolutions[ d ] * interval.realMin( d ));
			max[ d ] = (long)Math.floor( resolutions[ d ] * interval.realMax( d ));
		}
		return new FinalInterval( min, max );
	}
}
