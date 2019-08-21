package cmtk;

import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import loci.formats.FormatException;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.numeric.real.FloatType;

/**
 * cd
 * /nrs/saalfeld/john/projects/flyChemStainAtlas/cmtk_test/MakeAverageBrain/Registration/warp/start-1_F-A5_01_warp_m0g80c8e1e-1x26r4.list
 * echo 500 350 150 | streamxform -- ./registration yields 495.524119 352.71958
 * 151.566585
 */
public class TestCmtk
{

	public static void main( String[] args ) throws IOException, FormatException
	{
		secondTest();
	}

	public static void secondTest()
	{
		String displacementF = "/nrs/saalfeld/john/projects/flyChemStainAtlas/cmtk_test/MakeAverageBrain/Registration/warp/start-1_F-A5_01_warp_m0g80c8e1e-1x26r4.list/dfield.tif";

		ImagePlus ip = IJ.openImage( displacementF );
		Img< FloatType > displacementField = ImageJFunctions.convertFloat( ip );
		ANTSDeformationField df = new ANTSDeformationField( displacementField,
				new double[]{ 1, 1, 1 }, ip.getCalibration().getUnit() );

		System.out.println( displacementField.numDimensions() );
		System.out.println( displacementField.dimension( 3 ) );

		// double ox = -5.208333333;
		// double oy = -5.46875;
		// double oz = -6.25;
		// double[] pt = new double[]{ 500.0 - ox, 350.0 - oy, 150.0 - oz };
		double[] pt = new double[]
		{ 500.0, 350.0, 150.0 };

		RandomAccess< FloatType > dfra = displacementField.randomAccess();

		dfra.setPosition( new int[]
		{ 500, 350, 150, 0 } );
		System.out.println( dfra.get().getRealDouble() );

		dfra.setPosition( new int[]
		{ 500, 350, 150, 1 } );
		System.out.println( dfra.get().getRealDouble() );

		dfra.setPosition( new int[]
		{ 500, 350, 150, 2 } );
		System.out.println( dfra.get().getRealDouble() );

		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		if ( df != null )
		{
			totalXfm.add( df );
		}

		double[] ptXfm = new double[ 3 ];
		totalXfm.applyInverse( ptXfm, pt );

		System.out.println(
				String.format( "( %f, %f, %f )", ptXfm[ 0 ], ptXfm[ 1 ], ptXfm[ 2 ] ) );
	}

	public static void firstTest()
	{
		String displacementF = "/nrs/saalfeld/john/projects/flyChemStainAtlas/cmtk_test/MakeAverageBrain/Registration/warp/start-1_F-A5_01_warp_m0g80c8e1e-1x26r4.list/defField.tif";
		// double[] pt = new double[]{ 500.0, 350.0, 150.0 };
		/*
		 * yielded: ( 495.568002, 352.465640, 151.696919 )
		 */

		double ox = -5.208333333;
		double oy = -5.46875;
		double oz = -6.25;
		double[] pt = new double[]
		{ 500.0 - ox, 350.0 - oy, 150.0 - oz };
		/*
		 * yielded: ( 502.710026, 356.955505, 158.651995 )
		 */

		ImagePlus ip = IJ.openImage( displacementF );
		Img< FloatType > displacementField = ImageJFunctions.convertFloat( ip );
		ANTSDeformationField df = new ANTSDeformationField( displacementField,
				new double[]{ 1, 1, 1 }, ip.getCalibration().getUnit() );

		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		if ( df != null )
		{
			totalXfm.add( df );
		}

		double[] ptXfm = new double[ 3 ];
		totalXfm.applyInverse( ptXfm, pt );

		System.out.println(
				String.format( "( %f, %f, %f )", ptXfm[ 0 ], ptXfm[ 1 ], ptXfm[ 2 ] ) );
	}

}
