package io;

import static org.junit.Assert.*;

import org.junit.Test;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class DfieldAxisPermutation
{

	@Test
	public void testPermutation() throws Exception
	{
		FinalInterval ai = new FinalInterval( 3, 10, 11, 12 );
		FinalInterval bi = new FinalInterval( 10, 3, 11, 12 );
		FinalInterval ci = new FinalInterval( 10, 11, 3, 12 );
		FinalInterval di = new FinalInterval( 10, 11, 12, 3 );
		
		FinalInterval[] list = new FinalInterval[]{  ai, bi, ci, di };

		System.out.println( ai );
		System.out.println( bi );
		System.out.println( ci );
		System.out.println( di );
		
		RandomAccessible< UnsignedByteType > ra = ConstantUtils.constantRandomAccessible( new UnsignedByteType(), 4 );
		
		for( int i = 0; i < 4; i++ )
			for( int j = 0; j < 4; j++ )
			{
				IntervalView< UnsignedByteType > src = Views.interval( ra, list[ j ]  );
				RandomAccessibleInterval< UnsignedByteType > srcPermuted = DfieldIoHelper.vectorAxisPermute( src, 3, i );

//				System.out.println( Util.printInterval( srcPermuted ));
//				System.out.println( Util.printInterval( list[i] ));
//				System.out.println( " " );

				assertTrue( String.format( "permute %d to %d", j, i ), equals( srcPermuted, list[i] ) );
			}
	}

	public static boolean equals( final Interval a, final Interval b )
	{
		if( a.numDimensions() != b.numDimensions() )
			return false;
		
		for ( int d = 0; d < a.numDimensions(); d++ )
		{
			if( a.min( d ) != b.min( d) )
				return false;

			if( a.max( d ) != b.max( d) )
				return false;
		}
		return true;	
	}
}
