#@ImagePlus(label="Image") ipin
#@String(label="specify downsampling by:", choices={"Factors", "Resolution"}, style="radioButtonHorizontal") type
#@Double(label="factor x", value=1, min=1) fx 
#@Double(label="factor y", value=1, min=1) fy
#@Double(label="factor z", value=1, min=1) fz
#@Double(label="output resolution x", max=0) rx 
#@Double(label="output resolution y", max=0) ry
#@Double(label="output resolution z", max =0) rz
#@String(label="Interpolation:", choices={"LINEAR", "NEAREST"}, style="radioButtonHorizontal") interpType
#@Double(label="source sigma x", value=0.5) ssigx
#@Double(label="source sigma y", value=0.5) ssigy
#@Double(label="source sigma z", value=0.5) ssigz
#@Double(label="target sigma x", value=0.5) tsigx
#@Double(label="target sigma y", value=0.5) tsigy
#@Double(label="target sigma z", value=0.5) tsigz
#@Integer(label="Number of threads", value=4, min=1, max=128) nThreads

import process.DownsampleGaussian;
import net.imglib2.Interval;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.view.Views;

/*
 * Why use an ImagePlus and not a Dataset?
 * Because Dataset's don't (currently) have metadata - specifically resolution
 */

nd = 3; // only 3d for now

epsilon = 1e-6 as double
offset = [ 0, 0, 0 ] as long[]

downsampleFactors = [ fx, fy, fz ] as double[]
resultResolutions = [ rx, ry, rz ] as double[]

resIn = [ 1, 1, 1] as double[]
resIn = [	ipin.getCalibration().pixelWidth,
			ipin.getCalibration().pixelHeight,
			ipin.getCalibration().pixelDepth ] as double[]


if( type.equals( "Resolution" ))
{
	println( "Inferring downsample factors from resolution inputs" );
	downsampleFactors = new double[ nd ];
	for( int d = 0; d < nd; d++ )
	{
		downsampleFactors[ d ] = resultResolutions[ d ] / resIn[ d ];
	}
}
else
{
	resultResolutions = [downsampleFactors, resIn].transpose().collect{ it[0] * it[1] }
	println( resultResolutions )
}


image = ImageJFunctions.wrap( ipin );
outputInterval = DownsampleGaussian.inferOutputIntervalFromFactors( image, downsampleFactors );

interpfactory = DownsampleGaussian.getInterp( interpType, Views.flatIterable( image ).firstElement() );

sourceSigmas = [ ssigx, ssigy, ssigz ] as double[]
targetSigmas = [ tsigx, tsigy, tsigz ] as double[]

factory = new ImagePlusImgFactory( Views.flatIterable( image ).firstElement());
out = factory.create( outputInterval );

DownsampleGaussian.resampleGaussianInplace( 
		image, out, offset,
		interpfactory, 
		downsampleFactors, sourceSigmas, targetSigmas,
		nThreads,
		epsilon );

ipout = out.getImagePlus();
ipout.getCalibration().pixelWidth  = resultResolutions[ 0 ]
ipout.getCalibration().pixelHeight = resultResolutions[ 1 ]
ipout.getCalibration().pixelDepth  = resultResolutions[ 2 ]

ipout.setDimensions( (int)1, (int)outputInterval.dimension( 2 ), (int)1 )

ipout.show()
