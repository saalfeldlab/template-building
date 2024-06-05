package evaluation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.janelia.saalfeldlab.transform.io.TransformReader;

import io.IOHelper;
import net.imglib2.Cursor;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.iterator.IteratorToStream;
import net.imglib2.iterator.RealIntervalIterator;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.2.0-SNAPSHOT" )
public class TransformComparison implements Callable< Void >
{

	@Option( names = { "-a" }, required = true, description = "List of A transforms." )
	private List< String > inputFilesA = new ArrayList<>();

	@Option( names = { "-b" }, required = true, description = "List of B transforms." )
	private List< String > inputFilesB = new ArrayList<>();

	@Option( names = { "-l", "--min" }, required = false, split=",", description = "Interval min (low)" )
	private double[] min;

	@Option( names = { "-h", "--max" }, required = true, split=",", description = "Interval max (high)" )
	private double[] max;

	@Option( names = { "-s", "--spacing" }, required = false, split=",", description = "Spacing" )
	private double[] spacing;

	// TODO implement these
//	@Option( names = { "-m", "--mask" }, required = false, 
//	description = "Mask image. Error is only computed where the mask is greater than zero. Overrides min/max/spacing and reference." )
//	private String maskImage;
//
//	@Option( names = { "-o", "--output-error-image" }, required = false, description = "Output error image." )
//	private String outputErrorImage;
//
//	@Option( names = { "-f", "--reference" }, required = false, 
//		description = "Reference image defining the space over which to compute errors.  Is over-ridden by min/max/spacing parameters.")
//	private String referenceImagke;

	@Option( names = { "-t", "--debug-threshold" }, required = false, description = "Prints message when error are greater than this threshold." )
	private double threshold = Double.MAX_VALUE;

	@Option( names = { "-d", "--difference-out" }, required = false, description = "Output path for the difference image." )
	private String differenceImagePath;

	@Option( names = { "-v", "--verbose" }, required = false, description = "Verbose output" )
	private boolean verbose = false;


    private int ndims; // number of dimensions
    private RealPoint tmpA;
    private RealPoint tmpB;

	private RealTransformSequence transformA;
	private RealTransformSequence transformB;
    
    private double minErrMag = 0;
    private double maxErrMag = 0;
    private double avgErrMag = 0;

	public static void main( String[] args ) throws Exception
	{
		new CommandLine( new TransformComparison() ).execute( args );
		System.exit(0);
    }

	public void setup()
	{
		transformA = TransformReader.readTransforms( inputFilesA );
		transformB = TransformReader.readTransforms( inputFilesB );
        ndims = transformA.numSourceDimensions();

        tmpA = new RealPoint( ndims );
        tmpB = new RealPoint( ndims );
        
		if ( spacing == null )
		{
			setSpacing( new double[ ndims ] );
			Arrays.fill( spacing, 1.0 );
		}

		if ( min == null )
		{
			setMin( new double[ ndims ] );
			Arrays.fill( min, 0.0 );
		}
    }

	public Void call()
	{
        setup();

        
        if( differenceImagePath != null )
        {
        	long[] sz = new long[ ndims ];
        	for( int d = 0; d < ndims; d++)
				sz[ d ] = (long) Math.floor( (max[ d ] - min[ d ]) / spacing[ d ] );
        	
//        	ArrayImg< DoubleType, DoubleArray > differenceImage = ArrayImgs.doubles( sz );
        	FloatImagePlus< FloatType > differenceImage = ImagePlusImgs.floats( sz );
        	System.out.println( "difference image is size: " + Intervals.toString( differenceImage ));

        	loopImage( differenceImage );
      
        	IOHelper.write( differenceImage.getImagePlus(), differenceImagePath );
        }
        else
        	loopIterator();
        
		
		System.out.println( "min error magnitude : " + minErrMag );
		System.out.println( "avg error magnitude : " + avgErrMag );
		System.out.println( "max error magnitude : " + maxErrMag );
		
		return null;
	}
	
	public <T extends RealType<T>> void loopIterator()
	{
		avgErrMag = 0;
		minErrMag = Double.MAX_VALUE;
		maxErrMag = Double.MIN_VALUE;

		// the interval (in physical space) on which to measure
        RealIntervalIterator it = new RealIntervalIterator( new FinalRealInterval( min, max ), spacing );

		long i = 0 ;
		double[] errV = new double[ ndims ];
		while( it.hasNext() )
		{
			it.fwd();

			err( errV, it, transformA, transformB );
			double errMag = Math.sqrt( errV[0]*errV[0] + errV[1]*errV[1] + errV[2]*errV[2] );
			
			avgErrMag += errMag;
			
			if( errMag < minErrMag )
				minErrMag = errMag;
			
			if( errMag > maxErrMag )
				maxErrMag = errMag;
			
			if( verbose )
			{
				if( errMag > threshold ) 
				{
					System.out.println( "point   : " + Util.printCoordinates( it ));
					System.out.println( "  a     : " + Util.printCoordinates( tmpA ));
					System.out.println( "  b     : " + Util.printCoordinates( tmpB ));
					System.out.println( "  error : " + errMag );
				}
			}
			else
			{
				if( errMag > threshold ) 
					System.out.println( "error is : " + errMag + " at " + Util.printCoordinates( it ));
			}

			i++;
		}
		avgErrMag /= i;
		
	}

	public <T extends RealType<T>> void loopImage( final RandomAccessibleInterval<T> differenceImage )
	{
		avgErrMag = 0;
		minErrMag = Double.MAX_VALUE;
		maxErrMag = Double.MIN_VALUE;

		final RealPoint p = new RealPoint( ndims );
		final Cursor< T > c = Views.flatIterable( differenceImage ).cursor();

		final double[] errV = new double[ ndims ];
		while( c.hasNext() )
		{
			c.fwd();

			for ( int d = 0; d < ndims; d++ )
				p.setPosition( (c.getDoublePosition( d ) * spacing[ d ]) + min[ d ], d );

			err( errV, p, transformA, transformB );
			final double errMag = Math.sqrt( errV[0]*errV[0] + errV[1]*errV[1] + errV[2]*errV[2] );

			c.get().setReal( errMag );

			avgErrMag += errMag;
			
			if( errMag < minErrMag )
				minErrMag = errMag;
			
			if( errMag > maxErrMag )
				maxErrMag = errMag;
			
			if( verbose )
			{
				if( errMag > threshold ) 
				{
					System.out.println( "point   : " + Util.printCoordinates( p ));
					System.out.println( "  a     : " + Util.printCoordinates( tmpA ));
					System.out.println( "  b     : " + Util.printCoordinates( tmpB ));
					System.out.println( "  error : " + errMag );
				}
			}
			else
			{
				if( errMag > threshold ) 
					System.out.println( "error is : " + errMag + " at " + Util.printCoordinates( p ));
			}
		}

		avgErrMag /= Intervals.numElements( differenceImage );
		
		System.out.println( "min error magnitude : " + minErrMag );
		System.out.println( "avg error magnitude : " + avgErrMag );
		System.out.println( "max error magnitude : " + maxErrMag );
	}

	/**
	 * Computes transformA( p ) minus transformB( p ) and stores the result in the passed err array.
	 */
	public <T extends RealType<T>,S extends RealType<S>> void err( 
            final double[] err, 
            final RealLocalizable p,
            final RealTransform transformA,
            final RealTransform transformB )

	{
		transformA.apply( p, tmpA );
		transformB.apply( p, tmpB );

		for( int d = 0; d < 3; d++ )
			err[ d ] = tmpA.getDoublePosition( d ) - tmpB.getDoublePosition( d );
	}

	/**
	 * @param ptStream strem of points
	 * @param transformA first transform
	 * @param transformB second transform
	 * @return Stream of error magnitudes
	 */
	public static DoubleStream errorStream( 
            final Stream<RealLocalizable> ptStream,
            final RealTransform transformA,
            final RealTransform transformB )
	{
		final int nd = transformA.numTargetDimensions();
		return ptStream.mapToDouble( p -> {
			final RealPoint tmpA = new RealPoint( nd );
			final RealPoint tmpB = new RealPoint( nd );

			transformA.apply( p, tmpA );
			transformB.apply( p, tmpB );

			double sqrErr = 0.0;
			for( int d = 0; d < nd; d++ ) {
				double del = tmpA.getDoublePosition( d ) - tmpB.getDoublePosition( d );
				sqrErr += del*del;
			}
			return Math.sqrt( sqrErr );
		});
	}

	/**
	 * @param ptStream  stream of points
	 * @param transformA first transform
	 * @param transformB second transform
	 * @return Stream of error magnitudes
	 */
	public static DoubleStream errorStream(final RealIntervalIterator ptIterator, final RealTransform transformA,
			final RealTransform transformB) {

		Iterable<RealLocalizable> it = () -> new IteratorToStream<>(ptIterator);
		return errorStream(StreamSupport.stream(it.spliterator(), false), transformA, transformB);
	}

	public void setVerbose( boolean verbose )
	{
		this.verbose = verbose;
	}

	public void setThreshold( double threshold )
	{
		this.threshold = threshold;
	}

	public void setSpacing( double[] spacing )
	{
		this.spacing = spacing;
	}

	public void setMax( double[] max )
	{
		this.max = max;
	}

	public void setMin( double[] min )
	{
		this.min = min;
	}

	public void setInputFilesB( List< String > inputFilesB )
	{
		this.inputFilesB = inputFilesB;
	}

	public void setInputFilesA( List< String > inputFilesA )
	{
		this.inputFilesA = inputFilesA;
	}

}
