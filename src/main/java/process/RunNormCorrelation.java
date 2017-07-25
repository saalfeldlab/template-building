package process;

import ij.IJ;
import net.imglib2.algorithm.stats.Correlation;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;

public class RunNormCorrelation
{

	public static void main( String[] args )
	{
		Img< FloatType > im1 = ImageJFunctions.convertFloat( IJ.openImage( args[ 0 ] ));
		Img< FloatType > im2 = ImageJFunctions.convertFloat( IJ.openImage( args[ 1 ] ));
		System.out.println( Correlation.correlation( im1, im2 ));
	}

}
