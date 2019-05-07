//@Dataset(label="dataset") img
//@Double( label="x") x
//@Double( label="y") y
//@Double( label="z") z
//@Double( label="resolution x") rx
//@Double( label="resolution y") ry
//@Double( label="resolution z") rz
//@UIService ui

import java.util.Arrays;
import net.imglib2.util.Util;

def centralDerivatives( ra, dims, res, dim_idx )
{
	derivs = new double[ 3 ];
	i = 0;
	for( d in dims )
	{
		ra.bck(d);
		b = ra.get().getRealDouble();
		ra.fwd(d); ra.fwd(d);
		f = ra.get().getRealDouble();
		ra.bck(d);

		if( d == dim_idx )
			derivs[i] = 1 + (f - b)/(res[i] * 2.0 );
		else
			derivs[i] = (f - b)/(res[i] * 2.0 );
		i++;
	}
	return derivs
}

System.out.println( Util.printInterval( img ));
ra = img.randomAccess();


ra.setPosition( [x,y,0,z] as long[])
//System.out.println( Util.printCoordinates( ra ));
//System.out.println( ra.get().getRealDouble());

/*
c = ra.get().getRealDouble();
ra.fwd(0);
fx = ra.get().getRealDouble();
ra.bck(0);ra.bck(0);
bx = ra.get().getRealDouble();
*/

res = [rx, ry, rz] as double[]

System.out.println( Util.printCoordinates( ra ));
dx = centralDerivatives( ra, [0,1,3] as int[], res, 0 );
ra.fwd(2);
System.out.println( Util.printCoordinates( ra ));
dy = centralDerivatives( ra, [0,1,3] as int[], res, 1 );
ra.fwd(2);
System.out.println( Util.printCoordinates( ra ));
dz = centralDerivatives( ra, [0,1,3] as int[], res, 3 );

System.out.println( Arrays.toString( dx ));
System.out.println( Arrays.toString( dy ));
System.out.println( Arrays.toString( dz ));

det = dx[0]*dy[1]*dz[2] + dx[1]*dy[2]*dz[0] + dx[2]*dy[0]*dz[1] - dx[2]*dy[1]*dz[0] - dx[1]*dy[0]*dz[2] - dx[0]*dy[2]*dz[1];
System.out.println( det );


