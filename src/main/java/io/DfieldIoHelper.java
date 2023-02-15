package io;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.janelia.saalfeldlab.transform.io.TransformReader.H5TransformParameters;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import loci.formats.FormatException;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.DisplacementFieldTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.realtransform.Scale2D;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.RealComposite;
import sc.fiji.io.Dfield_Nrrd_Reader;

public class DfieldIoHelper
{

	public static final String MULT_KEY = "multiplier";

	public RandomAccessibleInterval dfieldRAI;

	public double[] spacing;

	private AffineGet affine; // store the affine
	
	public static void main( String[] args ) throws Exception
	{
		String dfieldIn = args[ 0 ];
		String dfieldOut = args[ 1 ];

		if( isN5TransformBase( dfieldIn ) && isN5TransformBase( dfieldOut ))
		{
			convertN5Transform( dfieldIn, dfieldOut );
		}
		else
		{
			DfieldIoHelper io = new DfieldIoHelper();
			RandomAccessibleInterval dfield = io.read( dfieldIn );
			io.write( dfield, dfieldOut );
		}
	}

	public void setSpacing( final double[] spacing ) {
		this.spacing = spacing;
	}

	public static boolean isN5TransformBase( final String path )
	{
		return path.contains( "h5" ) ||
				path.contains( "hdf5" ) ||
				path.contains( "hdf" ) ||
				path.contains( "n5" );
	}

	public static N5Reader getN5Reader( final String path ) throws IOException
	{
		if ( path.contains( "h5" ) || path.contains( "hdf5" ) || path.contains( "hdf" ))
		{
			return new N5HDF5Reader( path, 3, 32, 32, 32 );
		}
		else if( path.contains("n5" ))
		{
			return new N5FSReader( path);
		}
		return null;
	}

	public static N5Writer getN5Writer( final String path ) throws IOException
	{
		if ( path.contains( "h5" ) || path.contains( "hdf5" ) || path.contains( "hdf" ))
		{
			return new N5HDF5Writer( path, 3, 32, 32, 32 );
		}
		else if( path.contains("n5" ))
		{
			return new N5FSWriter( path);
		}
		return null;
	}

	public static void convertN5Transform( final String pathIn, final String pathOut )
	{
		try
		{
			N5Reader n5in = getN5Reader( pathIn );
			N5Writer n5out = getN5Writer( pathOut );

			if( n5in.datasetExists( "/dfield" ))
				convertN5TransformDataset( n5in, n5out, "/dfield" );

			if( n5in.datasetExists( "/invdfield" ))
				convertN5TransformDataset( n5in, n5out, "/invdfield" );

			int i = 0;
			boolean tryNext = true;
			while( tryNext )
			{
				// continue to next scale level if either of these converstions take place
				String fwdDataset = String.format( "/%d/dfield", i );
				String invDataset = String.format( "/%d/dfield", i );

				tryNext = false;
				if( n5in.datasetExists( fwdDataset ))
				{
					convertN5TransformDataset( n5in, n5out, fwdDataset );
					tryNext = true;
				}

				if( n5in.datasetExists( invDataset ))
				{
					convertN5TransformDataset( n5in, n5out, invDataset );
					tryNext = true;
				}

				i++;
			}
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public static < T extends RealType< T > & NativeType< T > > void convertN5TransformDataset(
			final N5Reader n5in,
			final N5Writer n5out,
			final String dataset )
	{
		try
		{
			DatasetAttributes attrs = n5in.getDatasetAttributes( dataset );
			RandomAccessibleInterval< T > dfield = ( RandomAccessibleInterval< T > )N5Utils.open( n5in, dataset );

			// save dfield
			N5Utils.save( dfield, n5out, dataset, attrs.getBlockSize(), attrs.getCompression() );

			// save other attributes
			double[] affineParams  =  n5in.getAttribute( dataset, N5DisplacementField.AFFINE_ATTR, double[].class );
			if( affineParams != null )
				n5out.setAttribute( dataset, N5DisplacementField.AFFINE_ATTR, affineParams );

			double[] spacingParams  =  n5in.getAttribute( dataset, N5DisplacementField.SPACING_ATTR, double[].class );
			if( spacingParams != null )
				n5out.setAttribute( dataset, N5DisplacementField.SPACING_ATTR, spacingParams );

			Double quanitizationParam = n5in.getAttribute( dataset, N5DisplacementField.MULTIPLIER_ATTR, Double.class );
			if( quanitizationParam != null )
				n5out.setAttribute( dataset, N5DisplacementField.MULTIPLIER_ATTR, quanitizationParam );

		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
	}

	public < T extends RealType< T > & NativeType< T > > void write( 
			final RandomAccessibleInterval< T > dfieldIn, 
			final String outputPath ) throws Exception
	{

		if ( outputPath.contains( "h5" ) || 
			 outputPath.contains( "hdf5" ) ||
			 outputPath.contains( "hdf" ) ||
			 outputPath.contains("n5" ))
		{
			RandomAccessibleInterval<T> dfield = vectorAxisPermute( dfieldIn, 3, 3 );

			String dataset =  N5DisplacementField.FORWARD_ATTR;
			String path = outputPath;
			if( outputPath.contains( ":" ))
			{
				String[] split = outputPath.split( ":" );
				path = split[ 0 ];
				dataset = split[ 1 ];
			}

			try
			{
				//WriteH5DisplacementField.write( dfield, outputPath, new int[] { 3, 32, 32, 32 }, spacing, null );
				N5Writer n5Writer;
				if ( outputPath.contains( "h5" ) || outputPath.contains( "hdf5" ) || outputPath.contains( "hdf" ))
				{
					n5Writer = new N5HDF5Writer( path, 3, 32, 32, 32 );
				}
				else if( outputPath.contains("n5" ))
				{
					n5Writer = new N5FSWriter( path);
				}
				else
				{
					System.err.println("Could not create an n5 writer from path: " + path );
					n5Writer = null; // let the the null pointer be caught
				}

				N5DisplacementField.save(n5Writer, dataset, affine, 
						dfield, spacing, new int[]{ 3, 32, 32, 32 },
						new GzipCompression() );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
		else if ( outputPath.endsWith( "nii" ) )
		{
			File outFile = new File( outputPath );
			Nifti_Writer writer = new Nifti_Writer( true );
			writer.save( dfieldIn, outFile.getParent(), outFile.getName(), spacing );
		}
		else if ( outputPath.endsWith( "nrrd" ) )
		{

			File outFile = new File( outputPath );
			long[] subFactors = new long[] { 1, 1, 1, 1 };

			//RandomAccessibleInterval<T> dfield = vectorAxisThird( dfieldIn );
			RandomAccessibleInterval<T> dfield = vectorAxisPermute( dfieldIn, 3, 0 );
			System.out.println( "dfield out sz: " + Util.printInterval( dfield ) );
			RandomAccessibleInterval< FloatType > raiF = Converters.convert( dfield, new Converter< T, FloatType >()
			{
				@Override
				public void convert( T input, FloatType output )
				{
					output.set( input.getRealFloat() );
				}
			}, new FloatType() );

			RandomAccessibleInterval<T> dfieldForIp = vectorAxisPermute( dfieldIn, 3, 2 );
			ImagePlus ip = ImageJFunctions.wrapFloat( dfieldForIp, "wrapped" );
			ip.getCalibration().pixelWidth = spacing[ 0 ];
			ip.getCalibration().pixelHeight = spacing[ 1 ];
			ip.getCalibration().pixelDepth = spacing[ 2 ];

			String nrrdHeader = WriteNrrdDisplacementField.makeDisplacementFieldHeader( ip, subFactors, "gzip" );
			if ( nrrdHeader == null )
			{
				System.err.println( "Failed" );
				return;
			}

			FileOutputStream out = new FileOutputStream( outFile );
			// First write out the full header
			Writer bw = new BufferedWriter( new OutputStreamWriter( out ) );

			// Blank line terminates header
			bw.write( nrrdHeader + "\n" );
			// Flush rather than close
			bw.flush();

			GZIPOutputStream dataStream = new GZIPOutputStream( new BufferedOutputStream( out ) );
			WriteNrrdDisplacementField.dumpFloatImg( raiF, null, false, dataStream );
		}
		else
		{
			//RandomAccessibleInterval< T > dfield = vectorAxisThird( dfieldIn );
			RandomAccessibleInterval<T> dfield = vectorAxisPermute( dfieldIn, 3, 2 );

			ImagePlus dfieldip = ImageJFunctions.wrapFloat( dfield, "dfield" );
			dfieldip.getCalibration().pixelWidth = spacing[ 0 ];
			dfieldip.getCalibration().pixelHeight = spacing[ 1 ];
			dfieldip.getCalibration().pixelDepth = spacing[ 2 ];

			IJ.save( dfieldip , outputPath );
		}
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public <T extends RealType<T>> DisplacementFieldTransform readAsRealTransform( final String fieldPath )
	{
		try
		{
			RandomAccessibleInterval< FloatType > dfieldImgRaw = read( fieldPath );
			return new DisplacementFieldTransform( dfieldImgRaw, spacing );
	
//			RandomAccessibleInterval< FloatType > dfieldImg = N5DisplacementField.vectorAxisLast( dfieldImgRaw );
//			int nd = 3; // TODO generalize
//
//			RealRandomAccessible[] dfieldComponents = new RealRandomAccessible[ nd ];
//			Scale pixelToPhysical = new Scale( spacing );
//			for( int i = 0; i < nd; i++ )
//			{
//				dfieldComponents[ i ] = 
//						RealViews.affine(
//							Views.interpolate( 
//								Views.extendBorder( Views.hyperSlice( dfieldImg, nd, i )),
//								new NLinearInterpolatorFactory<>()),
//							pixelToPhysical.copy() );
//			}
//			return new DeformationFieldTransform<FloatType>( dfieldComponents );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return null;
		}
	}

	public static <T extends RealType<T>> DisplacementFieldTransform toDeformationField(
			final RandomAccessibleInterval<T> dfieldImg, final AffineGet pixelToPhysical) {

//		int nd = dfieldImg.numDimensions() - 1;
//		@SuppressWarnings("unchecked")
//		RealRandomAccessible<T>[] dfieldComponents = new RealRandomAccessible[ nd ];
//		for (int i = 0; i < nd; i++) {
//			dfieldComponents[i] = RealViews
//					.affine(Views.interpolate(Views.extendBorder(Views.hyperSlice(dfieldImg, nd, i)),
//							new NLinearInterpolatorFactory<>()), pixelToPhysical.copy());
//		}
//		return new DeformationFieldTransform<T>(dfieldComponents);
		
		return new DisplacementFieldTransform( dfieldImg, pixelToPhysical );
	}

	@Deprecated
	public ANTSDeformationField readAsAntsField( final String fieldPath ) throws Exception
	{
		return readAsAntsField( fieldPath, new FloatType() );
	}

	@Deprecated
	@SuppressWarnings("unchecked")
	public <T extends RealType< T > & NativeType< T > > ANTSDeformationField readAsAntsField( final String fieldPath, final T defaultType ) throws Exception
	{
		RandomAccessibleInterval<FloatType> dfieldRAI = null;
		ImagePlus dfieldIp = null;
		double[] spacing = null;
		String unit = null;
		if ( fieldPath.endsWith( "nii" ) )
		{
			try
			{
				dfieldIp = NiftiIo.readNifti( new File( fieldPath ) );
				spacing = new double[] { dfieldIp.getCalibration().pixelWidth, dfieldIp.getCalibration().pixelHeight, dfieldIp.getCalibration().pixelDepth };
				unit = dfieldIp.getCalibration().getUnit();

			}
			catch ( FormatException e )
			{
				e.printStackTrace();
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}

		}
		else if ( fieldPath.endsWith( "nrrd" ) )
		{
			Dfield_Nrrd_Reader reader = new Dfield_Nrrd_Reader();
			File tmp = new File( fieldPath );
			dfieldIp = reader.load( tmp.getParent(), tmp.getName() );

			spacing = new double[]{ 
					dfieldIp.getCalibration().pixelWidth,
					dfieldIp.getCalibration().pixelHeight,
					dfieldIp.getCalibration().pixelDepth };

			unit = dfieldIp.getCalibration().getUnit();

		}
		else if ( fieldPath.contains( "h5" ) )
		{
			String dataset = "dfield";
			String filepath = fieldPath;

			if( fieldPath.contains( ":" ))
			{
				String[] split = fieldPath.split( ":" );
				filepath = split[ 0 ];
				dataset = split[ 1 ];
			}

			try
			{
				N5HDF5Reader n5 = new N5HDF5Reader( filepath, 32, 32, 32, 3 );

				dfieldRAI = N5DisplacementField.openField( n5, dataset, new FloatType() );
				spacing = n5.getAttribute( dataset, N5DisplacementField.SPACING_ATTR, double[].class );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			dfieldIp = IJ.openImage( fieldPath );

			spacing = new double[]{ 
					dfieldIp.getCalibration().pixelWidth,
					dfieldIp.getCalibration().pixelHeight,
					dfieldIp.getCalibration().pixelDepth };

			unit = dfieldIp.getCalibration().getUnit();
		}
		
		if( dfieldIp != null )
		{
			dfieldRAI = ImageJFunctions.wrapFloat( dfieldIp );

		}
		return new ANTSDeformationField( dfieldRAI, spacing, unit );
	}

	public < T extends RealType< T > & NativeType< T > > DisplacementFieldTransform readAsDeformationField( final String fieldPath, final T defaultType ) throws Exception
	{
		return makeDfield( readAsRai( fieldPath, defaultType ), spacing );
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible<? extends RealLocalizable> readAsVectorField( final String fieldPath, final T defaultType ) throws Exception
	{
		return convertToCompositeLast( readAsRai( fieldPath, defaultType ), spacing );
	}

	public < T extends RealType< T > & NativeType< T > > RandomAccessibleInterval<T> readAsRai( final String fieldPath, final T defaultType ) throws Exception
	{
		ImagePlus dfieldIp = null;
		spacing = null;
		String unit = null;
		if ( fieldPath.endsWith( "nii" ) )
		{
			try
			{
				dfieldIp = NiftiIo.readNifti( new File( fieldPath ) );

				spacing = new double[] { dfieldIp.getCalibration().pixelWidth, dfieldIp.getCalibration().pixelHeight, dfieldIp.getCalibration().pixelDepth };
				unit = dfieldIp.getCalibration().getUnit();

			}
			catch ( FormatException e )
			{
				e.printStackTrace();
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}

		}
		else if ( fieldPath.endsWith( "nrrd" ) )
		{
			Dfield_Nrrd_Reader reader = new Dfield_Nrrd_Reader();
			File tmp = new File( fieldPath );
			dfieldIp = reader.load( tmp.getParent(), tmp.getName() );

			spacing = new double[]{ 
					dfieldIp.getCalibration().pixelWidth,
					dfieldIp.getCalibration().pixelHeight,
					dfieldIp.getCalibration().pixelDepth };

			unit = dfieldIp.getCalibration().getUnit();

		}
		else if ( fieldPath.contains( "h5" ) || fieldPath.contains( "hdf5" ) || 
				  fieldPath.contains( "n5" ))
		{
			String dataset = "dfield";
			String filepath = fieldPath;

			if( fieldPath.contains( ":" ))
			{
				String[] split = fieldPath.split( ":" );
				filepath = split[ 0 ];
				dataset = split[ 1 ];
			}

			try
			{
				N5Reader n5;
				if ( filepath.contains( "h5" ) || filepath.contains( "hdf5" ))
				{
					n5 = new N5HDF5Writer( filepath, 3, 32, 32, 32 );
				}
				else if( filepath.contains("n5" ))
				{
					n5 = new N5FSWriter( filepath );
				}
				else
				{
					System.err.println("Could not create an n5 writer from path: " + filepath );
					n5 = null; // let the the null pointer be caught
				}

				dfieldRAI = (RandomAccessibleInterval<T>) N5DisplacementField.openField( n5, dataset, defaultType );
				spacing = n5.getAttribute( dataset, N5DisplacementField.SPACING_ATTR, double[].class );

				if( spacing == null )
				{
					spacing = new double[ dfieldRAI.numDimensions() - 1 ];
					Arrays.fill( spacing, 1.0 );
				}
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			dfieldIp = IJ.openImage( fieldPath );

			spacing = new double[]{ 
					dfieldIp.getCalibration().pixelWidth,
					dfieldIp.getCalibration().pixelHeight,
					dfieldIp.getCalibration().pixelDepth };

			unit = dfieldIp.getCalibration().getUnit();
		}
		
		if( dfieldIp != null )
		{
			dfieldRAI = ImageJFunctions.wrapFloat( dfieldIp );
		}

		final RandomAccessibleInterval< T > fieldPermuted;
		if( dfieldRAI.numDimensions() == 4 )
			fieldPermuted = DfieldIoHelper.vectorAxisPermute( dfieldRAI, 3, 3 );
		else if ( dfieldRAI.numDimensions() == 3 )
			fieldPermuted = DfieldIoHelper.vectorAxisPermute( dfieldRAI, 2, 2 );
		else
			fieldPermuted = null;

		return fieldPermuted;
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends RealType<T>> DisplacementFieldTransform makeDfield( RandomAccessibleInterval<T> rai, double[] spacing )
	{
//		// TODO make give extension and interpolation ptions
//		NLinearInterpolatorFactory<T> interpolator = new NLinearInterpolatorFactory<T>();
//		int nd = rai.numDimensions() - 1;
//
//		final AffineGet pix2Phys;
//		if( nd == 1 )
//			pix2Phys = new Scale( spacing[ 0 ] );
//		else if( nd == 2 )
//			pix2Phys = new Scale2D( spacing );
//		else if( nd == 3 )
//			pix2Phys = new Scale3D( spacing );
//		else
//			return null;
//
//		@SuppressWarnings("rawtypes")
//		RealRandomAccessible[] displacementFields = new RealRandomAccessible[ nd ];
//
//		for( int i = 0; i < nd; i++ )
//		{
//			IntervalView<T> coordDisplacement = Views.hyperSlice( rai, nd, i );
//			RealRandomAccessible< T > dfieldReal = Views.interpolate( Views.extendBorder( coordDisplacement ), interpolator );
//
//			if ( pix2Phys != null )
//				displacementFields[i] = RealViews.affine( dfieldReal, pix2Phys );
//			else
//				displacementFields[i] = dfieldReal;
//		}
//
//		return new DeformationFieldTransform<T>( displacementFields );
		
		return new DisplacementFieldTransform( rai, spacing );
	}

	@SuppressWarnings("unchecked")
	public < T extends RealType< T > > RandomAccessibleInterval< T > read( final String fieldPath ) throws Exception
	{
		ImagePlus dfieldIp = null;
		if ( fieldPath.endsWith( "nii" ) )
		{
			try
			{
				dfieldIp = NiftiIo.readNifti( new File( fieldPath ) );
				spacing = new double[] { dfieldIp.getCalibration().pixelWidth, dfieldIp.getCalibration().pixelHeight, dfieldIp.getCalibration().pixelDepth };

			}
			catch ( FormatException e )
			{
				e.printStackTrace();
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}

		}
		else if ( fieldPath.endsWith( "nrrd" ) )
		{
			Dfield_Nrrd_Reader reader = new Dfield_Nrrd_Reader();
			File tmp = new File( fieldPath );
			dfieldIp = reader.load( tmp.getParent(), tmp.getName() );

			spacing = new double[]{ 
					dfieldIp.getCalibration().pixelWidth,
					dfieldIp.getCalibration().pixelHeight,
					dfieldIp.getCalibration().pixelDepth };

		}
		else if ( 	fieldPath.contains( "h5" ) || 
					fieldPath.contains( "hdf5" ) ||
					fieldPath.contains( "hdf" ) || 
					fieldPath.contains( "n5" ) )
		{
			H5TransformParameters params = TransformReader.H5TransformParameters.parse(fieldPath);
			String dataset = params.inverse ? params.invdataset : params.fwddataset;
			try
			{
				System.out.println("reading: " + params.path + " : " + dataset );
				N5Reader n5;
				if( fieldPath.contains( "n5" ))
					n5 = new N5FSReader( params.path );
				else
					n5 = new N5HDF5Reader( params.path, 32, 32, 32, 3 );

				RandomAccessibleInterval<FloatType> dfield = N5DisplacementField.openField( n5, dataset, new FloatType() );
				affine = N5DisplacementField.openAffine( n5, dataset );
				spacing = n5.getAttribute( dataset, N5DisplacementField.SPACING_ATTR, double[].class );
				return (RandomAccessibleInterval<T>) dfield;
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			dfieldIp = IJ.openImage( fieldPath );
            if( dfieldIp == null )
            {
                System.err.println( "could not read transform from: " + fieldPath );
                return null;
            }
			spacing = new double[]
					{
						dfieldIp.getCalibration().pixelWidth,
						dfieldIp.getCalibration().pixelHeight,
						dfieldIp.getCalibration().pixelDepth
					};
		}
		

		Img< FloatType > tmpImg = ImageJFunctions.wrapFloat( dfieldIp );
		RandomAccessibleInterval<FloatType> img;
		if( tmpImg.numDimensions() == 4 && tmpImg.dimension( 2 ) == 3 )
			img = Views.permute( tmpImg, 2, 3 );
		else
			img = tmpImg;
			
		return (RandomAccessibleInterval<T>) N5DisplacementField.vectorAxisLast( img );
	}

	public static final < T extends RealType< T > > int[] vectorAxisThirdPermutation( RandomAccessibleInterval< T > source ) throws Exception
	{
		final int n = source.numDimensions();
		int[] component = null;
		
		if( n != 4 )
		{
			throw new Exception( "Displacement field must be 4d" );
		}

		if ( source.dimension( 2 ) == 3 )
		{	
			return null;
		}
		else if ( source.dimension( 3 ) == 3 )
		{
			component = new int[ n ];
			component[ 0 ] = 0; 
			component[ 1 ] = 1; 
			component[ 2 ] = 3; 
			component[ 3 ] = 2;

			return component;
		}
		else if ( source.dimension( 0 ) == 3 )
		{
			component = new int[ n ];
			component[ 0 ] = 1;
			component[ 1 ] = 2;
			component[ 2 ] = 0;
			component[ 3 ] = 3;

			return component;
		}

		throw new Exception( 
				String.format( "Displacement fields must store vector components in the first or last dimension. " + 
						"Found a %d-d volume; expect size [%d,...] or [...,%d]", n, ( n - 1 ), ( n - 1 ) ) );
	}

	public static final < T extends RealType< T > > RandomAccessibleInterval< T > vectorAxisThird( RandomAccessibleInterval< T > source ) throws Exception
	{
		int[] component = vectorAxisThirdPermutation( source );
		if( component != null )
			return N5DisplacementField.permute( source, component );

		throw new Exception( "Some problem permuting" );
	}

	/**
	 * Permutes the dimensions of the input {@link RandomAccessibleInterval} so that 
	 * the first dimension of length dimLength is in dimension destinationDim in the output image.
	 * Other dimensions are "shifted" so that the order of the remaining dimensions is preserved.
	 * 
	 * @param source
	 * @param dimLength
	 * @param destinationDim
	 * @return the permutaion indexes
	 * @throws Exception
	 */
	public static final < T extends RealType< T > > int[] vectorAxisPermutation(
			final RandomAccessibleInterval< T > source,
			final int dimLength,
			final int destinationDim ) throws Exception
	{
		// the dimension of the vector field
		final int n = source.numDimensions(); 

		int currentVectorDim = -1;
		for( int i = 0; i < n; i++ )
		{
			if( source.dimension( i ) == dimLength )
				currentVectorDim = i;
		}

		if( currentVectorDim == destinationDim )
			return null;

		if( currentVectorDim < 0 )
			throw new Exception( 
					String.format( "Displacement fields must contain a dimension with a length of %d", dimLength ));

		int j = 0;

		int[] component = new int[ n ];
		component[ currentVectorDim ] = destinationDim;

		if( j == currentVectorDim )
			j++;

		for( int i = 0; i < n; i++ )
		{
			if( i != destinationDim )
			{
				component[ j ] = i;
				j++;

				if( j == currentVectorDim )
					j++;
			}
		}

		return component;
	}

	/**
	 * Permutes the dimensions of the input {@link RandomAccessibleInterval} so that 
	 * the first dimension of length dimLength is in dimension destinationDim in the output image.
	 * Other dimensions are "shifted" so that the order of the remaining dimensions is preserved.
	 * 
	 * @param source
	 * @param dimLength
	 * @param destinationDim
	 * @return the permuted image
	 * @throws Exception
	 */
	public static final < T extends RealType< T > > RandomAccessibleInterval< T > vectorAxisPermute( 
			final RandomAccessibleInterval< T > source,
			final int dimLength,
			final int destinationDim ) throws Exception
	{
		// the dimension of the vector field
		final int n = source.numDimensions(); 

		int currentVectorDim = -1;
		for( int i = 0; i < n; i++ )
		{
			if( source.dimension( i ) == dimLength )
				currentVectorDim = i;
		}
	
		if( currentVectorDim == destinationDim )
			return source;

		if( currentVectorDim < 0 )
			throw new Exception( 
					String.format( "Displacement fields must contain a dimension with a length of %d", dimLength ));

		int j = 0;

		int[] component = new int[ n ];
		component[ currentVectorDim ] = destinationDim;

		if( j == currentVectorDim )
			j++;

		for( int i = 0; i < n; i++ )
		{
			if( i != destinationDim )
			{
				component[ j ] = i;
				j++;

				if( j == currentVectorDim )
					j++;
			}
		}

		return N5DisplacementField.permute( source, component );
	}
	
	public static Interval dfieldIntervalVectorFirst3d( Interval dfieldInterval )
	{
		if( dfieldInterval.dimension( 0 ) == 3 )
		{
			return dfieldInterval;
		}
		else if( dfieldInterval.dimension( 3 ) == 3 )
		{
			FinalInterval interval = new FinalInterval(
					dfieldInterval.dimension( 3 ),
					dfieldInterval.dimension( 0 ),
					dfieldInterval.dimension( 1 ),
					dfieldInterval.dimension( 2 ));
			return interval;
		}
		else 
			return null;
	}

	public static < T extends RealType< T > > RealRandomAccessible< ? extends RealLocalizable > convertToCompositeFirst( final RandomAccessibleInterval< T > position )
	{
		return convertToCompositeLast( Views.moveAxis( position, 0, position.numDimensions() - 1 ) );
	}

	public static < T extends RealType< T > > RealRandomAccessible< ? extends RealLocalizable > convertToCompositeLast( final RandomAccessibleInterval< T > position )
	{
		return Views.interpolate( Views.extendBorder( Views.collapseReal( position ) ), new NLinearInterpolatorFactory<>() );
	}

	public static < T extends RealType< T > > RealRandomAccessible< ? extends RealLocalizable > convertToCompositeLast( final RandomAccessibleInterval< T > position, final double[] spacing )
	{
		return RealViews.affine( convertToCompositeLast( position ), spacing.length == 2 ? new Scale2D( spacing ) : spacing.length == 3 ? new Scale3D( spacing ) : new Scale( spacing ) );
	}
}
