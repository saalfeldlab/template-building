package process;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import ij.IJ;
import io.AffineImglib2IO;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;
import transforms.AffineHelper;
import util.RenderUtil;

public class RenderAffineSequence
{

	public static void main( String[] args ) throws IOException, ImgLibException
	{
		final String outF = args[ 0 ];
		final String inF = args[ 1 ];
		
		String[] xfmFileList = new String[ args.length - 2 ];
		int j = 0;
		for( int i = 2; i < args.length; i++ )
			xfmFileList[ j++ ] = args[ i ];
		
		
		ArrayList< AffineTransform3D > xfmList = loadAffines( xfmFileList );
		AffineTransform3D totalXfm = new AffineTransform3D();
		AffineTransform3D totalXfmInv = new AffineTransform3D();
		for( AffineTransform3D xfm : xfmList )
		{
			totalXfm.concatenate( xfm );
			totalXfmInv.preConcatenate( xfm.inverse());
		}
		
		Img< FloatType > baseImg = ImageJFunctions.wrap( IJ.openImage( inF ));
		FloatImagePlus< FloatType > result = ImagePlusImgs.floats( 
				Intervals.dimensionsAsLongArray( baseImg ) );

		RandomAccessibleOnRealRandomAccessible< FloatType > imgXfm = 
			Views.raster( 
				RealViews.affine(
						Views.interpolate( Views.extendZero( baseImg ), new NLinearInterpolatorFactory<FloatType>() ),
						totalXfmInv ));

		RenderUtil.copyToImageStack( imgXfm, result, 8 );

		System.out.println( "writing" );
		IJ.save( result.getImagePlus(), outF );
	}
	
	
	public static ArrayList<AffineTransform3D> loadAffines( String[] xfmFileList ) throws IOException
	{
		ArrayList< AffineTransform3D > xfmList = new ArrayList< AffineTransform3D >();
		for( String f : xfmFileList )
		{
			if( f.endsWith( "mat" ))
			{
				xfmList.add( ANTSLoadAffine.loadAffine( f ));
			}
			else if( f.endsWith( "txt" ))
			{
				xfmList.add( AffineHelper.to3D( 
						AffineImglib2IO.readXfm( 3, new File( f ))));
			}
			else
			{
				System.err.println( "Error reading: " + f );
				return null;
			}
		}
		return xfmList;
	}

}
