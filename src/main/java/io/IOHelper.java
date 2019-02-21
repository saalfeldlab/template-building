package io;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import loci.formats.FormatException;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import process.RenderTransformed;
import sc.fiji.io.Dfield_Nrrd_Reader;
import sc.fiji.io.Dfield_Nrrd_Writer;

@Command( version = "0.0.2-SNAPSHOT" )
public class IOHelper implements Callable<Void>
{

	ImagePlus ip;
	RandomAccessibleInterval< ? > rai;
	double[] resolution;

	@Option(names = {"--input", "-i"}, description = "Input image file" )
	private String inputFilePath;

	@Option(names = {"--output", "-o"}, description = "Output image file" )
	private String outputFilePath;
	
	@Option(names = {"--resolution", "-r"}, description = "Force output resolution", split=",")
	private double[] resIn;

	@Option(names = {"--unit", "-u"}, description = "Unit" )
	private String unit = null;

	final Logger logger = LoggerFactory.getLogger( IOHelper.class );
	
	public static void main( String[] args )
	{
		CommandLine.call( new IOHelper(), args );
	}

	public Void call()
	{
		// read
		ImagePlus ip = readIp( inputFilePath );
		
		// resolution
		if( resIn != null )
			setResolution( ip, resIn );
	
		// units
		if( unit != null)
			ip.getCalibration().setUnit( unit );

		// write
		IOHelper.write( ip, outputFilePath );

		return null;
	}

	public ValuePair< long[], double[] > readSizeAndResolution( File file )
	{
		try
		{
			return readSizeAndResolution( file.getCanonicalPath() );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		return null;
	}

	public ValuePair< long[], double[] > readSizeAndResolution( String filePath )
	{
		if ( filePath.endsWith( "nii" ) )
		{
			try
			{
				return NiftiIo.readSizeAndResolution( new File( filePath ) );
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
		else if ( filePath.endsWith( "nrrd" ) )
		{
			Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
			try
			{
				return nr.readSizeAndResolution( new File( filePath ) );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		return null;
	}

	public ImagePlus readIp( File file )
	{
		try
		{
			return readIp( file.getCanonicalPath() );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		return null;
	}

	public ImagePlus readIp( String filePathAndDataset )
	{
		if( filePathAndDataset.endsWith( "nii" ))
		{
			try
			{
				ip =  NiftiIo.readNifti( new File( filePathAndDataset ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if( filePathAndDataset.endsWith( "nrrd" ))
		{
			Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
			File imFile = new File( filePathAndDataset );

			ip = nr.load( imFile.getParent(), imFile.getName());
		}
		else if( filePathAndDataset.endsWith( "h5" ))
		{
			ip = toImagePlus( readRai( filePathAndDataset ));
		}
		else
		{
			ip = IJ.openImage( filePathAndDataset );
		}
		
		resolution = new double[ 3 ];
		resolution[ 0 ] = ip.getCalibration().pixelWidth;
		resolution[ 1 ] = ip.getCalibration().pixelHeight;
		resolution[ 2 ] = ip.getCalibration().pixelDepth;

		return ip;
	}
	
	public static void setResolution( ImagePlus ip, double[] res )
	{
		ip.getCalibration().pixelWidth  = res[ 0 ];
		ip.getCalibration().pixelHeight = res[ 1 ];
		ip.getCalibration().pixelDepth  = res[ 2 ];
	}
	
	public <T extends RealType<T> & NativeType<T>> T getType()
	{
		if( rai != null )
			return ( T ) Util.getTypeFromInterval( rai );

		else if( ip != null )
		if( ip.getBitDepth() == 8 )
		{
			return ( T ) new UnsignedByteType();
		}
		else if( ip.getBitDepth() == 16 )
		{
			return ( T ) new UnsignedShortType();
		}
		else if( ip.getBitDepth() == 32 )
		{
			return ( T ) new FloatType();
		}
		return null;
	}
	
	public ImagePlus getIp()
	{
		return ip;
	}

	public <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval< T > getRai()
	{
		return ( RandomAccessibleInterval< T > ) rai;
	}

	//public <T extends RealType<T> & NativeType<T>> ImagePlus toImagePlus( RandomAccessibleInterval<T> img )
	public ImagePlus toImagePlus( RandomAccessibleInterval<?> img )
	{
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		ImagePlus ipout = ImageJFunctions.wrap( (RandomAccessibleInterval<NumericType>) img, "img", null );
		setResolution( ipout, resolution );
		return ipout;
	}

	public <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> readRai( File file )
	{
		try
		{
			return readRai( file.getCanonicalPath() );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * If the file  
	 * The input string should contain both the absolute file path and dataset into the h5 file separated by a colon (":"),
	 * E.g. "/tmp/myfile.h5:mydataset" 
	 * 
	 * @param filePathAndDataset the file and dataset string
	 * @return the image
	 */
	public <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> readRai( String filePathAndDataset )
	{
		if( filePathAndDataset.contains( ".h5?" ) )
		{
			String[] partList = filePathAndDataset.split( "?" );
			String fpath = partList[ 0 ];
			String dset = partList[ 1 ];
			
			logger.debug( "fpath: " + fpath );
			logger.debug( "dset: " + dset );
			
			RandomAccessibleInterval<T> img = null;
			N5HDF5Reader n5;
			try
			{
				n5 = new N5HDF5Reader( fpath, 32, 32, 32 );
				img = N5Utils.open( n5, dset );

				float[] rtmp = n5.getAttribute( dset, "element_size_um", float[].class );
				if( rtmp != null )
				{
					resolution = new double[ 3 ];
					// h5 attributes are usually listed zyx not xyz
					resolution[ 0 ] = rtmp[ 2 ];
					resolution[ 1 ] = rtmp[ 1 ];
					resolution[ 2 ] = rtmp[ 0 ];
				}
				else
					resolution = new double[]{ 1, 1, 1 };
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
			rai = img;
			return img;
		}
		else
		{
			rai = ImageJFunctions.wrap( readIp( filePathAndDataset ) );
			return ( RandomAccessibleInterval< T > ) rai;
		}
	}
	
	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > interpolate( 
			final RandomAccessible<T> img,
			final String interp )
	{
		return Views.interpolate( img, 
				RenderTransformed.getInterpolator( interp, img ));
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > readPhysical( 
			final File file,
			final String interp )
	{
		try
		{
			return readPhysical( file.getCanonicalPath(), interp );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		return null;
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > readPhysical( 
			final String filePathAndDataset,
			final String interp )
	{
		RandomAccessibleInterval< T > rai = readRai( filePathAndDataset );
		RealRandomAccessible< T > realimgpixel = interpolate( Views.extendZero( rai ), interp );
		if( resolution == null )
			return realimgpixel;

		Scale pixelToPhysical = new Scale( resolution );
		return RealViews.affine( realimgpixel, pixelToPhysical );
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > readPhysical( 
			final String filePathAndDataset,
			final InterpolatorFactory< T, RandomAccessible<T> > interp )
	{
		rai = readRai( filePathAndDataset );
		RealRandomAccessible< T > realimgpixel = Views.interpolate( getRai(), interp );
		if( resolution == null )
			return realimgpixel;

		Scale pixelToPhysical = new Scale( resolution );
		return RealViews.affine( realimgpixel, pixelToPhysical );
	}

	public static void write( ImagePlus ip, File outputFile )
	{
		try
		{
			write( ip, outputFile.getCanonicalPath() );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
	}

	public static void write( ImagePlus ip, String outputFilePath )
	{
		if( outputFilePath.endsWith( "nii" ))
		{
			File f = new File( outputFilePath );

			boolean is_displacement = false;
			if( ip.getDimensions()[ 2 ] == 3 )
				is_displacement = true;

			Nifti_Writer writer = new Nifti_Writer( is_displacement );
			writer.save( ip, f.getParent(), f.getName() );
		}
		else if( outputFilePath.endsWith( "nrrd" ))
		{
			File f = new File( outputFilePath );
			Dfield_Nrrd_Writer writer = new Dfield_Nrrd_Writer();
			writer.save( ip, f.getParent(), f.getName() );
		}
		else
		{
			IJ.save( ip, outputFilePath );
		}
			
	}
}
