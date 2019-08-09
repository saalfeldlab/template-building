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

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;
import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.janelia.saalfeldlab.transform.io.TransformReader.H5TransformParameters;

import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import loci.formats.FormatException;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import sc.fiji.io.Dfield_Nrrd_Reader;

public class DfieldIoHelper
{

	public static final String MULT_KEY = "multiplier";

	public double[] spacing;
	
	public static void main( String[] args ) throws Exception
	{
		String dfieldIn = args[ 0 ];
		String dfieldOut = args[ 1 ];
		
		DfieldIoHelper io = new DfieldIoHelper();

		RandomAccessibleInterval< FloatType > dfield = io.read( dfieldIn );


		io.write( dfield, dfieldOut );
	}

	public < T extends RealType< T > & NativeType< T > > void write( 
			final RandomAccessibleInterval< T > dfieldIn, 
			final String outputPath ) throws Exception
	{

		if ( outputPath.contains( "h5" ) )
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

				N5Writer n5Writer = new N5HDF5Writer( path, 3, 32, 32, 32 );
				N5DisplacementField.save(n5Writer, dataset, null, 
						dfield, spacing, new int[]{ 3, 32, 32, 32},
						new GzipCompression() );
			}
			catch ( IOException e )
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

	public ANTSDeformationField readAsDeformationField( final String fieldPath ) throws Exception
	{
		return readAsDeformationField( fieldPath, new FloatType() );
	}

	public < T extends RealType< T > > ANTSDeformationField readAsDeformationField( final String fieldPath, final T defaultType ) throws Exception
	{
		
		RandomAccessibleInterval<FloatType> dfieldRAI = null;
		ImagePlus dfieldIp = null;
		double[] spacing = null;
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
		}
		
		if( dfieldIp != null )
			dfieldRAI = ImageJFunctions.wrapFloat( dfieldIp );

		RandomAccessibleInterval< FloatType > fieldPermuted = DfieldIoHelper.vectorAxisPermute( dfieldRAI, 3, 3 );
		return new ANTSDeformationField( fieldPermuted, spacing );
	}

	public < T extends RealType< T > > RandomAccessibleInterval< FloatType > read( final String fieldPath ) throws Exception
	{
		System.out.println("reading deformation field: " + fieldPath );

		ImagePlus dfieldIp = null;
		if ( fieldPath.endsWith( "nii" ) )
		{
			try
			{
				System.out.println( "loading nii" );
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
		else if ( fieldPath.contains( "h5" ) )
		{
			H5TransformParameters params = TransformReader.H5TransformParameters.parse(fieldPath);
			String dataset = params.inverse ? params.invdataset : params.fwddataset;
			try
			{
				System.out.println("reading: " + params.path + " : " + dataset );
				N5HDF5Reader n5 = new N5HDF5Reader( params.path, 32, 32, 32, 3 );
				RandomAccessibleInterval<FloatType> dfield = N5DisplacementField.openField( n5, dataset, new FloatType() );
				spacing = n5.getAttribute( dataset, N5DisplacementField.SPACING_ATTR, double[].class );
				return dfield;
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			dfieldIp = IJ.openImage( fieldPath );
			spacing = new double[]
					{
						dfieldIp.getCalibration().pixelWidth,
						dfieldIp.getCalibration().pixelHeight,
						dfieldIp.getCalibration().pixelDepth
					};
		}

		Img< FloatType > tmpImg = ImageJFunctions.wrapFloat( dfieldIp );
		return N5DisplacementField.vectorAxisLast( tmpImg );
	}

	public static final < T extends RealType< T > > RandomAccessibleInterval< T > vectorAxisThird( RandomAccessibleInterval< T > source ) throws Exception
	{
		final int n = source.numDimensions();
		int[] component = null;
		
		if( n != 4 )
		{
			throw new Exception( "Displacement field must be 4d" );
		}

		if ( source.dimension( 2 ) == 3 )
		{	
			return source;
		}
		else if ( source.dimension( 3 ) == 3 )
		{
			component = new int[ n ];
			component[ 0 ] = 0; 
			component[ 1 ] = 1; 
			component[ 2 ] = 3; 
			component[ 3 ] = 2;

			return N5DisplacementField.permute( source, component );
		}
		else if ( source.dimension( 0 ) == 3 )
		{
			component = new int[ n ];
			component[ 0 ] = 1;
			component[ 1 ] = 2;
			component[ 2 ] = 0;
			component[ 3 ] = 3;

			return N5DisplacementField.permute( source, component );
		}

		throw new Exception( 
				String.format( "Displacement fields must store vector components in the first or last dimension. " + 
						"Found a %d-d volume; expect size [%d,...] or [...,%d]", n, ( n - 1 ), ( n - 1 ) ) );
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
}
