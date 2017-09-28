package transforms;

import org.janelia.utility.parse.ParseUtils;

import net.imglib2.realtransform.AffineTransform3D;

public class CenterAffineTransform
{

	public static void main( String[] args )
	{
		double[] mtx = reorder( ParseUtils.parseDoubleArray( args[ 0 ] ));
		double[] center = ParseUtils.parseDoubleArray( args[ 1 ] );

		double[] params = centerParams( mtx, center );

//		System.out.println( " " );
//		System.out.println( Torig );
//		System.out.println( M );

		System.out.println("#Insight Transform File V1.0");
		System.out.println("#Transform 0");
		System.out.println("Transform: MatrixOffsetTransformBase_double_3_3");
		System.out.println( String.format( "Parameters: %s", 
				params2String(  params )));
		System.out.println( String.format( "FixedParameters: %s", 
				params2String( center )));
	}
	
	public static double[] centerParams( final double[] mtxImglib, final double[] center )
	{
		AffineTransform3D T = new AffineTransform3D();
		T.set( mtxImglib );

		AffineTransform3D centering = new AffineTransform3D();
		centering.setTranslation( center );

		// this is correct (finally
		T.preConcatenate( centering.inverse() ).concatenate( centering );

		double[] params = new double[ 12 ];
		T.toArray( params );
		return reorderBack( params );
	}

	public static String params2String( double[] x )
	{
		StringBuilder sb = new StringBuilder();
		for( int i = 0; i < x.length; i++ )
		{
			sb.append( x[ i ] );
			sb.append( ' ' );
		}
		return sb.toString();
	}

	/**
	 * 
	 * @param mtx the matrix
	 */
	public static double[] reorder( final double[] mtx )
	{
		double[] out = new double[ mtx.length ];
		out[ 0 ] = mtx[ 0 ];
		out[ 1 ] = mtx[ 1 ];
		out[ 2 ] = mtx[ 2 ];
		
		out[ 3 ] = mtx[ 9 ];
		
		out[ 4 ] = mtx[ 3 ];
		out[ 5 ] = mtx[ 4 ];
		out[ 6 ] = mtx[ 5 ];
		
		out[ 7 ] = mtx[ 10 ];
		
		out[  8 ] = mtx[ 6 ];
		out[  9 ] = mtx[ 7 ];
		out[ 10 ] = mtx[ 8 ];
		
		out[ 11 ] = mtx[ 11 ];
		
		return out;
	}
	
	/**
	 * 
	 * @param mtx the matrix
	 */
	public static double[] reorderBack( final double[] mtx )
	{
		double[] out = new double[ mtx.length ];
		out[ 0 ] = mtx[ 0 ];
		out[ 1 ] = mtx[ 1 ];
		out[ 2 ] = mtx[ 2 ];

		out[ 3 ] = mtx[ 4 ];
		out[ 4 ] = mtx[ 5 ];
		out[ 5 ] = mtx[ 6 ];

		out[ 6 ] = mtx[ 8 ];
		out[ 7 ] = mtx[ 9 ];
		out[ 8 ] = mtx[ 10 ];

		out[  9 ] = mtx[ 3 ];
		out[ 10 ] = mtx[ 7 ];
		out[ 11 ] = mtx[ 11 ];
		
		return out;
	}
	

}
