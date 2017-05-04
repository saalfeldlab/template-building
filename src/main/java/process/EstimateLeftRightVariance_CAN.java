package process;

import java.io.File;
import java.io.IOException;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import io.AffineImglib2IO;
import io.nii.NiftiIo;
import jitk.spline.XfmUtils;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import transforms.AffineHelper;

public class EstimateLeftRightVariance_CAN
{
	

	
	/**
	 * I used to use the parameters below, but they're wrong
	 */
//	double[] factors = new double[]{ 4, 4, 2 };
//	// upsample factors that make the final volume isotropic:
//	// original data are at [ 0.1882689 x 0.1882689 x 0.38 ]um
//	double[] factors2Iso = new double[]{ 1.0, 1.0, 2.018389654 };
//	double[] rawRes = new double[]{ 0.1882689, 0.1882689, 0.38 };

	/*
	 * These are correct, because the distance we're measuring is in the space
	 * of the original image: thus to convert (i,j,k) pixel coordinates to 
	 * (x,y,z) physical coordinates, we need to use the resolution of the original images,
	 * given by 'rawRes' below
	 */
	double[] factors = new double[]{ 1, 1, 1  };
	double[] factors2Iso = new double[]{ 1.0, 1.0, 1.0 };
	double[] rawRes = new double[]{ 0.1882689, 0.1882689, 0.38 };
	
	
	double[] res;
	
//	AffineTransform3D toIso;
	AffineTransform3D up3d;
	AffineTransform3D down3d;
	AffineTransform3D up3dNoShift;

	public EstimateLeftRightVariance_CAN()
	{
		init();
	}

	public void init()
	{
//		toIso = new AffineTransform3D();
//		toIso.set( factors2Iso[ 0 ], 0, 0 );
//		toIso.set( factors2Iso[ 1 ], 1, 1 );
//		toIso.set( factors2Iso[ 2 ], 2, 2 );
		
		up3d = new AffineTransform3D();
		up3d.set( factors[ 0 ], 0, 0 );
		up3d.set( factors[ 1 ], 1, 1 );
		up3d.set( factors[ 2 ], 2, 2 );
		up3d.set( 4, 0, 3 );
		up3d.set( 4, 1, 3 );
		up3d.set( 2, 2, 3 );
		down3d = up3d.inverse();
		
		up3dNoShift = new AffineTransform3D();
		up3dNoShift.set( factors[ 0 ], 0, 0 );
		up3dNoShift.set( factors[ 1 ], 1, 1 );
		up3dNoShift.set( factors[ 2 ], 2, 2 );
		
		res = new double[ 3 ];
		for( int i = 0; i < 3; i++ )
		{
			res[ i ] = rawRes[ i ]* factors[ i ];
		}
	}

	public InvertibleRealTransformSequence buildTransform(
			String flipPreXfmF,
			String affineF,
			String deformationF ) throws IOException, FormatException
	{
		System.out.println("affine: " + affineF );
		System.out.println("deformation: " + deformationF );
		
		AffineTransform3D affine = ANTSLoadAffine.loadAffine( affineF );
		
//		ANTSDeformationField df = null;
		Img< FloatType > defLowImg = ImageJFunctions.wrap( 
				NiftiIo.readNifti( new File( deformationF ) ) );
		ANTSDeformationField df = new ANTSDeformationField( defLowImg, new double[]{ 1, 1, 1} );
		
		AffineTransform3D flipAffine = null;
		if( flipPreXfmF != null && !flipPreXfmF.isEmpty() )
		{
			System.out.println("have flip: " + flipPreXfmF );
			flipAffine = AffineHelper.to3D( AffineImglib2IO.readXfm( 3, new File( flipPreXfmF ) ));
		}
		return buildTransform( flipAffine, affine, df );
	}
	
	public InvertibleRealTransformSequence buildTransform(
			AffineTransform3D flipPreXfm,
			AffineTransform3D affine,
			ANTSDeformationField df )
	{
		AffineTransform3D totalAffine = null;
		if ( flipPreXfm != null )
		{
			totalAffine = flipPreXfm.inverse().copy();
		} else
		{
			totalAffine = down3d.copy();
		}
		totalAffine.preConcatenate( affine.inverse() );

		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		totalXfm.add( totalAffine );

		if ( df != null )
		{
			totalXfm.add( df );
		}

//		if ( toNormal != null )
//		{
//			totalXfm.add( toNormal );
//		}
//		totalXfm.add( up3dNoShift );
//		totalXfm.add( toIso );

		return totalXfm;
	}
	
	public <T extends RealType<T>> void buildDistanceImage( 
			Img<FloatType> destinationImg,
			Img<T> mask, 
			InvertibleRealTransformSequence totalXfm,
			InvertibleRealTransformSequence totalXfmFlip,
			AffineTransform3D templateFlip )
	{
		
		double[] x = new double[ 3 ];
		double[] xFlip = new double[ 3 ];
		double[] xSrc = new double[ 3 ];
		double[] xSrcFlip = new double[ 3 ];

		Cursor< FloatType > cursor = destinationImg.cursor();
		RandomAccess< T > mra = mask.randomAccess();
		while( cursor.hasNext() )
		{
			cursor.fwd();
			mra.setPosition( cursor );
			
			if( mra.get().getRealDouble() <= 0 )
			{
				continue;
			}
			
			cursor.localize( x );
			templateFlip.applyInverse( xFlip, x );
			
			totalXfm.applyInverse( xSrc, x);
			totalXfmFlip.applyInverse( xSrcFlip, xFlip );
			
			cursor.get().setReal( distance( xSrc, xSrcFlip, res ) );
		}
	}
	
	public static double distance( double[] x, double[] y, double[] res )
	{
		double squaredDist = 0.0;
		for( int i = 0; i < x.length; i++ )
		{
			squaredDist += res[ i ] * ( x[ i ] - y[ i ] ) * ( x[ i ] - y[ i ] );
		}
		return Math.sqrt( squaredDist );
	}

	public static void main( String[] args ) throws IOException, FormatException, ImgLibException
	{
		String outDistImg = args[ 0 ];
		String templateF = args[ 1 ];
		String templateFlipF = args[ 2 ];
		String templateFlipAdjustF = args[ 3 ];

		String affineF = args[ 4 ];
		String warpF = args[ 5 ];
		String unflipToCanF = args[ 6 ];

		String affineFlipF = args[ 7 ];
		String warpFlipF = args[ 8 ];
		String fliptoCanF = args[ 9 ];

		//FinalInterval destInterval = new FinalInterval( 765, 766, 303 );

		// DO THE WORK
		EstimateLeftRightVariance_CAN alg = new EstimateLeftRightVariance_CAN();
		
		System.out.println("build unflipped");
		System.out.println("preXfm : unflipToCanF:  " + unflipToCanF);
		InvertibleRealTransformSequence totalXfm = alg.buildTransform( unflipToCanF, affineF, warpF );
		
		System.out.println("build flipped");
		InvertibleRealTransformSequence totalXfmFlip = alg.buildTransform( fliptoCanF, affineFlipF, warpFlipF ); 

		System.out.println( "totalXfm    : " + totalXfm );
		System.out.println( "totalXfmFlip: " + totalXfmFlip );
		
		AffineTransform3D templateFlip = AffineHelper.to3D( AffineImglib2IO.readXfm( 3, new File( templateFlipF ) ));
		AffineTransform3D templateAdjust = ANTSLoadAffine.loadAffine( templateFlipAdjustF );
		templateFlip.preConcatenate( templateAdjust.inverse() );
		
//		String testF = "/groups/saalfeld/saalfeldlab/FROM_TIER2/fly-light/20160107_31_63X_female/data_1/C1/C1_TileConfiguration_lens.registered.tif";
//		Img< ShortType > testImg = ImageJFunctions.wrap( IJ.openImage( testF ));
		Img< FloatType > baseImg = ImageJFunctions.wrap( IJ.openImage( templateF ));

//		IntervalView< ShortType > xfmImg = 
//				Views.interval( 
//						Views.raster( 
//								RealViews.transform(
//										Views.interpolate( Views.extendZero( testImg ), new NLinearInterpolatorFactory< ShortType >() ),
//								totalXfm )), 
//						baseImg );
//
//		Bdv bdv = BdvFunctions.show( xfmImg, "xfm img" );
//		bdv.getBdvHandle().getSetupAssignments().getConverterSetups().get( 0 ).setDisplayRange( 0, 1000 );

//		double[] x = new double[]{ 158.0, 326.0, 159.0 };
//		double[] xFlip = new double[ 3 ];
//		
//		templateFlip.applyInverse( xFlip, x );
//
//		double[] xSrc = new double[ 3 ];
//		double[] xSrcFlip = new double[ 3 ];
//		totalXfm.applyInverse( xSrc, x);
//		totalXfmFlip.applyInverse( xSrcFlip, xFlip );
//
//		System.out.println( "x       : " + XfmUtils.printArray( x ));
//		System.out.println( "x flip  : " + XfmUtils.printArray( xFlip ));
//		System.out.println( "xSrc    : " + XfmUtils.printArray( xSrc ));
//		System.out.println( "xSrcFlip: " + XfmUtils.printArray( xSrcFlip ));
		
		
		

		System.out.println( "allocating" );
		FloatImagePlus< FloatType > distanceImage = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( baseImg ) );
		System.out.println( "computing" );
		alg.buildDistanceImage( distanceImage, baseImg, totalXfm, totalXfmFlip, templateFlip );
		System.out.println( "writing" );
		IJ.save( distanceImage.getImagePlus(), outDistImg );
	}
	
	
}
