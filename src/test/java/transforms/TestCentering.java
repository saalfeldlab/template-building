package transforms;

import java.util.Arrays;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ants.ANTSLoadAffine;

/**
 * Using the transformation stored in:
 * /nrs/saalfeld/john/projects/flyChemStainAtlas/take6_groupwise_template_442/f-flip-r1p5-affineCC/ALLF-F-A1_TileConfiguration_lens_registered_downAffine.txt
 * 
 */
public class TestCentering
{
	public static void main( String[] args )
	{
		double[] params = new double[]{
				1.041419038628723, -0.1888159297751273, -0.02344566014030862, 
				0.17961393898740666, 0.9951829815454668, -0.053777722620340286,
				-0.008089683689001986, 0.0037274162503157975, 0.9713446110716895,
				1.300474112132107, -5.806095627682563, 4.34574702775138 };


		double[] center = new double[]{ 500.47661739997045, 312.43954228699346, 166.94031858583028 };		
//		System.out.println( center );
		
		AffineTransform3D affine = ANTSLoadAffine.loadAffineParams( params, center );
		double[] paramsImglib = new double[ 12 ];
		affine.toArray( paramsImglib );
		System.out.println( Arrays.toString( paramsImglib ));
		
		System.out.println( affine );

		double[] paramsCenter = CenterAffineTransform.centerParams( paramsImglib, center );

		System.out.println( " " );
		System.out.println( Arrays.toString( params ));
		System.out.println( Arrays.toString( paramsCenter ));
	}
}