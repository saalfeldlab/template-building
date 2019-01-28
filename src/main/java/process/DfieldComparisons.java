package process;

import io.DfieldIoHelper;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;

public class DfieldComparisons
{

	public static void main( String[] args ) throws Exception
	{
		String dfield1Path = args[ 0 ];
		String dfield2Path = args[ 1 ];
		
		DfieldIoHelper dfieldIo = new DfieldIoHelper();
//		RandomAccessibleInterval< FloatType > dfield1 = dfieldIo.read( dfield1Path );
//		RandomAccessibleInterval< FloatType > dfield2 = dfieldIo.read( dfield2Path );
		
		ANTSDeformationField obj1 = dfieldIo.readAsDeformationField( dfield1Path );
		ANTSDeformationField obj2 = dfieldIo.readAsDeformationField( dfield2Path );

		RandomAccessibleInterval< FloatType > dfield1 = obj1.getImg();
		RandomAccessibleInterval< FloatType > dfield2 = obj2.getImg();
	
		System.out.println( " dfield1: " + Util.printInterval( dfield1 ));
		System.out.println( " dfield2: " + Util.printInterval( dfield2 ));
		
		// the interval (in physical space) on which to measure
		FinalInterval processingInterval = ANTSDeformationField.largestIntervalFromRealInterval( obj1.getDefInterval(), obj1.getResolution() );
		IntervalIterator it = new IntervalIterator( processingInterval );
		
		RealRandomAccess< FloatType > ra1 = obj1.getDefField().realRandomAccess();
		RealRandomAccess< FloatType > ra2 = obj2.getDefField().realRandomAccess();
		
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

	public static <T extends RealType<T>,S extends RealType<S>> void err( double[] err, RealRandomAccess<T> truth, RealRandomAccess<S> approx )
	{
		for( int d = 0; d < 3; d++ )
		{
			truth.setPosition( d, 3 );
			approx.setPosition( d, 3 );
			err[ d ] = truth.get().getRealDouble() - approx.get().getRealDouble();
		}
	}
}
