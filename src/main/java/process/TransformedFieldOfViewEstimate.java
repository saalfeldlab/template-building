package process;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.transform.io.TransformReader;

import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.iterator.RealIntervalIterator;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.util.Intervals;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.2.0-SNAPSHOT" )
public class TransformedFieldOfViewEstimate implements Callable< RealInterval >
{
	
	public static enum METHODS { corners, faces };

	@Option( names = { "--min" }, required = true, description = "Minimum.", split="," )
	private double[] min;

	@Option( names = { "--max" }, required = true, description = "Maximum.", split="," )
	private double[] max;
	
	@Option( names = { "-t", "--transform" }, required = false, description = "Transformation file." )
	private List< String > transformFiles = new ArrayList<>();

	@Option( names = { "--method" }, required = false, description = "Method (corners,faces)" )
	private String method = "corners";
	
	@Option( names = { "--spacing" }, required = false, description = "Spacing (faces method only).", split="," )
	private double[] spacing;
	
	private RealInterval result;

	public static void main(String[] args)
	{
		CommandLine.call( new TransformedFieldOfViewEstimate(), args );
		System.exit(0);
	}

	@Override
	public RealInterval call() throws Exception
	{
		RealTransformSequence transform = TransformReader.readTransforms( transformFiles );

		List<RealPoint> pts = null;
		switch( METHODS.valueOf( method ) )
		{
			case corners:
				pts = getCornerPoints(min, max);
				break;
			case faces:
				pts = getFaces(min, max).stream().map( x -> sampleFace( x, spacing))
						.flatMap( Collection::stream )
						.collect( Collectors.toList() );
				break;
			default:
				System.err.println("no method called: " + method );
				break;
		}	

		if( pts == null)
		{
			System.err.println("no points.");
			return null;
		}

		result = smallestContainingInterval( pts, transform );
		System.out.println("min " + Arrays.toString(Intervals.minAsDoubleArray( result )));
		System.out.println("max " + Arrays.toString(Intervals.maxAsDoubleArray( result )));

		return result;
	}

	public static FinalRealInterval smallestContainingInterval( List< RealPoint > pts , final RealTransform transform )
	{
		int nd = pts.get( 0 ).numDimensions();
		double[] min = new double[ nd ];
		double[] max = new double[ nd ];
		
		Arrays.fill(min, Double.MAX_VALUE);
		Arrays.fill(max, Double.MIN_VALUE);
		
		RealPoint pXfm = new RealPoint( nd );
		for( RealPoint p : pts )
		{
			transform.apply( p, pXfm );
			
			for( int d = 0; d < nd; d++ )
			{
				if( pXfm.getDoublePosition( d ) < min[ d ])
				{
					min[ d ]  = pXfm.getDoublePosition( d );
				}

				if( pXfm.getDoublePosition( d ) > max[ d ])
				{
					max[ d ]  = pXfm.getDoublePosition( d );
				}
			}
		}
	
		return new FinalRealInterval(min, max);
	}

	public static List<RealInterval> getFaces(final double[] min, final double[] max)
	{
		int nd = min.length;
		RealInterval interval = new FinalRealInterval( min, max );

		ArrayList< RealInterval > faces = new ArrayList<>();
		for( int d = 0; d < nd; d++ )
		{
			// face at min(d)
			faces.add( intervalHyperSlice( interval, d, min[ d ]));
			
			// face at max(d)
			faces.add( intervalHyperSlice( interval, d, max[ d ]));
		}

		return faces;
	}
	
	public static List< RealPoint > sampleFace( RealInterval interval, double[] spacing )
	{
		ArrayList< RealPoint > pts = new ArrayList<>();
		RealIntervalIterator it = new RealIntervalIterator( interval, spacing );

		while( it.hasNext() )
		{
			RealPoint p = new RealPoint( interval.numDimensions() );
			p.setPosition( it );
			pts.add( p );
		}

		return pts;
	}
	
	public static RealInterval intervalHyperSlice( final RealInterval interval, int dim, double pos )
	{
		int nd = interval.numDimensions();
		double[] min = new double[ nd ];
		double[] max = new double[ nd ];
		for( int d = 0; d < nd; d++ )
		{
			if( d == dim )
			{
				min[ d ] = pos;
				max[ d ] = pos;
			}
			else
			{
				min[ d ] = interval.realMin( d );
				max[ d ] = interval.realMax( d );
			}
		}

		return new FinalRealInterval( min, max );
	}

	public static List<RealPoint> getCornerPoints(final double[] min, final double[] max)
	{
		int nd = min.length;

		// Let's be reasonable
		if( nd > 16 )
		{
			System.err.println( "Only deals with less than 16 dimensions" );
			return null;
		}

		int ncorners = 1 << nd; // 2^nd
		int bitcode = 1;

		ArrayList< RealPoint > pts = new ArrayList<>();
		for( int i = 0; i < ncorners; i++ )
		{
			bitcode = 1;
			RealPoint corner = new RealPoint( nd );
			for( int d = 0; d < nd; d++ )
			{
				if( (i & bitcode) > 0 )
				{
					corner.setPosition( max[ d ], d);
				}
				else
				{
					corner.setPosition( min[ d ], d);
				}
				
				bitcode = bitcode << 1;
			}
			pts.add( corner );
		}
		return pts; 
	}
	

}