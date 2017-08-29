package net.imglib2.histogram;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.imageplus.ByteImagePlus;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ShortImagePlus;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;


public class UniqueLabels
{

	public static void main(String[] args) throws FormatException, IOException
	{

		final String imF = args[ 0 ];

		ImagePlus imp = null;
		if( imF.endsWith( "nii" ))
		{
			imp = NiftiIo.readNifti( new File( imF ));
		}
		else
		{
			imp = IJ.openImage( imF );
		}

		if( imp.getType() == ImagePlus.GRAY8 )
		{
			ByteImagePlus< UnsignedByteType > labels = ImagePlusAdapter.wrapByte( imp );
			System.out.println( "labels: " + BuildCompartmentHistograms.uniqueValues( labels ));

		}
		else if( imp.getType() == ImagePlus.GRAY16 )
		{
			ShortImagePlus< UnsignedShortType > labels = ImagePlusAdapter.wrapShort( imp );
			System.out.println( "labels: " + BuildCompartmentHistograms.uniqueValues( labels ));
		}
		else if( imp.getType() == ImagePlus.GRAY32 )
		{
			FloatImagePlus< FloatType > labels = ImagePlusAdapter.wrapFloat( imp );
			System.out.println( "labels: " + BuildCompartmentHistograms.uniqueValues( labels ));
		}
		else
		{
			System.err.println( "mask must be byte or short image" );
		}
		
	}

}
