package io;

import java.io.File;
import java.io.IOException;

import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.utility.parse.ParseUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import loci.formats.FormatException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import process.DownsampleGaussian;
import sc.fiji.io.Dfield_Nrrd_Reader;
import sc.fiji.io.Dfield_Nrrd_Writer;

public class IOHelper {

	double[] resolution;

	@Parameter(names = {"--input", "-i"}, description = "Input image file" )
	private String inputFilePath;

	@Parameter(names = {"--output", "-o"}, description = "Output image file" )
	private String outputFilePath;
	
	@Parameter(names = {"--resolution", "-r"}, description = "Force output resolution", 
			converter = ParseUtils.DoubleArrayConverter.class )
	private double[] resIn = null;

	@Parameter(names = {"--unit", "-u"}, description = "Unit" )
	private String unit = null;

	public static void main( String[] args )
	{
		IOHelper io = new IOHelper();

		// parse inputs
		JCommander jCommander = new JCommander( io );
		jCommander.setProgramName( "input parser" ); 
		try 
		{
			jCommander.parse( args );
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}

		// read
		ImagePlus ip = io.readIp( io.inputFilePath );
		
		// resolution
		if( io.resIn != null )
			setResolution( ip, io.resIn );
	
		// units
		if( io.unit != null)
			ip.getCalibration().setUnit( io.unit );

		// write
		IOHelper.write( ip, io.outputFilePath );
	}

	public ImagePlus readIp( String filePathAndDataset )
	{
		ImagePlus baseIp = null;
		if( filePathAndDataset.endsWith( "nii" ))
		{
			try
			{
				baseIp =  NiftiIo.readNifti( new File( filePathAndDataset ) );
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

			baseIp = nr.load( imFile.getParent(), imFile.getName());
			System.out.println( "baseIp");
		}
		else if( filePathAndDataset.endsWith( "h5" ))
		{
			baseIp = toImagePlus( readRai( filePathAndDataset ));
		}
		else
		{
			baseIp = IJ.openImage( filePathAndDataset );
		}

		return baseIp;
	}
	
	public static void setResolution( ImagePlus ip, double[] res )
	{
		ip.getCalibration().pixelWidth  = res[ 0 ];
		ip.getCalibration().pixelHeight = res[ 1 ];
		ip.getCalibration().pixelDepth  = res[ 2 ];
	}

	//public <T extends RealType<T> & NativeType<T>> ImagePlus toImagePlus( RandomAccessibleInterval<T> img )
	public ImagePlus toImagePlus( RandomAccessibleInterval<?> img )
	{
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		ImagePlus ipout = ImageJFunctions.wrap( (RandomAccessibleInterval<NumericType>) img, "img", null );
		setResolution( ipout, resolution );
		return ipout;
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
		if( filePathAndDataset.contains( ".h5:" ) )
		{
			String[] partList = filePathAndDataset.split( ":" );
			String fpath = partList[ 0 ];
			String dset = partList[ 1 ];
			
			System.out.println( "fpath: " + fpath );
			System.out.println( "dset: " + dset );
			
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
			return img;
		}
		else
		{
			return ImageJFunctions.wrap( readIp( filePathAndDataset ) );
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
