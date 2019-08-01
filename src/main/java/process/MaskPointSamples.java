package process;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Callable;


import io.IOHelper;
import net.imglib2.Cursor;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.1.1-SNAPSHOT",
		description="Take random samples in a mask image.")
public class MaskPointSamples<T extends RealType<T>> implements Callable<Void>
{
	@Option( names = { "-m", "--mask" }, required = true, description = "Mask image path" )
	private String maskPath;

	@Option( names = { "-n", "--num-samples" }, required = true, description = "Number of samples" )
	private int N;

	@Option( names = { "-t", "--threshold" }, required = false, description = "Threshold.  Only"
			+ "pixels with intensities strictly greater than this value are considered." )
	private double threshold = 0;

	@Option( names = { "-d", "--delimeter" }, required = false, description = "Delimeter" )
	private String delimiter = ",";

	@Option( names = { "--physical" }, required = false, description = "Return physical (calibrated) coordinates, "
			+ "rather than pixel locations" )
	private boolean physical = false;

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public static void main( String[] args )
	{
		CommandLine.call( new MaskPointSamples(), args );
	}

	/**
	 * Generates point samples.
	 * 
	 * java process.PointSamples
	 */
	public Void call()
	{

		IOHelper io = new IOHelper();
		RandomAccessibleInterval<?> maskimg = io.readRai( new File( maskPath ) );
		double[] res = io.getResolution();

		int nd = maskimg.numDimensions();

		@SuppressWarnings( "unchecked" )
		ArrayList< Long > indicies = indexListPassingThreshold( ( RandomAccessibleInterval< T > ) maskimg, threshold );
		
		Collections.shuffle( indicies );

		long[] dimensions = Intervals.dimensionsAsLongArray( maskimg );

		long[] position = new long[ nd ];
		Point p = Point.wrap( position );

		double[] rpos = new double[ nd ];
		RealPoint rp = RealPoint.wrap( rpos );

		for( int j = 0; j < N; j++ )
		{
			StringBuffer str = new StringBuffer();
			for ( int i = 0; i < nd; i++ )
			{
				//Intervals.

				if( physical )
				{
					IntervalIndexer.indexToPosition( indicies.get( j ), dimensions, rpos );
					str.append( Double.toString( res[ i ] * rp.getDoublePosition( i ) ) );
				}
				else
				{
					IntervalIndexer.indexToPosition( indicies.get( j ), dimensions, position );
					str.append( Double.toString( p.getLongPosition( i )));
				}

				if ( i < nd - 1 )
					str.append( delimiter );

			}
			System.out.println( str.toString() );

		}

		return null;
	}
	
	public ArrayList<Long> indexListPassingThreshold( RandomAccessibleInterval<T> mask, double threshold )
	{
		ArrayList< Long > indicies = new ArrayList<Long>();

		Cursor< T > c = Views.flatIterable( mask ).cursor();
		long i = 0;
		while( c.hasNext())
		{
			if( c.next().getRealDouble() > threshold )
			{
				indicies.add( i );
			}
			i++;
		}
		return indicies;
	}

}
