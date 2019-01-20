package process;

import java.util.Arrays;

import io.DfieldIoHelper;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class DfieldComparisons
{

	public static void main( String[] args ) throws Exception
	{
		String dfield1Path = args[ 0 ];
		String dfield2Path = args[ 1 ];
		
		DfieldIoHelper dfieldIo = new DfieldIoHelper();
		RandomAccessibleInterval< FloatType > dfield1 = dfieldIo.read( dfield1Path );
		RandomAccessibleInterval< FloatType > dfield2 = dfieldIo.read( dfield2Path );
	
		System.out.println( " dfield1: " + Util.printInterval( dfield1 ));
		System.out.println( " dfield2: " + Util.printInterval( dfield2 ));
		
//		System.out.println( " spacing: " + Arrays.toString( dfieldIo.spacing ));
		
		IntervalIterator it = new IntervalIterator( 
				new long[]{
						dfield1.min( 0 ),
						dfield1.min( 1 ),
						dfield1.min( 2 ),
						0
				}, 
				new long[]{
						dfield1.max( 0 ),
						dfield1.max( 1 ),
						dfield1.max( 2 ),
						0
				} );
		
		
		RandomAccess< FloatType > ra1 = dfield1.randomAccess();
		RandomAccess< FloatType > ra2 = dfield2.randomAccess();
		
		double avgErrMag = 0;
		double minErrMag = Double.MAX_VALUE;
		double maxErrMag = Double.MIN_VALUE;

		long i = 0 ;
		double[] errV = new double[ 3 ];
		while( it.hasNext() )
		{
			it.fwd();

			ra1.setPosition( it );
			ra1.setPosition( 0, 3 );

			ra2.setPosition( it );
			ra2.setPosition( 0, 3 );

			
			err( errV, ra1, ra2 );
			double errMag = Math.sqrt( errV[0]*errV[0] + errV[1]*errV[1] + errV[2]*errV[2] );
			
			avgErrMag += errMag;
			
			if( errMag < minErrMag )
				minErrMag = errMag;
			
			if( errMag > maxErrMag )
				maxErrMag = errMag;


			i++;
		}
		avgErrMag /= i;
		
		System.out.println( "min error magnitude : " + minErrMag );
		System.out.println( "avg error magnitude : " + avgErrMag );
		System.out.println( "max error magnitude : " + maxErrMag );
		
	}

	public static <T extends RealType<T>,S extends RealType<S>> void err( double[] err, RandomAccess<T> truth, RandomAccess<S> approx )
	{
		for( int d = 0; d < 3; d++ )
		{
			truth.setPosition( d, 3 );
			approx.setPosition( d, 3 );
			err[ d ] = truth.get().getRealDouble() - approx.get().getRealDouble();
		}
	}
}
