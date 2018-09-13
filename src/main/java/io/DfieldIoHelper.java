package io;

import java.io.File;
import java.io.IOException;
import java.util.Map;


import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
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
//				switch (datasetAttributes.getDataType()) {
//				case UINT8:
//					RandomAccessibleInterval<UnsignedByteType> dfield_tmp = N5Utils.open( reader, "dfield" );
//				    dfield_h5 = convert( dfield_tmp, mult );	
//					break;
//				case INT8:
//					RandomAccessibleInterval<ByteType> dfield_tmp = N5Utils.open( reader, "dfield" );
//				    dfield_h5 = convert( dfield_tmp, mult );	
//					break;
//				case UINT16:
//					RandomAccessibleInterval<UnsignedShortType> dfield_tmp = N5Utils.open( reader, "dfield" );
//				    dfield_h5 = convert( dfield_tmp, mult );	
//					break;
//				case INT16:
//					RandomAccessibleInterval<ShortType> dfield_tmp = N5Utils.open( reader, "dfield" );
//				    dfield_h5 = convert( dfield_tmp, mult );	
//					break;
//				case UINT32:
//					RandomAccessibleInterval<UnsignedIntType> dfield_tmp = N5Utils.open( reader, "dfield" );
//				    dfield_h5 = convert( dfield_tmp, mult );	
//					break;
//				case INT32:
//					RandomAccessibleInterval<IntType> dfield_tmp = N5Utils.open( reader, "dfield" );
//				    dfield_h5 = convert( dfield_tmp, mult );	
//					break;
//				case UINT64:
//					RandomAccessibleInterval<UnsignedLongType> dfield_tmp = N5Utils.open( reader, "dfield" );
//				    dfield_h5 = convert( dfield_tmp, mult );	
//					break;
//				case INT64:
//					RandomAccessibleInterval<LongType> dfield_tmp = N5Utils.open( reader, "dfield" );
//				    dfield_h5 = convert( dfield_tmp, mult );	
//					break;
//				case FLOAT32:
//					dfield_h5 = N5Utils.open( reader, "dfield" );
//					break;
//				case FLOAT64:
//					dfield_h5 = N5Utils.open( reader, "dfield" );
//					break;
//				default:
//					dfield_h5 = null;
//				}
				
				switch (datasetAttributes.getDataType()) {
				case FLOAT32:
					dfield_h5 = N5Utils.open( reader, "dfield" );
					break;
				default:
					RandomAccessibleInterval<UnsignedByteType> dfield_tmp = N5Utils.open( reader, "dfield" );
				    dfield_h5 = convert( dfield_tmp, mult );	
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

	public static <T extends RealType<T>> RandomAccessibleInterval<FloatType> convert(
			final RandomAccessibleInterval<T> dfield, final double m  )
	{
		FloatType s = new FloatType();
		return Converters.convert(
				dfield, 
				new Converter<T, FloatType>()
				{
					@Override
					public void convert(T input, FloatType output)
					{
						output.setReal( input.getRealDouble() * m );
					}
				}, 
				s);
	}
}
