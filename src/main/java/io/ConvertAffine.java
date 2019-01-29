package io;

import java.io.File;
import java.io.IOException;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;
import org.python.google.common.io.Files;

import io.cmtk.CMTKLoadAffine;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ants.ANTSLoadAffine;

public class ConvertAffine {

	public static void main(String[] args) throws IOException
	{
		String input = args[ 0 ];
		String output = args[ 1 ];
		
		AffineTransform3D inaffine = load( input );
		System.out.println( inaffine );
		write( inaffine, output );
	}
	
	public static AffineTransform3D load( final String input ) throws IOException
	{
		if( input.endsWith("xform" ))
		{
			CMTKLoadAffine io = new CMTKLoadAffine();
			return io.load( new File( input ));
		}
		else if( input.endsWith("txt"))
		{
			//imglib2 packed row copy
			return as3d( AffineImglib2IO.readXfm(3, new File( input )));
		}
		else if( input.endsWith("mat"))
		{
			// text 
			return ANTSLoadAffine.loadAffine( input );
		}
		else if( input.contains("h5"))
		{
			String fpath = input;
			String dataset = "dfield";
			if( input.contains(":"))
			{
				String[] pathAndDataset = input.split(":");
				fpath = pathAndDataset[ 0 ];
				dataset = pathAndDataset[ 1 ];
			}

			N5Reader n5 = new N5HDF5Reader( fpath, 32, 32, 32, 3 );
			AffineTransform3D out = new AffineTransform3D();
			out.set( n5.getAttribute( dataset, N5DisplacementField.AFFINE_ATTR, double[].class ));
			return out;
		}
		return null;
	}

	public static void write( final AffineTransform3D affine, final String output ) throws IOException
	{
		
		if( output.endsWith("xform" ))
		{
			System.err.println("Use CMTK's mat2dof utility");
		}
		else if( output.endsWith("txt"))
		{
			AffineImglib2IO.writeXfm(new File(output), affine);
		}
		else if( output.endsWith("mat"))
		{
			Files.write( ANTSLoadAffine.toHomogeneousMatrixString(affine).getBytes(),
					new File( output ));
		}
		else if( output.contains("h5"))
		{
			System.err.println("Cannot write to h5 directly");
		}
	}
	
	public static AffineTransform3D as3d( AffineTransform in )
	{
		double[] data = new double[ 12 ];
		int k = 0;
		for( int i = 0; i < 3; i++ )
			for( int j = 0; j < 4; j++ )
				data[ k++ ] = in.get( i, j );
	
		AffineTransform3D out = new AffineTransform3D();
		out.set( data );
		return out;
	}

}
