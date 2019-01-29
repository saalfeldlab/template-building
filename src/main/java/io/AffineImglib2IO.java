package io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;

public class AffineImglib2IO
{

	public static AffineTransform readXfm( int ndims, File f ) throws IOException
	{
		return readXfm( ndims, f, "," );
	}

	public static AffineTransform readXfm( int ndims, File f, String delimeter ) throws IOException
	{
		return readXfm( ndims,
				new String( Files.readAllBytes( Paths.get( f.getAbsolutePath() ))),
				delimeter );
	}
	
	public static void writeXfm( File f, AffineGet affine ) throws IOException
	{
		String s = "";
//		System.out.println(affine.toString());
		
		for( int r = 0; r < affine.numDimensions(); r++ )
		{
			for( int c = 0; c < affine.numDimensions() + 1; c++ )
			{
				s += affine.get( r, c );
				if( r < affine.numDimensions() - 1 
						|| c < affine.numDimensions() )
				{
					s += ", ";
				}
			}
		}
//		System.out.println( s );
		List<String> lines = new ArrayList<String>();
		lines.add( s );
		Files.write( Paths.get( f.getAbsolutePath() ), lines );
	}
	
	public static AffineTransform readXfm( int ndims, String s, String delimeter )
	{
		double[] data = new double[ ndims * ( ndims + 1 ) ];
		String[] array = s.split( delimeter );
		int i = 0;
		for( String elem : array )
			data[ i++ ] = Double.parseDouble( elem );
		
		AffineTransform xfm = new AffineTransform( ndims );
		xfm.set( data );
		
		return xfm;
	}
	
	public static void main( String[] args ) throws IOException
	{
		AffineTransform xfm = readXfm( 3, new File( args[0]) );
		System.out.println( xfm );
		writeXfm( new File(""), xfm );
	}

}
