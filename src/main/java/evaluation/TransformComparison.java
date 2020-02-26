package evaluation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.transform.io.TransformReader;

import net.imglib2.FinalRealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.iterator.RealIntervalIterator;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.1.1-SNAPSHOT" )
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
//		description = "Reference image defining the space over which to compute errors.  Is overrided by min/max/spacing parameters.")
//	private String referenceImagke;

	@Option( names = { "-t", "--debug-threshold" }, required = false, description = "Prints message when error are greater than this threshold." )
	private double threshold = Double.MAX_VALUE;

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
		CommandLine.call( new TransformComparison(), args );
		System.exit(0);
    }

	public void setup()
	{
		transformA = TransformReader.readTransforms( inputFilesA );
		transformB = TransformReader.readTransforms( inputFilesB );
        ndims = transformA.numSourceDimensions();

        tmpA = new RealPoint( ndims );
        tmpB = new RealPoint( ndims );
        
        if( spacing == null )
        {
        	spacing = new double[ ndims ];
        	Arrays.fill( spacing, 1.0 );
        }
        
        if( min == null )
        {
        	min = new double[ ndims ];
        	Arrays.fill( min, 0.0 );
        }
    }

	public Void call()
	{
        setup();

		// the interval (in physical space) on which to measure
        RealIntervalIterator it = new RealIntervalIterator( new FinalRealInterval( min, max ), spacing );
        
		avgErrMag = 0;
		minErrMag = Double.MAX_VALUE;
		maxErrMag = Double.MIN_VALUE;

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
		
		System.out.println( "min error magnitude : " + minErrMag );
		System.out.println( "avg error magnitude : " + avgErrMag );
		System.out.println( "max error magnitude : " + maxErrMag );
		
		return null;
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
}
