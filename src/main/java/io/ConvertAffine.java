package io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;
import org.janelia.saalfeldlab.transform.io.TransformReader;

import io.cmtk.CMTKLoadAffine;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ants.ANTSLoadAffine;

public class ConvertAffine {

	public static void main(String[] args) throws IOException
	{
		String input = args[ 0 ];
		String output = args[ 1 ];

		AffineGet inaffine = load( input );
		System.out.println( inaffine );
		write( inaffine, output );
	}
	
	public static AffineGet load( final String input ) throws IOException
	{
		// force h5 files to read only the affine part 
		if( input.contains("h5") || input.contains("hdf5") )
		{
			String h5affineString = h5StringToh5AffineString( input );
			System.out.println( h5affineString );
			return (AffineGet)TransformReader.readH5Invertible( h5affineString );
		}
		else
			return (AffineGet)TransformReader.readInvertible( input );
	}

	public static String h5StringToh5AffineString(final String input) {
		String[] parts = input.split("\\?");
		StringBuffer out = new StringBuffer();

		for (int i = 0; i < 4; i++) {
			if (i == 3)
				out.append(TransformReader.AFFINEFLAG);
			else if (i < parts.length)
				out.append(parts[i]);

			if (i < 3)
				out.append('?');
		}

		return out.toString();
	}

	public static void write( final AffineGet affine, final String output ) throws IOException
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
			Files.write( Paths.get(output), 
					ANTSLoadAffine.toHomogeneousMatrixString( as3d( affine )).getBytes(),
					StandardOpenOption.CREATE );
		}
		else if( output.contains("h5"))
			System.err.println("Cannot write to h5 directly");
		else
			System.err.println("Did not recognize output extension.");
	}
	
	public static AffineTransform3D as3d( AffineGet in )
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
