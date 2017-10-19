package process;

import java.io.File;
import java.io.IOException;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import io.AffineImglib2IO;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import loci.formats.FormatException;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import transforms.AffineHelper;
import util.RenderUtil;

public class FlipX
{
	public static void main( String[] args ) throws IOException, FormatException, ImgLibException
	{
		String destdir = args[ 0 ];
		
		// the second argument is an optional center about which to flip
		int offset = 1;
		double xcenter = Double.NaN;
		try 
		{
			//System.out.println( "trying to parse: " + args[ 1 ]);
			double tmp = Double.parseDouble( args[ 1 ] );
			xcenter = tmp;
			offset = 2;
			//System.out.println( "success : " + xcenter );
		}catch(Exception e )
		{
			// do nothing
		}

		String[] subjects = new String[ args.length - 1 ];
		System.arraycopy( args, offset, subjects, 0, subjects.length );
		boolean isNii = false;

		//for ( String f : subjects )
		for( int i = offset; i < args.length; i++ )
		{
			String f = args[ i ];
			String fout = "";
			ImagePlus ip = null;
			if( f.endsWith( "nii" ))
			{
				isNii = true;
				fout = f.replaceAll( ".nii", "-flip.nii" );
				ip = NiftiIo.readNifti( new File( f ));
			}
			else
			{
				fout = f.replaceAll( ".tif", "-flip.tif" );
				ip =  IJ.openImage( f );
			}

			File fin = new File( f );
			String affineFOut = destdir + File.separator + fin.getName() + "-flipXfm.txt";
			System.out.println( f );
			System.out.println( fout );
			System.out.println( affineFOut );

			Img< FloatType > img = ImageJFunctions.wrap( ip );

			double[] center = AffineHelper.center( img );
			if( Double.isNaN( xcenter ))
			{
				xcenter = center[ 0 ];
			}
			System.out.println( "xcenter = " + xcenter );

			// This transform has the effect of flipping the x-axis about the center 'xcenter'
			AffineTransform3D totalXfm = new AffineTransform3D();
			totalXfm.set( -1.0, 0.0, 0.0, 2*xcenter, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0 );

			IntervalView< FloatType > result = Views.interval(
					Views.raster( RealViews.affine(
							Views.interpolate( Views.extendZero( img ),
									new NLinearInterpolatorFactory< FloatType >() ),
							totalXfm ) ),
					img );

			System.out.println( "writing xfm" );
			AffineImglib2IO.writeXfm( new File( affineFOut ), totalXfm );

			FloatImagePlus< FloatType > outputImg = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( img ) );
			RenderUtil.copyToImageStack( result, outputImg, 4 );

			System.out.println( "writing img" );
			if( fout.endsWith( "nii" ))
			{
				Nifti_Writer writer = new Nifti_Writer( false );
				File file_out = new File( fout );

				ImagePlus ipout = outputImg.getImagePlus();
				ipout.setDimensions( 1, (int)img.dimension( 2 ), 1 );
				writer.save( ipout, 
						file_out.getParent(), file_out.getName() );
			}
			else
			{
				IJ.save( outputImg.getImagePlus(), fout );
			}

			System.out.println( " " );
		}

	}

	public static void centerFlipXYInPlace( double[] center )
	{
		double tmp = center[ 0 ];
		center[ 0 ] = center[ 1 ];
		center[ 1 ] = tmp;
	}

	public static double[] centerFlipXY( double[] center )
	{
		double[] out = new double[ center.length ];
		for ( int d = 0; d < center.length; d++ )
		{
			if ( d == 0 )
				out[ d ] = center[ 1 ];
			else if ( d == 1 )
				out[ d ] = center[ 0 ];
			else
				out[ d ] = center[ d ];
		}
		return out;
	}

	public static FinalInterval intervalFlipXY3d( Interval interval )
	{
		return new FinalInterval( 
				interval.dimension( 1 ), 
				interval.dimension( 0 ),
				interval.dimension( 2 ) );
	}

}
