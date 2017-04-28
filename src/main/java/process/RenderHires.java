package process;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.janelia.utility.parse.ParseUtils;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import io.AffineImglib2IO;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.img.imageplus.ShortImagePlus;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;
import transforms.AffineHelper;
import util.RenderUtil;

/**
 * Renders images transformed/registered at low resoution 
 * at high resolution.
 * 
 * @author John Bogovic
 *
 */
public class RenderHires
{

	public static void main( String[] args ) throws FormatException, IOException
	{

		String imF = args[ 0 ];
		String downFlipXfmF = args[ 1 ];
		String affineF = args[ 2 ];
		String warpF = args[ 3 ];
		String outputInterval = args[ 4 ];
		String outF = args[ 5 ];

		// TODO expose these two as parameters
		double[] factors = new double[]{ 4, 4, 2 };

		// upsample factors that make the final volume isotropic:
		// original data are at [ 0.1882689 x 0.1882689 x 0.38 ]um
		double[] factors2Iso = new double[]{ 1.0, 1.0, 2.018389654 };

		FinalInterval renderInterval = parseInterval( outputInterval );

		String toNormalF = "";
		AffineTransform3D toNormal = null;
		if ( args.length >= 7 )
		{
			toNormalF = args[ 6 ];
			System.out.println( "tonormal: " + toNormalF );
			toNormal = AffineHelper.to3D( AffineImglib2IO.readXfm( 3, new File( toNormalF ) ) );
		}

		AffineTransform toIso = new AffineTransform( 3 );
		toIso.set( factors2Iso[ 0 ], 0, 0 );
		toIso.set( factors2Iso[ 1 ], 1, 1 );
		toIso.set( factors2Iso[ 2 ], 2, 2 );

		AffineTransform up3dNoShift = new AffineTransform( 3 );
		up3dNoShift.set( factors[ 0 ], 0, 0 );
		up3dNoShift.set( factors[ 1 ], 1, 1 );
		up3dNoShift.set( factors[ 2 ], 2, 2 );

		/*
		 * READ THE TRANSFORM
		 */
		AffineTransform totalAffine = null;
		AffineTransform flipAffine = null;
		if ( !downFlipXfmF.equals( "none" ) )
		{
			System.out.println( "flip affine: " + downFlipXfmF );
			flipAffine = AffineImglib2IO.readXfm( 3, new File( downFlipXfmF ) );
			System.out.println( flipAffine );
		}
		totalAffine = flipAffine.inverse().copy();

		// The affine part
		AffineTransform3D affine = null;
		if ( affineF != null )
		{
			affine = ANTSLoadAffine.loadAffine( affineF );
		}
		totalAffine.preConcatenate( affine.inverse() );

		Img< FloatType > defLowImg = ImageJFunctions.convertFloat( 
				NiftiIo.readNifti( new File( warpF ) ) );
		System.out.println( defLowImg );

		// the deformation
		ANTSDeformationField df = null;
		if ( warpF != null )
		{
			System.out.println( "loading warp - factors 1 1 1" );
			df = new ANTSDeformationField( defLowImg, new double[]
			{ 1, 1, 1 } );
			System.out.println( df.getDefInterval() );
		}

		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		totalXfm.add( totalAffine );

		if ( df != null )
			totalXfm.add( df );

		if( toNormal != null )
		{
			totalXfm.add( toNormal );
		}
		totalXfm.add( up3dNoShift );
		totalXfm.add( toIso );

		// LOAD THE IMAGE
		ImagePlus bip = IJ.openImage( imF );
		Img< ShortType > baseImg = ImageJFunctions.wrap( bip );

		System.out.println("transforming");
		IntervalView< ShortType > imgHiXfm = Views.interval( 
				Views.raster( 
					RealViews.transform(
							Views.interpolate( Views.extendZero( baseImg ), new NLinearInterpolatorFactory<ShortType>() ),
							totalXfm )),
				renderInterval );

//		Bdv bdv = BdvFunctions.show( imgHiXfm, "img hi xfm" );
//////	Bdv bdv = BdvFunctions.show( Views.stack( template, imgHiXfm ), "img hi xfm" );
//		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 0 ).setRange( 0, 1200 );

		System.out.println("allocating");
		ShortImagePlus< ShortType > out = ImagePlusImgs.shorts( 
				renderInterval.dimension( 0 ),
				renderInterval.dimension( 1 ),
				renderInterval.dimension( 2 ));

		IntervalView< ShortType > outTranslated = Views.translate( out,
				renderInterval.min( 0 ),
				renderInterval.min( 1 ),
				renderInterval.min( 2 ));

		System.out.println("copying with 8 threads");
		RenderUtil.copyToImageStack( imgHiXfm, outTranslated, 8 );

		try
		{
			System.out.println("saving to: " + outF );
			IJ.save( out.getImagePlus(), outF );
		}
		catch ( ImgLibException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static FinalInterval parseInterval( String outSz )
	{
		FinalInterval destInterval = null;
		if ( outSz.contains( ":" ) )
		{
			String[] minMax = outSz.split( ":" );
			System.out.println( " " + minMax[ 0 ] );
			System.out.println( " " + minMax[ 1 ] );

			long[] min = ParseUtils.parseLongArray( minMax[ 0 ] );
			long[] max = ParseUtils.parseLongArray( minMax[ 1 ] );
			destInterval = new FinalInterval( min, max );
		} else
		{
			long[] outputSize = ParseUtils.parseLongArray( outSz );
			destInterval = new FinalInterval( outputSize );
		}
		return destInterval;
	}
}
