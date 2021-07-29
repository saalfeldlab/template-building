#@ Dataset (label="Displacement field") dfieldRaw

import net.imglib2.*;
import net.imglib2.util.*;
import net.imglib2.view.*;
import net.imglib2.view.composite.*;


def magnitude( Composite c, int N )
{
	double out = 0.0;
	for( int i = 0; i < N; i++ )
	{
		double v = c.get( i ).getRealDouble();
		out += ( v * v );
	}
	return Math.sqrt( out );
}

def stats( RandomAccessibleInterval dfieldVec )
{
	double magSum = 0.0;
	double magMax = Double.MIN_VALUE;
	double magMin = Double.MAX_VALUE;

	nc = (int)dfield.dimension( dfield.numDimensions() - 1 );
	c = Views.flatIterable( dfieldVec ).cursor();
	while( c.hasNext()) {
		Composite v = c.next();
		double mag = magnitude(v, nc);
		
		magSum += mag;
		
		if( mag > magMax )
			magMax = mag;

		if( mag < magMin )
			magMin = mag;
	}

	double magMean = magSum / Intervals.numElements( dfieldVec );
	println( "min magnitude  : " + magMin );
	println( "max magnitude  : " + magMax );
	println( "mean magnitude : " + magMean );
}


dfield = Views.permute( dfieldRaw, 2, 3 );
println( Intervals.toString( dfield ) );

stats( Views.collapse( dfield ))

