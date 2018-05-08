package net.imglib2.posField;

import java.io.File;

import org.janelia.utility.parse.ParseUtils;

import ij.IJ;
import ij.ImagePlus;
import io.WritingHelper;
import io.nii.Nifti_Writer;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import process.RenderTransformed;
import util.RenderUtil;

public class GeneratePositionFieldRes
{

	public static void main( String[] args ) throws ImgLibException
	{
		String outArg = args[ 0 ];
		String intervalArg = args[ 1 ];
		String resArg = args[ 2 ];

		int dim = -1;
		if ( args.length >= 3 )
			dim = Integer.parseInt( args[ 3 ]);

		FinalInterval interval = RenderTransformed.parseInterval( intervalArg );

		double[] res = ParseUtils.parseDoubleArray( resArg );
		AffineTransform3D xfm = new AffineTransform3D();
		xfm.set( 	res[ 0 ], 0.0, 0.0, 0.0,
					0.0, res[ 1 ], 0.0, 0.0,
					0.0, 0.0, res[ 2 ], 0.0 );

		PositionRandomAccessible< FloatType, AffineTransform3D > pra = 
				new PositionRandomAccessible< FloatType, AffineTransform3D >(
						interval.numDimensions(), new FloatType(), xfm, dim );

		int nd = interval.numDimensions();
//		long[] min = null;
//		long[] max = null;
		long[] min = new long[ nd ];
		long[] max = new long[ nd ];

		for( int d = 0; d < nd; d++ )
		{
			min[ d ] = interval.min( d );
			max[ d ] = interval.max( d );
		}

		if( dim < 0 )
		{
			min[ nd ] = 0;
			max[ nd ] = nd-1;
		}

		FinalInterval outputInterval = new FinalInterval( min, max );

		// Permute so that slices are correctly interpreted as such 
		// (instead of channels )
		RandomAccessibleInterval< FloatType > raiout = Views.interval( pra, outputInterval );
		System.out.println( raiout );
		System.out.println( Util.printInterval(raiout) );


		FloatImagePlus< FloatType > output = ImagePlusImgs.floats( max );
		RenderUtil.copyToImageStack( raiout, output, 4 );

		ImagePlus imp = output.getImagePlus();
		imp.getCalibration().pixelWidth  = res[ 0 ];
		imp.getCalibration().pixelHeight = res[ 1 ];
		imp.getCalibration().pixelDepth  = res[ 2 ];
		imp.setDimensions( imp.getNSlices(), imp.getNChannels(), imp.getNFrames() );

		System.out.println( "nChannels " + imp.getNChannels() );
		System.out.println( "nFrames " + imp.getNFrames() );
		System.out.println( "nSlices " + imp.getNSlices() );

		WritingHelper.write( imp, outArg );
	}

}
