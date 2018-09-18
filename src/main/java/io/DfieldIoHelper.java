package io;

import java.io.File;
import java.io.IOException;

import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.quantization.AbstractQuantizer;
import net.imglib2.quantization.GammaQuantizer;
import net.imglib2.quantization.LinearQuantizer;

import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import sc.fiji.io.Nrrd_Reader;

public class DfieldIoHelper
{
	
	public static final String MULT_KEY = "multiplier";
	
	public double[] spacing;

	public < T extends RealType<T>> RandomAccessibleInterval< FloatType > read( final String fieldPath )
	{
		ImagePlus dfieldIp = null;
		if( fieldPath.endsWith( "nii" ))
		{
			try
			{
				System.out.println("loading nii");
				dfieldIp =  NiftiIo.readNifti( new File( fieldPath ) );
				
				spacing = new double[]{ 
						dfieldIp.getCalibration().pixelWidth,
						dfieldIp.getCalibration().pixelHeight,
						dfieldIp.getCalibration().pixelDepth };
				
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}

		}
		else if( fieldPath.endsWith( "nrrd" ))
		{
			// This will never work since the Nrrd_Reader can't handle 4d volumes, actually
			Nrrd_Reader nr = new Nrrd_Reader();
			File imFile = new File( fieldPath );
			dfieldIp = nr.load( imFile.getParent(), imFile.getName());
			
			spacing = new double[]{ 
					dfieldIp.getCalibration().pixelWidth,
					dfieldIp.getCalibration().pixelHeight,
					dfieldIp.getCalibration().pixelDepth };

		}
		else if( fieldPath.endsWith( "h5" ))
		{
			try
			{
				N5HDF5Reader reader = new N5HDF5Reader( fieldPath, 32, 32, 32, 3 );
				RandomAccessibleInterval<FloatType> dfield_h5;
				
				Double mult = reader.getAttribute("dfield","multiplier", Double.TYPE);
				System.out.println( "mult "  + mult );
				
				spacing = reader.getAttribute( "dfield","spacing", double[].class );
				
				DatasetAttributes datasetAttributes = reader.getDatasetAttributes( "dfield" );

				
				switch (datasetAttributes.getDataType()) {
				case INT8:
					RandomAccessibleInterval<ByteType> dfield_b = N5Utils.open( reader, "dfield" );
				    dfield_h5 = Converters.convert( dfield_b, getQuantizer( reader, new ByteType() ).inverse(), new FloatType());
					break;
				case INT16:
					RandomAccessibleInterval<ShortType> dfield_s = N5Utils.open( reader, "dfield" );
					dfield_h5 = Converters.convert( dfield_s, getQuantizer( reader, new ShortType() ).inverse(), new FloatType());
					break;
				default:
					dfield_h5 = N5Utils.open( reader, "dfield" );
					break;	
				}

//				return dfield_h5;
				return Views.permute( Views.permute( Views.permute(dfield_h5, 0, 1 ), 1, 2 ), 2, 3 );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			dfieldIp = IJ.openImage( fieldPath );
		}
		
		return ImageJFunctions.wrapFloat( dfieldIp );
	}

	public static <S extends RealType<S>> AbstractQuantizer<FloatType,S> getQuantizer( 
			N5HDF5Reader reader, S s ) throws IOException
	{
		
		Double gamma = reader.getAttribute( "dfield","gamma", Double.TYPE );

		
		if( gamma != null )
		{
			Double maxIn = reader.getAttribute( "dfield","b", Double.TYPE );
			Double maxOut = reader.getAttribute( "dfield","a", Double.TYPE );
			return new GammaQuantizer<FloatType,S>( new FloatType(), s, maxOut, maxIn, gamma );
		}
		else
		{
			Double mult = reader.getAttribute( "dfield","multiplier", Double.TYPE );
			if ( mult == null )
				mult = 1/reader.getAttribute( "dfield","m", Double.TYPE );

			return new LinearQuantizer<FloatType,S>( new FloatType(), s, mult, 0 );
		}
	}
	

}
