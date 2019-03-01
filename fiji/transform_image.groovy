#@ImagePlus(label="Image") ip
#@ImagePlus(label="Reference Image") ref
#@String(label="Transformation file path list") xfmPathList
#@String(label="Interpolation:", choices={"LINEAR", "NEAREST"}, style="radioButtonHorizontal") interpType
#@Integer(label="Number of threads", value=4, min=1, max=128) nThreads
#@LogService logger

import io.IOHelper;
import loci.formats.FormatException;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.Scale;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.transform.io.TransformReader;
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

Scale resInXfm = null;
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
	resInXfm = new Scale( [rx, ry, rz] as double[] );
}

renderInterval = new FinalInterval(
				[ ref.getWidth(), ref.getHeight(), ref.getNSlices() ] as long[] );

outputResolutions = [
			ref.getCalibration().pixelWidth, 
			ref.getCalibration().pixelHeight,
			ref.getCalibration().pixelDepth] as double[]

logger.info("build the transform");
RealTransformSequence totalXfm = new RealTransformSequence();

totalXfm.add( new Scale( outputResolutions ));

for( p in ( xfmPathList.split( "\\s+" )))
{
	totalXfm.add( TransformReader.read( p ) );
}

if( resInXfm != null )
	totalXfm.add( resInXfm.inverse() );

logger.info("allocating");
ImagePlusImgFactory factory = null;
if( ip.getBitDepth() == 8 )
{
	factory = new ImagePlusImgFactory( new UnsignedByteType() );
}
else if( ip.getBitDepth() == 16 )
{
	factory = new ImagePlusImgFactory( new UnsignedShortType() );
}
else if( ip.getBitDepth() == 32 )
{
	factory = new ImagePlusImgFactory( new FloatType() );
}
imgout = factory.create( renderInterval );

// interpolate
io = new IOHelper();
realImg = io.interpolate( Views.extendZero( ImageJFunctions.wrap( ip )), interpType );

// transform
imgXfm = Views.interval( 
			Views.raster( new RealTransformRandomAccessible( realImg, totalXfm ) ),
			renderInterval );
try
{
	logger.info("transforming");
	RenderUtil.copyToImageStack( imgXfm, imgout, nThreads );
}
catch ( Exception e )
{
	e.printStackTrace();
	logger.error( "copying failed" );
	return;
}

ipout = imgout.getImagePlus();
ipout.getCalibration().pixelWidth = outputResolutions[ 0 ];
ipout.getCalibration().pixelHeight = outputResolutions[ 1 ];
ipout.getCalibration().pixelDepth = outputResolutions[ 2 ];
ipout.setDimensions( 1, (int) imgout.dimension(2), 1 ) ; // 
if ( io.getIp() != null )
	ipout.getCalibration().setUnit( io.getIp().getCalibration().getUnit() );

ipout.show();
