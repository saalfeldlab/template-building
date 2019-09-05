package io.elastix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.imglib2.realtransform.AffineTransform3D;

public class ElastixLoadAffine
{
	
	public static final String NUM_PARAMS_STRING = "(NumberOfParameters ";
	public static final String PARAMS_STRING = "(TransformParameters ";
	public static final String CENTER_STRING = "(CenterOfRotationPoint ";

	public static void main( String[] args ) throws IOException
	{
		AffineTransform3D xfm = load( args[ 0 ] );
		System.out.println( Arrays.stream(xfm.getRowPackedCopy()).mapToObj( Double::toString ).collect( Collectors.joining(",") ));
	}
	
	public static AffineTransform3D load( String path ) throws IOException
	{
		List< String > lines = Files.readAllLines( Paths.get( path ));
		double[] params = null;
		double[] center = null;
		for( String line : lines )
		{
//			if ( line.startsWith( NUM_PARAMS_STRING ))
//			{
//				String s = line.replaceAll( "\\" + NUM_PARAMS_STRING, "" ).replaceAll("\\)","").trim();
//				System.out.println( s );
//				int n = Integer.parseInt( s );
//				params = new double[ n ];
//			}

			if ( line.startsWith( PARAMS_STRING ))
			{
				String paramString = line.replaceAll( "\\" + PARAMS_STRING, "" ).replaceAll("\\)","").trim();
				String[] split = paramString.split( " " );
				params = Arrays.stream( split ).mapToDouble( Double::parseDouble ).toArray();
			}
			else if ( line.startsWith( CENTER_STRING ))
			{
				String paramString = line.replaceAll( "\\" + CENTER_STRING, "" ).replaceAll("\\)","").trim();
				String[] split = paramString.split( " " );
				center = Arrays.stream( split ).mapToDouble( Double::parseDouble ).toArray();
			}
		}
		
		AffineTransform3D translation = new AffineTransform3D();
		translation.setTranslation( center );

		AffineTransform3D out = new AffineTransform3D();
		out.set( params[0], params[1], params[2], params[ 9] ,
				 params[3], params[4], params[5], params[10] ,
				 params[6], params[7], params[8], params[11] );
		
//		System.out.println( out );
//		System.out.println( translation );

		out.concatenate( translation.inverse() ).preConcatenate( translation );
		return out;
	}

}