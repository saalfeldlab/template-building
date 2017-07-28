package process;

import ij.IJ;
import net.imglib2.algorithm.stats.MutualInformation;
import net.imglib2.converter.Converter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.real.FloatType;

public class RunNMI
{

	public static void main( String[] args )
	{
		Img< FloatType > im1 = ImageJFunctions.convertFloat( IJ.openImage( args[ 0 ] ));
		Img< FloatType > im2 = ImageJFunctions.convertFloat( IJ.openImage( args[ 1 ] ));
		float histmin = Float.parseFloat( args[ 2 ] );
		float histmax = Float.parseFloat( args[ 3 ] );
		int numBins = Integer.parseInt( args[ 4 ] );
		
		//TODO mask the images before passing it to nmi. 
		
		System.out.println( MutualInformation.nmi( im1, im2, histmin, histmax, numBins ));
	}
}
