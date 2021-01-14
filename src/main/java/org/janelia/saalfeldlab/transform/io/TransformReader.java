package org.janelia.saalfeldlab.transform.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
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
	public static final String STEP_TRIES_FLAG = "stepTries";

	public static final String IDENTITY = "id";

	final static Logger logger = LoggerFactory.getLogger( TransformReader.class );

	public static RealTransformSequence readTransforms( List<String> transformList )
	{
		RealTransformSequence totalTransform = new RealTransformSequence();
		if( transformList == null || transformList.size() < 1 )
			return totalTransform;
	
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
					else if( param.startsWith( STEP_TRIES_FLAG ))
					{
						inverseOptimizer.setStepSizeMaxTries( 
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
					AffineTransform xfm = AffineImglib2IO.readXfm( 3, new File( transformPath ) );
					if( invert )
					{
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
		else if( transformPath.contains( ".h5" ) || 
				 transformPath.contains( ".hdf5" ) ||
				 transformPath.contains( ".hdf" ) ||
				 transformPath.contains( ".n5" )) 
		{
			return readH5Invertible( transformPathFull );
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
				//DeformationFieldTransform< FloatType > dfield = dfieldIo.readAsRealTransform( transformPath );
				DeformationFieldTransform< FloatType > dfield = dfieldIo.readAsDeformationField( transformPath, new FloatType() );
				InvertibleDeformationFieldTransform< FloatType > invdef = new InvertibleDeformationFieldTransform< FloatType >( dfield );
				setIterativeInverseParameters( invdef.getOptimzer(), transformPathFull );

			if( invert )
			{
				return invdef.inverse();
			}
			else
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

		if( transformPath.contains( ".h5" ) || 
			transformPath.contains( ".hdf5" ) ||
			transformPath.contains( ".hdf" ) ||
			transformPath.contains( ".n5" )) 
		{
			RealTransform xfm = readH5( transformPath );
			if( xfm != null )
				return xfm;
		}
		// try reading as dfield
		DfieldIoHelper dfieldIo = new DfieldIoHelper();
		try
		{
			DeformationFieldTransform<FloatType> dfieldResult = dfieldIo.readAsDeformationField(
					transformPath, new FloatType());
//			DeformationFieldTransform<FloatType> dfieldResult = dfieldIo.readAsRealTransform( transformPath );
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
	 * 	path is the path to an hdf5 file or n5 container
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
	public static InvertibleRealTransform readH5Invertible( String transformArg )
	{
		H5TransformParameters params;
		N5Reader n5;
		try
		{
			params = H5TransformParameters.parse( transformArg );
			n5 = params.n5;
		}
		catch ( IOException e )
		{
			e.printStackTrace();
			return null;
		}

		return readH5Invertible( transformArg, params );
	}

	public static InvertibleRealTransform readH5Invertible( String transformArg, H5TransformParameters params )
	{
		N5Reader n5 = params.n5;

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

		H5TransformParameters params;
		N5Reader n5;
		try
		{
			params = H5TransformParameters.parse( transformArg );
			n5 = params.n5;
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
		else if( params.inverse )
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
		else
		{
			try
			{
				return N5DisplacementField.open( n5, params.fwddataset, false );
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
		double[] min = new double[ 3 ];
		double[] max = new double[ 3 ];

		ValuePair< long[], double[] > pair = transformSizeAndRes( transformArg );
		long[] dims = pair.a;
		double[] spacing = pair.b;

		if ( dims != null && spacing != null )
		{
			// min is always going to be zero
			for ( int d = 0; d < 3; d++ )
				max[ d ] = dims[ d ] * spacing[ d ];
		}

		return new FinalRealInterval( min, max );
	}

	public static ValuePair< long[], double[] > transformSpatialSizeAndRes( String transformArg ) throws FormatException, IOException
	{
		ValuePair< long[], double[] > sar = transformSizeAndRes( transformArg );
		long[] sz = sar.getA();
		double[] res = sar.getB();
		int nd = sz.length;
		int ndNew = nd - 1;

		int vectorIndex = -1;
		for ( int i = 0; i < nd; i++ )
			if ( sz[ i ] == ndNew )
				vectorIndex = i;

		if ( vectorIndex < 0 )
			return null;

		long[] szSpatial = new long[ ndNew ];
		double[] resSpatial = new double[ ndNew ];
		int j = 0;
		for ( int i = 0; i < nd; i++ )
		{
			if ( i != vectorIndex )
			{
				szSpatial[ j ] = sz[ i ];
				resSpatial[ j ] = res[ i ];
				j++;
			}
		}
		return new ValuePair< long[], double[] >( szSpatial, resSpatial );
	}

	public static ValuePair< long[], double[] > transformSizeAndRes( String transformArg ) throws FormatException, IOException
	{
		String transformPath = pathFromInversePath( transformArg );

		long[] dims = null;
		double[] spacing = null;

		if ( transformArg.contains( ".nrrd" ) )
		{

			Dfield_Nrrd_Reader reader = new Dfield_Nrrd_Reader();
			File tmp = new File( transformPath );

			NrrdDfieldFileInfo hdr = reader.getHeaderInfo( tmp.getParent(), tmp.getName() );
			spacing = new double[] { hdr.pixelWidth, hdr.pixelHeight, hdr.pixelDepth };
			dims = new long[] { hdr.sizes[ 1 ], hdr.sizes[ 2 ], hdr.sizes[ 3 ], hdr.sizes[0] };
			return new ValuePair<>( dims, spacing );
		}
		else if ( transformArg.contains( ".nii" ) )
		{
			return NiftiIo.readSizeAndResolution( new File( transformPath ) );
		}
		else if ( transformArg.contains( ".h5" ) )
		{

			H5TransformParameters params = H5TransformParameters.parse( transformArg );
			String dataset = params.inverse ? params.invdataset : params.fwddataset;

			N5HDF5Reader n5 = new N5HDF5Reader( params.path, 32, 32, 32, 3 );
			dims = n5.getDatasetAttributes( dataset ).getDimensions();
			spacing = n5.getAttribute( dataset, N5DisplacementField.SPACING_ATTR, double[].class );
			if ( spacing == null )
				spacing = new double[]{ 1, 1, 1 };

			return new ValuePair<>( dims, spacing );
		}
		else
		{
			long[] max = new long[ 3 ];
			Arrays.fill( max, Long.MAX_VALUE );

			return new ValuePair<>( max, new double[] { 1, 1, 1 } );
		}
	}
	
	public static N5Reader getN5Reader( final String path )
	{
		N5Reader n5 = null;
		if( path.contains(  ".n5" ))
		{
			try
			{
				n5 = new N5FSReader( path );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
				return null;
			}
		}
		else if( path.contains( ".h5" ) ||
				path.contains( ".hdf5" ) ||
				path.contains( ".hdf" ))
		{
			try
			{
				n5 = new N5HDF5Reader( path, 32, 32, 32, 3 );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
				return null;
			}
		}
		return n5;
	}

	public static class H5TransformParameters
	{
		public final String path;
		public final String fwddataset;
		public final String invdataset;
		public final boolean inverse;
		public final boolean affineOnly;
		public final boolean deformationOnly;
		public final N5Reader n5;
		
		public H5TransformParameters(
			String path,
			String fwddataset,
			String invdataset,
			boolean inverse,
			boolean affineOnly,
			boolean deformationOnly,
			N5Reader n5 )
		{
			this.path = path;
			this.fwddataset = fwddataset;
			this.invdataset = invdataset;
			this.inverse = inverse;
			this.affineOnly = affineOnly;
			this.deformationOnly = deformationOnly;
			this.n5 = n5;
		}
	
		public static H5TransformParameters parse( final String transformArg ) throws IOException
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

			N5Reader n5 = getN5Reader( path );

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
			else if( n5.datasetExists( baseDataset ))
			{
				fwddataset = baseDataset;
				invdataset = "";
			}
			else if( n5.datasetExists( baseDataset + "/" + N5DisplacementField.FORWARD_ATTR ) &&
					 n5.datasetExists( baseDataset + "/" + N5DisplacementField.INVERSE_ATTR ))
			{
				fwddataset = baseDataset + "/" + N5DisplacementField.FORWARD_ATTR;
				invdataset = baseDataset + "/" + N5DisplacementField.INVERSE_ATTR;
			}

			return new H5TransformParameters( path, fwddataset, invdataset, inverse, affineOnly, deformationOnly, n5 );
		}
	}

	public static AffineTransform3D from( AffineTransform xfm )
	{
		assert xfm.numDimensions() == 3;
		AffineTransform3D out = new AffineTransform3D();
		out.set( xfm.getRowPackedCopy());
		return out;
	}
}
