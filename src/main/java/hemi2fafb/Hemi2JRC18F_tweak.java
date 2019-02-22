package hemi2fafb;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import bdv.export.ProgressWriterConsole;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bigwarp.BigWarpInit;
import bigwarp.BigWarp;
import bigwarp.BigWarp.BigWarpData;
import bigwarp.loader.ImagePlusLoader;
//import bigwarp.loader.RaiLoader;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import process.RenderTransformed;
import sc.fiji.io.Dfield_Nrrd_Reader;
import util.RenderUtil;

public class Hemi2JRC18F_tweak
{

//	public static void main( String[] args ) throws Exception
//	{
//
//		String templatePath = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/JRC2018_FEMALE_p8um_iso.nrrd";
//		String hemiPath = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/tbar_render_resliceReverse_hdr_d4.nrrd";
//
////		String[] transformList = new String[]{
////				"/groups/saalfeld/public/jrc2018/transformations/hemiBrain_JRC2018_v1/hemiBrain_JRC2018F_Warp_cmtk.nrrd",
////				"/groups/saalfeld/public/jrc2018/transformations/hemiBrain_JRC2018_v1/hemiBrain_JRC2018F_Affine_cmtk"
////		};
//
//		String[] transformList = new String[]{
//				"inverse /groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/antsA_0Affine.mat",
//				"/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/antsA_1Warp.nii"
//		};
//		
//		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
//		for ( String xfmPath : transformList )
//		{
//			boolean inv = false;
//			if( xfmPath.startsWith( "inverse " ))
//			{
//				inv = true;
//				xfmPath = xfmPath.replace( "inverse ", "" );
//			}
//			totalXfm.add( RenderTransformed.loadTransform( xfmPath, inv ));
//		}
//
//		RealImgAndInterval< FloatType > hemi = loadNrrd( hemiPath, null );
//		RealImgAndInterval< FloatType > jrc18 = loadNrrd( templatePath, totalXfm );
//	
////		BdvOptions opts = BdvOptions.options();
////		BdvStackSource<FloatType> bdv = BdvFunctions.show( hemi.rra, hemi.itvl, "hemi brain", opts );
////		opts.addTo( bdv );
////		bdv.setDisplayRange( 0, 2000 );
////		
////		BdvStackSource< FloatType > bdv2 = BdvFunctions.show( jrc18.rra, jrc18.itvl, "jrc2018", opts );
////		//bdv2.getSources();
////		
////		RandomAccessibleInterval< FloatType > raiHemi = bdv.getSources().get( 0 ).getSpimSource().getSource( 0, 0 );
////		RandomAccessibleInterval< FloatType > raiJ18F = bdv.getSources().get( 1 ).getSpimSource().getSource( 0, 0 );
//
//		List< RandomAccessibleInterval > movingRaiList = new ArrayList<RandomAccessibleInterval>();
//		List< RandomAccessibleInterval > targetRaiList = new ArrayList<RandomAccessibleInterval>();
//		
////		movingRaiList.add( raiJ18F );
////		targetRaiList.add( raiHemi );
//
//		movingRaiList.add( jrc18.get() );
//		targetRaiList.add( hemi.get() );
//
//		BigWarpData data = herecreateBigWarpData( movingRaiList, targetRaiList, new String[]{"jrc2018", "hemi brain"} );
//		//BigWarpInit.createBigWarpData( movingRaiList, targetRaiList )
//		
//		BigWarp bw = new BigWarp( data, "bigwarp", new ProgressWriterConsole());
//	
//
//	}
//	
//	public static BigWarpData herecreateBigWarpData(
//			final List<RandomAccessibleInterval> movingRaiList,
//			final List<RandomAccessibleInterval> targetRaiList,
//			String[] names )
//	{
//		int numMovingSources = movingRaiList.size();
//		int numTargetSources = targetRaiList.size();
//		return BigWarpInit.createBigWarpData( 
//				new RaiLoader( movingRaiList, ImagePlusLoader.range( 0, numMovingSources )),
//				new RaiLoader( targetRaiList, ImagePlusLoader.range( numMovingSources, numTargetSources )),
//				names );
//	}
//	
//	public static RealImgAndInterval< FloatType > loadNrrd( String path, InvertibleRealTransform xfm )
//	{
//		Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
//		File hFile = new File( path );
//		ImagePlus ip = nr.load( hFile.getParent(), hFile.getName());
//
//		
//		double rx = ip.getCalibration().pixelWidth;
//		double ry = ip.getCalibration().pixelHeight;
//		double rz = ip.getCalibration().pixelDepth;
//		
//		AffineTransform3D resInXfm = new AffineTransform3D();
//		resInXfm.set( 	rx, 0.0, 0.0, 0.0, 
//				  		0.0, ry, 0.0, 0.0, 
//				  		0.0, 0.0, rz, 0.0 );
//
//
//		Img< FloatType > a = ImageJFunctions.wrapFloat( ip );
//		RealRandomAccessible< FloatType > rra = null;
//		if( xfm == null )
//		{
//			rra = RealViews.affine( 
//					Views.interpolate( 
//						Views.extendZero( a ), 
//						new NLinearInterpolatorFactory<FloatType>() ),
//					resInXfm );
//		}
//		else
//		{
//			InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
//			totalXfm.add( resInXfm );
//			totalXfm.add( xfm );
//
//			rra = RealViews.transform( 
//					Views.interpolate( 
//						Views.extendZero( a ), 
//						new NLinearInterpolatorFactory<FloatType>() ),
//					totalXfm );
//		}
//		
//		FinalInterval interval = RenderUtil.transformInterval( resInXfm, a );
//		return new RealImgAndInterval<FloatType>( interval, rra );
//	}
//	
//	public static class RealImgAndInterval<T>
//	{
//		public final Interval itvl;
//		public final RealRandomAccessible<T> rra;
//		
//		public RealImgAndInterval(
//			final Interval itvl,
//			final RealRandomAccessible<T> rra )
//		{
//			this.rra = rra;
//			this.itvl = itvl;
//		}
//		
//		public RandomAccessibleInterval<T> get()
//		{
//			return Views.interval( Views.raster( rra ), itvl );
//		}
//	}
//
}
