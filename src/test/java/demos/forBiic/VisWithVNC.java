package demos.forBiic;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

/**
 *
 * Visualize current nicest brain and vnc templates for biic conference.
 * 
 * Manually oriented the brain and vnc relative to one another, saved the settings here:
 *	/groups/saalfeld/public/fly-template/joint-brain-vnc-2.xml 
 *
 * TODO - incorporate that transformation into this script
 */
public class VisWithVNC
{

	public static void main( String[] args )
	{
		String brainTemplate = "/groups/saalfeld/public/fly-template/template.tif";
		String vncTemplate = "/nrs/saalfeld/john/projects/flyChemStainAtlas/VNC_take6/all-flip-r1p5-affineCC/hiResRenders/VNC-template.tif";
		
		ImagePlus brainIp = IJ.openImage( brainTemplate );
		ImagePlus vncIp = IJ.openImage( vncTemplate );
		
		Img< FloatType > brainimg = ImageJFunctions.convertFloat( brainIp );
		Img< FloatType > vncimg = ImageJFunctions.convertFloat( vncIp );
		
		FinalInterval unionInterval = Intervals.union( brainimg, vncimg );

//		IntervalView< FloatType > both = Views.permute( 
//				Views.addDimension( 
//						Views.stack( brainimg, vncimg ), 0, 1), 
//				4, 3);
		
		IntervalView< FloatType > brainpad = Views.interval( Views.extendZero( brainimg ), unionInterval );
		IntervalView< FloatType > vncpad = Views.interval( Views.extendZero( vncimg ), unionInterval );

		IntervalView< FloatType > bothpad = Views.permute( 
				Views.addDimension( 
						Views.stack( brainpad, vncpad ), 0, 1), 
				4, 3);
		
		Bdv bdv = BdvFunctions.show( bothpad, "Brain and VNC" );
		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 0 ).setRange( 0, 1000 );
	}

}
