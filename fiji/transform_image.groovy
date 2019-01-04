#@ImagePlus(label="Image") ip
#@ImagePlus(label="Reference Image") ref
#@String(label="Transformation file path list") xfmPathList
#@String(label="Interpolation:", choices={"LINEAR", "NEAREST"}, style="radioButtonHorizontal") interpType
#@Integer(label="Number of threads", value=4, min=1, max=128) nThreads


import process.RenderTransformed;
import loci.formats.FormatException;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import sc.fiji.io.Dfield_Nrrd_Reader;
import util.RenderUtil;
import ij.ImagePlus;

/*
 * Why use an ImagePlus and not a Dataset?
 * Because Dataset's don't (currently) have metadata - specifically resolution
 */


nd = 3; // only 3d for now

rx = ip.getCalibration().pixelWidth;
ry = ip.getCalibration().pixelHeight;
rz = ip.getCalibration().pixelDepth;

AffineTransform3D resInXfm = null;
if( rx == 0 ){
	rx = 1.0;
	System.err.println( "WARNING: rx = 0 setting to 1.0" );
}
if( ry == 0 ){
	ry = 1.0;
	System.err.println( "WARNING: ry = 0 setting to 1.0" );
}
if( rz == 0 ){
	rz = 1.0;
	System.err.println( "WARNING: rz = 0 setting to 1.0" );
}

if( rx != 1.0  || ry != 1.0 || rz != 1.0 )
{
	resInXfm = new AffineTransform3D();
	resInXfm.set( 	rx, 0.0, 0.0, 0.0, 
			  		0.0, ry, 0.0, 0.0, 
			  		0.0, 0.0, rz, 0.0 );
	System.out.println( "transform for input resolutions : " + resInXfm );
}

renderInterval = new FinalInterval(
				[ ip.getWidth(), ip.getHeight(), ip.getNSlices() ] as long[] );

outputResolutions = [
			ip.getCalibration().pixelWidth, 
			ip.getCalibration().pixelHeight,
			ip.getCalibration().pixelDepth] as double[]




InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
boolean invert = false;
for( p in (xfmPathList.split( "\\s+" )) )
{
	if( p.equals( "-i" ))
	{
		invert = true;
	}
	else
	{
		//println( p )
		//println( invert )
		totalXfm.add( RenderTransformed.loadTransform( p, invert ) );
		invert = false;
	}
}

ImagePlus ipout = null;
if( ip.getBitDepth() == 8 )
{
	ImagePlusImg< UnsignedByteType, ? > out = ImagePlusImgs.unsignedBytes(  
			renderInterval.dimension( 0 ),
			renderInterval.dimension( 1 ),
			renderInterval.dimension( 2 ));

	interp = RenderTransformed.getInterpolator( interpType, out );
	ipout = RenderTransformed.run( ImageJFunctions.wrapByte( ip ), out, resInXfm, renderInterval, totalXfm, outputResolutions, interp, nThreads );
}
else if( ip.getBitDepth() == 16 )
{
	ImagePlusImg< UnsignedShortType, ? > out = ImagePlusImgs.unsignedShorts( 
			renderInterval.dimension( 0 ),
			renderInterval.dimension( 1 ),
			renderInterval.dimension( 2 ));


	interp = RenderTransformed.getInterpolator( interpType, out );
	ipout = RenderTransformed.run( ImageJFunctions.wrapShort( ip ), out, resInXfm, renderInterval, totalXfm, outputResolutions, interp, nThreads );
}
else if( ip.getBitDepth() == 32 )
{
	ImagePlusImg< FloatType, ? > out = ImagePlusImgs.floats( 
			renderInterval.dimension( 0 ),
			renderInterval.dimension( 1 ),
			renderInterval.dimension( 2 ));

	interp = RenderTransformed.getInterpolator( interpType, out );
	ipout = RenderTransformed.run( ImageJFunctions.wrapFloat( ip ), out, resInXfm, renderInterval, totalXfm, outputResolutions, interp, nThreads );
}
else{
	return;
}

ipout.show()
