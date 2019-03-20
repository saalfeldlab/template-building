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

import bigwarp.landmarks.LandmarkTableModel;
import io.AffineImglib2IO;
import io.DfieldIoHelper;
import io.cmtk.CMTKLoadAffine;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.DeformationFieldTransform;
import net.imglib2.realtransform.ExplicitInvertibleRealTransform;
import net.imglib2.realtransform.InvertibleDeformationFieldTransform;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.realtransform.inverse.InverseRealTransformGradientDescent;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;
import sc.fiji.io.Dfield_Nrrd_Reader;
import sc.fiji.io.NrrdDfieldFileInfo;

public class TransformReader
{
	public static final String INVFLAG = "i";
	public static final String AFFINEFLAG = "affine";
	public static final String DEFFLAG = "def";

	// string flags for iterative inverse optimization
	public static final String INVOPT_FLAG = "invopt";
	public static final String BETA_FLAG = "beta";
	public static final String C_FLAG = "c";
	public static final String MAXITERS_FLAG = "maxIters";
	public static final String TOLERANCE_FLAG = "tolerance";

	public static final String IDENTITY = "id";

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

	private static String pathFromInversePath( final String transform )
	{
		if( transform.contains( "?" ))
		{
			String[] parts = transform.split( "\\?" );
			return parts[ 0 ];
		}	
		else
			return transform;
	}

	public static void setIterativeInverseParameters( InverseRealTransformGradientDescent inverseOptimizer, String transformString )
	{
		String[] parts = transformString.split( "\\?" );
		for( String part: parts )
		{
			if( part.startsWith( INVOPT_FLAG ))
			{
				String[] paramParts = part.split(";");
				for( String param : paramParts )
				{
					System.out.println( param );
					if( param.startsWith( INVOPT_FLAG ))
					{
						continue;
					}
					else if( param.startsWith( TOLERANCE_FLAG ))
					{
						inverseOptimizer.setTolerance( 
								Double.parseDouble( param.substring( param.indexOf( "=" ) + 1 )));
					}
					else if( param.startsWith( C_FLAG ))
					{
						inverseOptimizer.setC( 
								Double.parseDouble( param.substring( param.indexOf( "=" ) + 1 )));
					}
					else if( param.startsWith( BETA_FLAG ))
					{
						inverseOptimizer.setBeta( 
								Double.parseDouble( param.substring( param.indexOf( "=" ) + 1 )));
					}
					else if( param.startsWith( MAXITERS_FLAG ))
					{
						inverseOptimizer.setMaxIters( 
								Integer.parseInt( param.substring( param.indexOf( "=" ) + 1 )));
					}
					else
					{
						System.out.println( "param part : (" + param + ") not parse-able.");
					}
				}
			}
		}
	}

	/**
	 * Reads an {@link InvertibleRealTransform}, and returns the forward transform.
	 * 
	 * @param transformPath
	 * @return the transform
	 */
	public static InvertibleRealTransform readInvertible( String transformPathFull )
	{

		boolean invert = isInverse( transformPathFull );
		String transformPath = pathFromInversePath( transformPathFull );
	
		if( transformPath.equals( IDENTITY ))
		{
			return new Scale3D( 1, 1, 1 );
		}
		else if( transformPath.contains( ".mat" ))
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
		else if( transformPath.contains( ".xform" ))
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
		else if( transformPath.contains( ".txt" ))
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
			return readH5Invertible( transformPath );
		}
		else if( transformPath.contains( ".csv" ) )
		{
			LandmarkTableModel ltm = new LandmarkTableModel( 3 );
			try
			{
				ltm.load( new File( transformPath ));
			}
			catch ( IOException e )
			{
				e.printStackTrace();
				return null;
			}
			ThinplateSplineTransform tps = new ThinplateSplineTransform( ltm.getTransform() );
			WrappedIterativeInvertibleRealTransform< ThinplateSplineTransform > invtps = new WrappedIterativeInvertibleRealTransform<>( tps );
			setIterativeInverseParameters( invtps.getOptimzer(), transformPathFull );

			if( invert )
				return invtps.inverse();
			else
				return invtps;
		}
		else
		{
			// other formats are displacement fields
			try
			{
				DfieldIoHelper dfieldIo = new DfieldIoHelper();
				// keep meta data
				ANTSDeformationField dfieldResult = dfieldIo.readAsDeformationField( transformPath );
				InvertibleDeformationFieldTransform< FloatType > invdef = new InvertibleDeformationFieldTransform< FloatType >( new DeformationFieldTransform< FloatType >( dfieldResult.getDefField() ) );
				setIterativeInverseParameters( invdef.getOptimzer(), transformPathFull );

				return invdef;
			}
			catch( Exception e )
			{
				e.printStackTrace();
				return null;
			}
		}
		
		return null;
	}

	public static RealTransform read( String transformPath )
	{

		InvertibleRealTransform result = readInvertible( transformPath );
		if( result != null )
			return result;

		// try reading as dfield
		DfieldIoHelper dfieldIo = new DfieldIoHelper();
		try
		{
			ANTSDeformationField dfieldResult = dfieldIo.readAsDeformationField( transformPath );
			return dfieldResult;
		}
		catch ( Exception e )
		{
			e.printStackTrace();
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

		H5TransformParameters params = H5TransformParameters.parse( transformArg );

		N5HDF5Reader n5;
		try
		{
			n5 = new N5HDF5Reader( params.path, 32, 32, 32, 3 );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
			return null;
		}

	
		if ( params.affineOnly )
		{
			System.out.println("h5 transform affine only");
			String dataset = params.inverse ? params.invdataset : params.fwddataset;
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
					n5, params.fwddataset, params.invdataset,
					new FloatType(),
					new NLinearInterpolatorFactory<FloatType>());
	
				if( params.inverse )
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

		H5TransformParameters params = H5TransformParameters.parse( transformArg );

		N5HDF5Reader n5;
		try
		{
			n5 = new N5HDF5Reader( params.path, 32, 32, 32, 3 );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
			return null;
		}

		// Check that relevant datasets exist in the specified base dataset
	
		if( params.fwddataset.isEmpty() )
		{
			System.err.println( "could not find dataset" );
			return null;
		}
		
		if ( params.affineOnly )
		{
			String dataset = params.inverse ? params.invdataset : params.fwddataset;
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
		else if( params.deformationOnly )
		{
			String dataset = params.inverse ? params.invdataset : params.fwddataset;
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
					params.fwddataset, params.invdataset,
					new FloatType(),
					new NLinearInterpolatorFactory<FloatType>());
	
				if( params.inverse )
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
	
	/**
	 * Returns the spatial field of view over which the transform given by the
	 * input string is defined.
	 * 
	 * The output interval will have [-inf, inf] if the transformation is
	 * defined everywhere.
	 * 
	 * @param s
	 *            a string representing the transform (usually a file path_
	 * @return the interval
	 * @throws IOException
	 * @throws FormatException
	 */
	public static RealInterval transformFieldOfView( String transformArg ) throws IOException, FormatException
	{
		boolean invert = isInverse( transformArg );
		String transformPath = pathFromInversePath( transformArg );

		double[] min = new double[ 3 ];
		double[] max = new double[ 3 ];

		long[] dims = null;
		double[] spacing = null;
		if ( transformArg.contains( ".nrrd" ) )
		{

			Dfield_Nrrd_Reader reader = new Dfield_Nrrd_Reader();
			File tmp = new File( transformPath );

			NrrdDfieldFileInfo hdr = reader.getHeaderInfo( tmp.getParent(), tmp.getName() );
			spacing = new double[] { hdr.pixelWidth, hdr.pixelHeight, hdr.pixelDepth };
			dims = new long[] { hdr.sizes[ 0 ], hdr.sizes[ 1 ], hdr.sizes[ 2 ] };
		}
		else if ( transformArg.contains( ".nii" ) )
		{
			ValuePair< long[], double[] > sizeAndRes = NiftiIo.readSizeAndResolution( new File( transformPath ) );
			dims = sizeAndRes.a;
			spacing = sizeAndRes.b;
		}
		else if ( transformArg.contains( ".h5" ) )
		{

			H5TransformParameters params = H5TransformParameters.parse( transformArg );
			String dataset = params.inverse ? params.invdataset : params.fwddataset;

			N5HDF5Reader n5 = new N5HDF5Reader( params.path, 32, 32, 32, 3 );
			dims = n5.getDatasetAttributes( dataset ).getDimensions();
			spacing = n5.getAttribute( dataset, N5DisplacementField.SPACING_ATTR, double[].class );
			if ( spacing == null )
				spacing = new double[] { 1, 1, 1 };
		}
		else
		{
			Arrays.fill( min, Double.NEGATIVE_INFINITY );
			Arrays.fill( min, Double.POSITIVE_INFINITY );
		}

		if ( dims != null && spacing != null )
		{
			// min is always going to be zero
			for ( int d = 0; d < 3; d++ )
				max[ d ] = dims[ d ] * spacing[ d ];
		}

		return new FinalRealInterval( min, max );
	}

	public static class H5TransformParameters
	{
		public final String path;
		public final String fwddataset;
		public final String invdataset;
		public final boolean inverse;
		public final boolean affineOnly;
		public final boolean deformationOnly;
		
		public H5TransformParameters(
			String path,
			String fwddataset,
			String invdataset,
			boolean inverse,
			boolean affineOnly,
			boolean deformationOnly )
		{
			this.path = path;
			this.fwddataset = fwddataset;
			this.invdataset = invdataset;
			this.inverse = inverse;
			this.affineOnly = affineOnly;
			this.deformationOnly = deformationOnly;
		}
	
		public static H5TransformParameters parse( final String transformArg )
		{
			String path = transformArg;
			String baseDataset = "";
			boolean inverse = false;
			boolean affineOnly = false;
			boolean deformationOnly = false;
			
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
						deformationOnly = true;
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

			return new H5TransformParameters( path, fwddataset, invdataset, inverse, affineOnly, deformationOnly );
		}
	}
	
}
