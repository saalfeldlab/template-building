package util;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.IOHelper;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.ScaleAndTranslation;
import net.imglib2.realtransform.TransformToDeformationField;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import process.RenderTransformed;

public class FieldOfView implements Callable< Void >
{
	
	// TODO what if the origin is not in the fov?
	//public static enum PIN_POLICY{ MIN, MAX, ORIGIN, CENTER };

	public static enum PIN_POLICY{ MIN, MAX, CENTER };

	public PIN_POLICY defaultPolicy = PIN_POLICY.MIN;
	
	private final int ndims;

	private RealInterval physicalInterval;

	private Interval pixelInterval;

	private double[] spacing;
	
	private AffineGet pixelToPhysical;
	
	private double delta = 1e-6;

	@Option( names = { "-n", "--num-dimensions" }, required = true,
			description = "Number of dimensions." )
	private int ndimsArg;

	@Option( names = { "-s", "--outputImageSize" }, required = false,
			description = "Size / field of view of image output in pixels.  Comma separated, e.g. \"200,250,300\". "
					+ "Overrides reference image."  )
	private String outputSizeArg;

	@Option( names = { "-m", "--interval-min", "--offset" }, required = false, split=",",
			description = "Offset of the output interval in physical units. Overrides reference image." )
	private double[] intervalMinArg;

	@Option( names = { "-x", "--interval-max"  }, required = false, split=",",
			description = "Maximum coordinate of output interval.  Overrides reference, and outputImageSize parameters." )
	private double[] intervalMaxArg;

//	@Option( names = { "-i", "--input-resolution" }, required = false, split = ",", 
//			description = "The resolution of the input field of view. Overrides reference image." )
//	private double[] inputResolutionArg;

	@Option( names = { "-r", "--output-resolution" }, required = false, split = ",", 
			description = "The resolution of the output space. Overrides reference image." )
	private double[] outputResolutionArg;

	@Option( names = { "-f", "--reference" }, required = false, 
			description = "A reference image specifying the output size and resolution." )
	private String referenceImagePath;	
	
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
		updatePixelToPhysicalTransform();
	}

	public static void main( String[] args )
	{
		CommandLine.call( new FieldOfView( 3 ), args );
	}

	@Override
	public Void call()
	{
		// use size / resolution if the only input transform is a dfield
		// ( and size / resolution is not given )
		Optional< ValuePair< long[], double[] > > sizeAndRes = Optional.empty();
		if ( referenceImagePath != null && !referenceImagePath.isEmpty() && new File( referenceImagePath ).exists() )
		{
			IOHelper io = new IOHelper();
			sizeAndRes = Optional.of( io.readSizeAndResolution( new File( referenceImagePath ) ) );
		}

		if ( outputSizeArg != null && !outputSizeArg.isEmpty() )
			pixelInterval = RenderTransformed.parseInterval( outputSizeArg );

		FieldOfView fov = FieldOfView.parse( ndimsArg, sizeAndRes, Optional.ofNullable( intervalMinArg ), Optional.ofNullable( intervalMaxArg ), Optional.ofNullable( pixelInterval ), Optional.ofNullable( outputResolutionArg ) );
		fov.updatePixelToPhysicalTransform();

		// we need to tack on the conversion from pixel to physical space first
		pixelToPhysical = fov.getPixelToPhysicalTransform();
		pixelInterval = fov.getPixel();
		spacing = fov.getSpacing();

		System.out.println( fov );
		return null;
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
	
	public AffineGet getPixelToPhysicalTransform()
	{
		return pixelToPhysical;
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

	public void setPixel( Interval interval )
	{
		boolean isZeroMin = true;
		for( int i = 0; i < interval.numDimensions(); i++ )
			if( interval.min( i ) != 0 )
				isZeroMin = false;

		if( isZeroMin )
		{
			pixelInterval = new FinalInterval( interval );
		}
		else
		{
			pixelInterval = new FinalInterval( Intervals.dimensionsAsLongArray( interval ));
			setPixelOffset( Intervals.minAsLongArray( interval ));
		}
	}

	public void setPixelOffset( final long[] pixOffset )
	{
		double[] min = new double[ ndims ];
		double[] max = new double[ ndims ];
		for( int i = 0; i < ndims; i++ )
		{
			min[ i ] = pixOffset[ i ] * spacing[ i ];
			max[ i ] = min[ i ] + ( spacing[ i ] * pixelInterval.dimension( i ));
		}
		setPhysical( min, max );
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
	
	public void updatePixelToPhysicalTransform()
	{
		pixelToPhysical = pixelToPhysical( physicalInterval, pixelInterval );
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
			double y = w[ i ] / spacing[ i ];
			double yc = Math.ceil( y );
			if ( Math.abs( yc - y ) > delta )
			{
				System.out.println( "no add");
				sz[ i ] = (long)yc;
			}
			else
			{
				System.out.println( "add one");
				sz[ i ] = 1 + (long)yc;
			}
		}
		pixelInterval = new FinalInterval( sz );
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
//		case ORIGIN:
//			updatePhysicalPinOrigin();
//			break;
		case CENTER:
			updatePhysicalPinCenter();
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

//	/**
//	 * Updates the physical interval such that the origin is on the grid.
//	 * 
//	 */
//	public void updatePhysicalPinOrigin()
//	{
//		double[] min = new double[ ndims ];
//		double[] max = new double[ ndims ];
//		for( int i = 0; i < ndims; i++ )
//		{
//			double distLo = Math.abs( physicalInterval.realMin( i ));
//			double distHi = Math.abs( physicalInterval.realMax( i ));
//	
//			if( distLo < distHi )
//			{
//				min[ i ] = Math.ceil( physicalInterval.realMin( i ) / spacing[ i ] ) * spacing[ i ];
//				max[ i ] = min[ i ] + ( spacing[ i ] * pixelInterval.dimension( i ));
//			}
//			else
//			{
//				max[ i ] = 
//				min[ i ] = max[ i ] - ( spacing[ i ] * pixelInterval.dimension( i ));
//			}
//
//
//		}
//		setPhysical( min, max );
//	}
	
	/**
	 * Assumes the passed point is contained in this field of view.
	 * Does not check.
	 * 
	 * @param p the physical point
	 */
	public void updatePhysicalPinPoint( RealLocalizable p )
	{
		double[] min = new double[ ndims ];
		double[] max = new double[ ndims ];
		for( int i = 0; i < ndims; i++ )
		{
			double center = p.getDoublePosition( i );
			double rad = Math.min( 
					physicalInterval.realMax( i ) - p.getDoublePosition( i ),
					p.getDoublePosition( i ) - physicalInterval.realMin( i ));

			System.out.println( "rad   : " + rad );
			System.out.println( "center: " + center );

			min[ i ] = center - rad;
			max[ i ] = center + rad;
		}
		setPhysical( min, max );
		
	}

	public void updatePhysicalPinCenter()
	{
		double[] min = new double[ ndims ];
		double[] max = new double[ ndims ];
		for( int i = 0; i < ndims; i++ )
		{
			double center = ( physicalInterval.realMax( i ) + physicalInterval.realMin( i )) / 2.0;
			double rad = ( spacing[ i ] * pixelInterval.dimension( i ) ) / 2.0;

			System.out.println( "w: " + rad );
			System.out.println( "center: " + center );

			min[ i ] = center - rad;
			max[ i ] = center + rad;
		}
		setPhysical( min, max );
	}
	
	public boolean onGrid( RealLocalizable p, double eps )
	{
		for( int i = 0; i < ndims; i++ )
		{
			double rem = ( p.getDoublePosition( i ) / spacing [ i ] ) % 1;
			if( rem >= eps )
			{
				return false;
			}
		}
		return true;
	}

	public void getCenter( RealPositionable center )
	{
		for( int i = 0; i < ndims; i++ )
		{
			center.setPosition( ( physicalInterval.realMax( i ) + physicalInterval.realMin( i )) / 2.0,
					i  );
		}
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

		return new FieldOfView( new FinalRealInterval( subMin, subMax ), zeroMin( interval ), spacing );
	}

	// TODO replace with Intervals.zeroMin when we update imglib2
	public static Interval zeroMin( final Interval interval )
	{
		return new FinalInterval( Intervals.dimensionsAsLongArray( interval ) );	
	}

	public static FieldOfView parse(
			final int ndims,
			final ValuePair<long[], double[]> sizeAndRes,
			final double[] minOpt,
			final double[] maxOpt,
			final Interval pixelIntervalOpt,
			final double[] spacingOpt )
	{
		return parse( ndims,
				Optional.ofNullable( sizeAndRes ),
				Optional.ofNullable( minOpt ),
				Optional.ofNullable( maxOpt ),
				Optional.ofNullable( pixelIntervalOpt ),
				Optional.ofNullable( spacingOpt ));
	}

	/**
	 * Generate physical, pixel intervals from parameters and a transform from 
	 * pixel to physical space.  
	 * 
	 * The max in physical units or pixel size must be present.
	 * 
	 * @param ndims number of dimensions
	 * @param sizeAndRes size and resolution of some reference image
	 * @param minOpt of interval in physical units, defaults to origin if not present.
	 * @param maxOpt of interval in physical units
	 * @param sizeOpt of interval in pixel units
	 * @param spacingOpt pixel spacing (resolution), defaults to unit (1.0...) spacing if not present.
	 * @return true if parsing was successful
	 */
	public static FieldOfView parse(
			final int ndims,
			final Optional<ValuePair<long[], double[]>> sizeAndRes,
			final Optional<double[]> minOpt,
			final Optional<double[]> maxOpt,
			final Optional<Interval> pixelIntervalOpt,
			final Optional<double[]> spacingOpt )
	{
		// min defaults to zero
		double[] min = minOpt.orElse( new double[ ndims ]);
		
		FieldOfView fov = null;
		if( sizeAndRes.isPresent() )
		{
			fov = FieldOfView.fromSpacingSize( sizeAndRes.get().b, new FinalInterval( sizeAndRes.get().a ));

			if( pixelIntervalOpt.isPresent() )
			{
				fov.setPixel( pixelIntervalOpt.get() );
				fov.updatePhysical();
			}

			if( minOpt.isPresent() && maxOpt.isPresent() )
			{
				fov.setPhysical( minOpt.get(), maxOpt.get() );
				fov.updatePhysical();
			}
			else if( maxOpt.isPresent() )
			{
				fov.setPhysicalMax( maxOpt.get() );
				fov.updatePhysical();
			}

			if( spacingOpt.isPresent() )
			{
				fov.setSpacing( spacingOpt.get() );
				fov.updatePixel();
			}
		}
		else if( spacingOpt.isPresent() && pixelIntervalOpt.isPresent() )
		{
			fov = FieldOfView.fromSpacingSize( spacingOpt.get(), pixelIntervalOpt.get() );

			if( minOpt.isPresent() && maxOpt.isPresent() )
			{
				fov.setPhysical( minOpt.get(), maxOpt.get() );
				fov.updatePixel();
			}
			else if( maxOpt.isPresent() )
			{
				fov.setPhysicalMax( maxOpt.get() );
				fov.updatePixel();
			}
		}
		else if( maxOpt.isPresent() && pixelIntervalOpt.isPresent() )
		{
			fov = FieldOfView.fromPhysicalPixel( min, maxOpt.get(), pixelIntervalOpt.get() );

			if( spacingOpt.isPresent() )
			{
				fov.setSpacing( spacingOpt.get() );
				fov.updatePixel();
			}
		}else if( maxOpt.isPresent() && spacingOpt.isPresent() )
		{
			fov = FieldOfView.fromPhysicalSpacing( min, maxOpt.get(), spacingOpt.get() );
		}
		else
		{
			System.err.println("Some missing options.");
			return null;
		}

		return fov;
	}
	
	public FieldOfView copy()
	{
		FieldOfView copy = new FieldOfView( physicalInterval, pixelInterval, spacing );
		return copy;
	}

	public static FieldOfView fromSpacingSize( final double[] spacing, final Interval size )
	{
		FieldOfView fov = new FieldOfView( size.numDimensions() );
		fov.setSpacing( spacing );
		fov.setPhysicalMax( physicalSize( spacing, size ) );
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
		if ( min != null )
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
	public static FieldOfView fromPhysicalPixel( final double[] max, final Interval size )
	{
		return fromPhysicalPixel( null, max, size );
	}

	public static FieldOfView fromPhysicalPixel( final double[] min, final double[] max, final Interval size )
	{
		FieldOfView fov = new FieldOfView( size.numDimensions() );
		if ( min != null )
			fov.setPhysical( min, max );
		else
			fov.setPhysicalMax( max );

		fov.setPixel( size );
		fov.updateSpacing();
		return fov;
	}

	public static FieldOfView fromPhysicalSpacing( final double[] min, final double[] max, final double[] spacing )
	{
		FieldOfView fov = new FieldOfView( max.length );
		if ( min != null )
			fov.setPhysical( min, max );
		else
			fov.setPhysicalMax( max );

		fov.setSpacing( spacing );
		fov.updatePixel();
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
			s[ i ] = (physicalInterval.realMax( i ) - physicalInterval.realMin( i ) ) / pixelInterval.dimension( i );
			System.out.println( "i " + i  + " : " + s[ i ]);
		}

		ScaleAndTranslation xfm = new ScaleAndTranslation( s, t );
		return xfm;
	}

	public void setPhysicalExtents( final double[] spacing, final Interval size, final double[] min )
	{
		physicalInterval = new FinalRealInterval( new double[ size.numDimensions() ], physicalSize( spacing, size ) );
	}
	
	public String toString()
	{
		StringBuffer s = new StringBuffer();
		s.append( "FOV nd (" + ndims + "):\n" );
		s.append( "  pix: " + Util.printInterval( pixelInterval ) + "\n");
		s.append( "  phy: " + printRealInterva( physicalInterval ) + "\n" );
		s.append( "  res: " + Arrays.toString( spacing ) + "\n");
		return s.toString();
	}
	
	public static String printRealInterva( final RealInterval value )
	{
		final StringBuilder sb = new StringBuilder();

		sb.append( "[(" );
		final int n = value.numDimensions();
		for ( int d = 0; d < n; d++ )
		{
			sb.append( value.realMin( d ) );
			if ( d < n - 1 )
				sb.append( ", " );
		}
		sb.append( ") -- (" );
		for ( int d = 0; d < n; d++ )
		{
			sb.append( value.realMax( d ) );
			if ( d < n - 1 )
				sb.append( ", " );
		}
		sb.append( ")]" );

		return sb.toString();	
	}
	
	public static double[] physicalSize( final double[] spacing, final Interval size )
	{
		int nd = size.numDimensions();
		double[] physicalSize = new double[ nd ];
		for( int i = 0; i < nd; i++ )
			physicalSize[ i ] = spacing[ i ] * size.dimension( i );
		
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
