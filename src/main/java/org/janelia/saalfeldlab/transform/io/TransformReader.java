package org.janelia.saalfeldlab.transform.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.AffineImglib2IO;
import io.cmtk.CMTKLoadAffine;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.DeformationFieldTransform;
import net.imglib2.realtransform.ExplicitInvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.real.FloatType;

public class TransformReader
{
	public static final String INVFLAG = "i";
	public static final String AFFINEFLAG = "affine";
	public static final String DEFFLAG = "def";

	final static Logger logger = LoggerFactory.getLogger( TransformReader.class );


	public static RealTransformSequence readTransforms( List<String> transformList )
	{
		RealTransformSequence totalTransform = new RealTransformSequence();
		for( String transform : transformList )
		{
			if( isInverse( transform ))
				totalTransform.add( readInvertible( transform ));
			else
				totalTransform.add( read( transform ));
		}
	
		return totalTransform;
	}
	
	private static boolean isInverse( final String transform )
	{
		if( transform.contains( "?" ))
		{
			String[] parts = transform.split( "\\?" );
			if( parts[ parts.length - 1 ].equals( INVFLAG ) )
			{
				return true;
			}
		}	
		return false;
	}

	/**
	 * Reads an {@link InvertibleRealTransform}, and returns the forward transform.
	 * 
	 * @param transformPath
	 * @return the transform
	 */
	public static InvertibleRealTransform readInvertible( String transformPath )
	{
		boolean invert = isInverse( transformPath );
	
		if( transformPath.endsWith( "mat" ))
		{
			try
			{
				AffineTransform3D xfm = ANTSLoadAffine.loadAffine( transformPath );
				if( invert )
				{
					return xfm.inverse().copy();
				}

				return xfm;
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}	
		else if( transformPath.endsWith( "xform" ))
		{
			try
			{
				CMTKLoadAffine reader = new CMTKLoadAffine();
				AffineTransform3D xfm = reader.load( new File( transformPath ));
				if( invert )
				{
					return xfm.inverse().copy();
				}

				return xfm;
			}
			catch( Exception e )
			{
				e.printStackTrace();
			}
		}
		else if( transformPath.endsWith( "txt" ))
		{
			try
			{
				if( Files.readAllLines( Paths.get( transformPath ) ).get( 0 ).startsWith( "#Insight Transform File" ))
				{
					logger.debug("Reading itk transform file");
					try
					{
						AffineTransform3D xfm = ANTSLoadAffine.loadAffine( transformPath );
						if( invert )
						{
							System.out.println("inverting");
							return xfm.inverse().copy();
						}
						return xfm;
					} catch ( IOException e )
					{
						e.printStackTrace();
					}
				}
				else
				{
					System.out.println("Reading imglib2 transform file");
					AffineTransform xfm = AffineImglib2IO.readXfm( 3, new File( transformPath ) );
					System.out.println( Arrays.toString(xfm.getRowPackedCopy() ));
					if( invert )
					{
						System.out.println("inverting");
						return xfm.inverse().copy();
					}
					return xfm;
				}
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if( transformPath.contains( ".h5" ) || transformPath.contains( ".hdf5" )) 
		{
			System.out.println( "reading h5");
			return readH5Invertible( transformPath );
		}
		
		return null;
	}

	public static RealTransform read( String transformPath )
	{
		if( transformPath.contains( ".h5" ))
		{
			return readH5( transformPath );
		}

		return null;
	}
	
	/**
	 * Returns a {@link RealTransform} whose path and dataset are given by the specified h5 argument string.
	 *
	 * Strings of the form "path?dataset_root?i" are permitted, where:
	 *  <p> 
	 * 	path is the path to an hdf5 file.
	 *	<p> 
	 *  dataset_root is the parent dataset to the transformation or transformations 
	 *  (note that dfield and invdfield are required dataset names).
	 *  <p>
	 *  i is the single character 'i' and indicates that the inverse of the transform should be returned.
	 *  if no inverse is present, this method will return null.
	 *  
	 *  if "dataset_root" is not provided or left empty, this method will attempt to use the root dataset.
	 *  If a 'dfield' dataset is not found, this method will use a transform in /0/dfield if it exists. 
	 *  <p>
	 *  Examples:
	 *  <p>
	 *  The string "/home/user/transform.h5" checks the default datasets, and returns the forward transform
	 *  <p>
	 *  The string "/home/user/transform.h5??i" checks the default datasets, and returns the inverse transform.
	 *  <p>
	 *  The string "/home/user/transform.h5?/1?i" checks for the dataset /1/invdfield, and returns the inverse transform.
	 *  <p>
	 *  The string "/home/user/transform.h5?/1" checks for the dataset /1/dfield, and returns the forward transform.
	 * 
	 * 
	 * @param transformArg the path / dataset. 
	 * @return the transform 
	 */
	public static InvertibleRealTransform readH5Invertible( String transformArg ) {

		String path = transformArg;
		String baseDataset = "";
		boolean inverse = false;
		boolean affineOnly = false;

		if( transformArg.contains( "?" ))
		{
			String[] split = transformArg.split( "\\?" );
			path = split[ 0 ];
			baseDataset = split[ 1 ];
			
			if( split.length > 2  && split[ 2 ].equals( INVFLAG ))
				inverse = true;

			if( split.length > 3 )
				if( split[ 3 ].equals( AFFINEFLAG ) )
					affineOnly = true;
		}

		N5HDF5Reader n5;
		try
		{
			n5 = new N5HDF5Reader( path, 32, 32, 32, 3 );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
			return null;
		}

		// Check that relevant datasets exist in the specified base dataset
		String fwddataset = "";
		String invdataset = "";
		if( baseDataset.isEmpty() )
		{
			if( n5.datasetExists( "/" + N5DisplacementField.FORWARD_ATTR ) && 
				n5.datasetExists( "/" + N5DisplacementField.INVERSE_ATTR ))
			{
				fwddataset = "/" + N5DisplacementField.FORWARD_ATTR;
				invdataset = "/" + N5DisplacementField.INVERSE_ATTR;
			}
			else if( n5.datasetExists( "/0/" + N5DisplacementField.FORWARD_ATTR ) && 
					 n5.datasetExists( "/0/" + N5DisplacementField.INVERSE_ATTR ))
			{
				fwddataset = "/0/" + N5DisplacementField.FORWARD_ATTR;
				invdataset = "/0/" + N5DisplacementField.INVERSE_ATTR;
			}
		}
		else if( n5.datasetExists( baseDataset + "/" + N5DisplacementField.FORWARD_ATTR ) && 
				 n5.datasetExists( baseDataset + "/" + N5DisplacementField.INVERSE_ATTR ))
		{
			fwddataset = baseDataset + "/" + N5DisplacementField.FORWARD_ATTR;
			invdataset = baseDataset + "/" + N5DisplacementField.INVERSE_ATTR;
		}
	
		if ( affineOnly )
		{
			System.out.println("h5 transform affine only");
			String dataset = inverse ? invdataset : fwddataset;
			try
			{
				return N5DisplacementField.openAffine( n5, dataset );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				return null;
			}
		}	
		else
		{
			try
			{
				ExplicitInvertibleRealTransform xfm = N5DisplacementField.openInvertible(
					n5, fwddataset, invdataset,
					new FloatType(),
					new NLinearInterpolatorFactory<FloatType>());
	
				if( inverse )
					return xfm.inverse();
				else
					return xfm;
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				return null;
			}	
		}
	}

	public static RealTransform readH5( String transformArg ) {

		String path = transformArg;
		String baseDataset = "";
		boolean inverse = false;
		boolean affineOnly = false;
		boolean deformableOnly = false;

		if( transformArg.contains( "?" ))
		{
			String[] split = transformArg.split( "\\?" );
			path = split[ 0 ];
			baseDataset = split[ 1 ];
			
			if( split.length > 2  && split[ 2 ].equals( INVFLAG ))
				inverse = true;

			if( split.length > 3 )
				if( split[ 3 ].equals( AFFINEFLAG ) )
					affineOnly = true;
				else if( split[ 3 ].equals( DEFFLAG ) )
					deformableOnly = true;
		}

		N5HDF5Reader n5;
		try
		{
			n5 = new N5HDF5Reader( path, 32, 32, 32, 3 );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
			return null;
		}

		// Check that relevant datasets exist in the specified base dataset
		//String xfmDataset = inverse ? N5DisplacementField.INVERSE_ATTR : N5DisplacementField.FORWARD_ATTR;

		String fwddataset = "";
		String invdataset = "";
		if( baseDataset.isEmpty() )
		{
			if( n5.datasetExists( "/" + N5DisplacementField.FORWARD_ATTR ) && 
				n5.datasetExists( "/" + N5DisplacementField.INVERSE_ATTR ))
			{
				fwddataset = "/" + N5DisplacementField.FORWARD_ATTR;
				invdataset = "/" + N5DisplacementField.INVERSE_ATTR;
			}
			else if( n5.datasetExists( "/0/" + N5DisplacementField.FORWARD_ATTR ) && 
					 n5.datasetExists( "/0/" + N5DisplacementField.INVERSE_ATTR ))
			{
				fwddataset = "/0/" + N5DisplacementField.FORWARD_ATTR;
				invdataset = "/0/" + N5DisplacementField.INVERSE_ATTR;
			}
		}
		else if( n5.datasetExists( baseDataset + "/" + N5DisplacementField.FORWARD_ATTR ) && 
				 n5.datasetExists( baseDataset + "/" + N5DisplacementField.INVERSE_ATTR ))
		{
			fwddataset = baseDataset + "/" + N5DisplacementField.FORWARD_ATTR;
			invdataset = baseDataset + "/" + N5DisplacementField.INVERSE_ATTR;
		}
	
		if( fwddataset.isEmpty() )
		{
			System.err.println( "could not find dataset" );
			return null;
		}
		
		if ( affineOnly )
		{
			String dataset = inverse ? invdataset : fwddataset;
			try
			{
				return N5DisplacementField.openAffine( n5, dataset );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				return null;
			}
		}	
		else if( deformableOnly )
		{
			String dataset = inverse ? invdataset : fwddataset;
			try
			{
				return new DeformationFieldTransform<>( 
						N5DisplacementField.openCalibratedField( 
								n5, dataset, new NLinearInterpolatorFactory<>(), new FloatType() ));
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				return null;
			}	
		}
		else
		{
			try
			{
				ExplicitInvertibleRealTransform xfm = N5DisplacementField.openInvertible(
					n5,
					fwddataset, invdataset,
					new FloatType(),
					new NLinearInterpolatorFactory<FloatType>());
	
				if( inverse )
					return xfm.inverse();
				else
					return xfm;
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				return null;
			}	
		}
	}
	
}
