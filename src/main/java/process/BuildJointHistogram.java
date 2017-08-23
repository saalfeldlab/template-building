package process;

import ij.IJ;
import net.imglib2.algorithm.stats.MutualInformation;
import net.imglib2.converter.Converter;
import net.imglib2.histogram.HistogramNd;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.real.FloatType;

public class BuildJointHistogram
{

	public static void main( String[] args )
	{
		String outF = args[ 0 ];
		Img< FloatType > im1 = ImageJFunctions.convertFloat( IJ.openImage( args[ 1 ] ));
		Img< FloatType > im2 = ImageJFunctions.convertFloat( IJ.openImage( args[ 2 ] ));
		float histmin = Float.parseFloat( args[ 3 ] );
		float histmax = Float.parseFloat( args[ 4 ] );
		int numBins = Integer.parseInt( args[ 5 ] );
		
		//TODO mask the images before passing it to nmi. 
		
		HistogramNd< FloatType > jointHist = MutualInformation.jointHistogram( im1, im2, histmin, histmax, numBins );
		IJ.save( ImageJFunctions.wrap( jointHist, "jointHist" ), outF );
	}
}
