package util;

import java.util.Arrays;
import java.util.Optional;

import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.ScaleAndTranslation;
import net.imglib2.util.Intervals;

public class FieldOfView
{
	
	public static enum PIN_POLICY{ MIN, MAX, ORIGIN, EVEN };

	public PIN_POLICY defaultPolicy = PIN_POLICY.ORIGIN;
	
	private final int ndims;

	private RealInterval physicalInterval;

	private Interval pixelInterval;

	private double[] spacing;
	
	private AffineGet pixelToPhysical;
	
	public FieldOfView( int ndims )
	{ 
		this.ndims = ndims;
	}

	public FieldOfView( final RealInterval physicalInterval, final Interval pixelInterval, final double[] spacing )
	{
		this.ndims  = physicalInterval.numDimensions();
		this.physicalInterval = physicalInterval;
		this.pixelInterval = pixelInterval;
		this.spacing = spacing;
		pixelToPhysical = pixelToPhysical( physicalInterval, pixelInterval );
	}
	
	public RealInterval getPhysical()
	{
		return physicalInterval;
	}
	
	public double[] getPhysicalWidth()
	{
		double[] w = new double[ ndims ] ;
		for( int i = 0; i < ndims; i++ )
		{
			w[ i ] = physicalInterval.realMax( i ) - physicalInterval.realMin( i );
		}
		return w;
	}
	
	public double[] getPhysicalWidthFromPixelSpacing()
	{
		double[] w = new double[ ndims ] ;
		for( int i = 0; i < ndims; i++ )
		{
			w[ i ] = spacing[ i ] * pixelInterval.dimension( i );
		}
		return w;
	}

	public Interval getPixel()
	{
		return pixelInterval;
	}
	
	public double[] getSpacing()
	{
		return spacing;
	}
	
	/**
	 * This field of view is complete if both a real (physical) and discrete (pixel) field * of view are defined.
	 * 
	 * @return is this field of view complete.
	 */
	public boolean isComplete()
	{
		return (physicalInterval != null) && (pixelInterval != null);
	}

	public void setPhysicalMax( final double[] max )
	{
		if ( physicalInterval == null )
		{
			physicalInterval = new FinalRealInterval( new double[ max.length ], max );
		}
		else if( validMax( max, physicalInterval ))
		{
			physicalInterval = new FinalRealInterval( Intervals.minAsDoubleArray( physicalInterval ), max );
		}
	}
	
	public void setPhysical( final double[] min, final double[] max )
	{
		physicalInterval = new FinalRealInterval( min, max );
	}
	
	public void setPixel( final long[] size )
	{
		pixelInterval = new FinalInterval( size );
	}
	
	public void setSpacing( final double[] spacing )
	{
		this.spacing = spacing;
	}

	public void setDefaultPolicy( PIN_POLICY policy )
	{
		defaultPolicy = policy;
	}

	public void updateSpacing()
	{
		spacing = FieldOfView.spacing( getPhysicalWidth(), Intervals.dimensionsAsLongArray( pixelInterval ) );
	}

	public void updatePixel()
	{
		updatePixel( defaultPolicy );
	}

	/**
	 * Sets the pixel interval based on current physical extents and spacing. 
	 * 
	 * Uses the smallest pixel interval that entirely covers the current physical
	 * field of view.  It may be larger than the field of view depending on the spacing,
	 * and therefore the physical extents may be updated as a result.
	 * 
	 *  If they are updated, the given policy is used.
	 *
	 */
	public void updatePixel( PIN_POLICY policy )
	{
		double[] w = getPhysicalWidth();
		long[] sz = new long[ ndims ];
		for( int i = 0; i < ndims; i++ )
		{
			sz[ i ] = (long)Math.ceil( w[ i ] / spacing[ i ] );
		}
		pixelInterval = new FinalInterval( sz );
		updatePhysical( policy );
	}
	
	public void updatePhysical()
	{
		updatePhysical( defaultPolicy );
	}

	/**
	 * Set physical extents based on pixel interval and spacing based on the given policy.
	 * 
	 */
	public void updatePhysical( PIN_POLICY policy )
	{
		switch( policy )
		{
		case MIN:
			updatePhysicalPinMin();
			break;
		case MAX:
			updatePhysicalPinMax();
			break;
		case ORIGIN:
			updatePhysicalPinOrigin();
			break;
		case EVEN:
			updatePhysicalPinEven();
			break;
		}
	}
	
	public void updatePhysicalPinMin()
	{
		double[] min = new double[ ndims ];
		double[] max = new double[ ndims ];
		for( int i = 0; i < ndims; i++ )
		{
			min[ i ] = physicalInterval.realMin( i );
			max[ i ] = min[ i ] + ( spacing[ i ] * pixelInterval.dimension( i ));
		}
		setPhysical( min, max );
	}

	public void updatePhysicalPinMax()
	{
		double[] min = new double[ ndims ];
		double[] max = new double[ ndims ];
		for( int i = 0; i < ndims; i++ )
		{
			max[ i ] = physicalInterval.realMax( i );
			min[ i ] = max[ i ] - ( spacing[ i ] * pixelInterval.dimension( i ));
		}
		setPhysical( min, max );
	}

	public void updatePhysicalPinOrigin()
	{
		
	}

	public void updatePhysicalPinEven()
	{
		double[] min = new double[ ndims ];
		double[] max = new double[ ndims ];
		for( int i = 0; i < ndims; i++ )
		{
			double eps = ( spacing[ i ] * pixelInterval.dimension( i )) - ( physicalInterval.realMax( i ) - physicalInterval.realMin( i ));
			min[ i ] = physicalInterval.realMin( i ) - eps;
			max[ i ] = physicalInterval.realMax( i ) + eps;
		}
		setPhysical( min, max );
	}

	public FieldOfView getPixelSubset( final Interval interval )
	{
		double[] subMin = new double[ ndims ];
		double[] subMax = new double[ ndims ];

		for( int i = 0; i < ndims; i++ )
		{
			subMin[ i ] = spacing[ i ] * interval.min( i ) ;
			subMax[ i ] = spacing[ i ] * interval.max( i ) ;
		}

		return new FieldOfView( new FinalRealInterval( subMin, subMax ), Intervals.zeroMin( interval ), spacing );
	}


	/**
	 * Generate physical, pixel intervals from parameters and a transform from 
	 * pixel to physical space.  
	 * 
	 * The max in physical units or pixel size must be present.
	 * 
	 * @param ndims number of dimensions
	 * @param minOpt of interval in physical units, defaults to origin if not present.
	 * @param maxOpt of interval in physical units
	 * @param sizeOpt of interval in pixel units
	 * @param spacingOpt pixel spacing (resolution), defaults to unit (1.0...) spacing if not present.
	 * @return true if parsing was successful
	 */
	public boolean parse(
			final int ndims,
			final Optional<double[]> minOpt,
			final Optional<double[]> maxOpt,
			final Optional<long[]> sizeOpt,
			final Optional<double[]> spacingOpt )
	{

		double[] min = minOpt.orElse( new double[ ndims ]);

		// TODO: don't have a default spacing
		//double[] spacing = spacingOpt.orElse( DoubleStream.generate( () -> 1.0 ).limit( ndims ).toArray() );

		if( !maxOpt.isPresent() && !sizeOpt.isPresent() )
		{
			System.err.println("Warning: must pass either size or max.");
			return false;
		}
		
		if( maxOpt.isPresent() && spacingOpt.isPresent() && sizeOpt.isPresent() )
		{
			System.err.println(  );
		}
	
		if( maxOpt.isPresent() )
		{
			
		}

		return true;
	}
	
	public static FieldOfView fromSpacingSize( final double[] spacing, final long[] size )
	{
		FieldOfView fov = new FieldOfView( size.length );
		fov.setPhysicalMax( physicalSize( spacing, size ));
		fov.setPixel( size );

		return fov;
	}

	/**
	 * Assumes origin at zero
	 */
	public static FieldOfView fromSpacingPhysical( final double[] spacing, final double[] max )
	{
		return fromSpacingPhysical( spacing, null, max );
	}

	public static FieldOfView fromSpacingPhysical( final double[] spacing, final double[] min, final double[] max )
	{
		FieldOfView fov = new FieldOfView( spacing.length );
		if( min != null )
			fov.setPhysical( min, max );
		else
			fov.setPhysicalMax( max );

		fov.setSpacing( spacing );
		fov.updatePixel();

		return fov;
	}
	
	/**
	 * Assumes origin at zero
	 */
	public static FieldOfView fromPhysicalPixel( final double[] max, final long[] size )
	{
		return fromPhysicalPixel( null, max, size );
	}

	public static FieldOfView fromPhysicalPixel( final double[] min, final double[] max, final long[] size )
	{
		FieldOfView fov = new FieldOfView( size.length );
		if( min != null )
			fov.setPhysical( min, max );
		else
			fov.setPhysicalMax( max );
		
		fov.setPixel( size );
		fov.updateSpacing();
		return fov;
	}
	
	/**
	 * Assumes pixelInterval is zeroMin
	 */
	public static ScaleAndTranslation pixelToPhysical( 
			final RealInterval physicalInterval,
			final Interval pixelInterval )
	{
		int nd = physicalInterval.numDimensions();
		double[] s = new double[ nd ];
		double[] t = new double[ nd ];

		for( int i = 0; i < nd; i++ )
		{
			t[ i ] = physicalInterval.realMin( i );
			s[ i ] = (physicalInterval.realMax( i ) - physicalInterval.realMin( i ) / pixelInterval.dimension( i ) );
		}

		ScaleAndTranslation xfm = new ScaleAndTranslation( s, t );
		return xfm;
	}

	public void setPhysicalExtents( final double[] spacing, final long[] size, final double[] min )
	{
		physicalInterval = new FinalRealInterval( new double[ size.length ], physicalSize( spacing, size ) );
	}
	
	public String toString()
	{
		StringBuffer s = new StringBuffer();
		s.append( "FOV nd (" + ndims + "):\n" );
		s.append( "  pix: " + Intervals.toString( pixelInterval ) + "\n");
		s.append( "  phy: " + Intervals.toString( physicalInterval ) + "\n" );
		s.append( "  res: " + Arrays.toString( spacing ) + "\n");
		return s.toString();
	}
	
	public static double[] physicalSize( final double[] spacing, final long[] size )
	{
		int nd = size.length;
		double[] physicalSize = new double[ nd ];
		for( int i = 0; i < nd; i++ )
			physicalSize[ i ] = spacing[ i ] * size[ i ];
		
		return physicalSize;
	}
	
	public static double[] spacing( final double[] physicalSize, final long[] discreteSize )
	{
		int nd = physicalSize.length;
		double[] spacing = new double[ nd ];
		for( int i = 0; i < nd; i++ )
			spacing[ i ] = physicalSize[ i ] / ( discreteSize[ i ] - 1 );

		return spacing;
	}

	/**
	 * @param min the min
	 * @param interval the real interval
	 * @return is the min lower than the passed interval's max
	 */
	public static boolean validMin( final double[] min, final RealInterval interval )
	{
		for( int i = 0; i < interval.numDimensions(); i++ )
		{
			if( interval.realMax( i ) < min[ i ])
				return false;
		}
		return true;
	}

	/**
	 * @param max the max
	 * @param interval the real interval
	 * @return is the max higher than the passed interval's min
	 */
	public static boolean validMax( final double[] max, final RealInterval interval )
	{
		for( int i = 0; i < interval.numDimensions(); i++ )
		{
			if( interval.realMin( i ) > max[ i ])
				return false;
		}
		return true;
	}

}
