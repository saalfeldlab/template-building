package hemi2fafb;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.bdv.N5ExportMetadata;
import org.janelia.saalfeldlab.n5.bdv.N5ExportMetadataReader;
import org.janelia.saalfeldlab.n5.bdv.N5MultiscaleSource;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.imglib2.RandomAccessibleLoader;

import bdv.img.WarpedSource;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.RealRandomAccessibleIntervalSource;
import bdv.util.RealRandomAccessibleSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.Source;
import bdv.viewer.state.ViewerState;
import ij.ImagePlus;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.GenericIntType;
import net.imglib2.type.numeric.integer.GenericLongType;
import net.imglib2.type.numeric.integer.GenericShortType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import process.RenderTransformed;
import sc.fiji.io.Dfield_Nrrd_Reader;
import util.RenderUtil;


import net.imglib2.type.PrimitiveType;

public class VisHemi2Fafb
{

//	public static final ByteType BYTE = new ByteType();
//	public static final ShortType SHORT = new ShortType();
//	public static final IntType INT = new IntType();
//	public static final LongType LONG = new LongType();
//	public static final FloatType FLOAT = new FloatType();
//	public static final DoubleType DOUBLE = new DoubleType();
	

	public static void main( String[] args ) throws Exception
	{
		final SharedQueue queue = new SharedQueue( 8 );
		/*
		 * setup hemi brain
		 */
		Source< ? > hemiSource = loadHemi( queue );
//		Source< ? > hemiSource = loadHemiToFafb();
		
		/*
		 * setup hemi brain tbars
		 */
//		Source< ? > hemiTbarsSource = loadHemiTbarsLo();
		
		/* 
		 * setup fafb
		 */
		Source< ? > fafb = loadFAFB( queue );
		
		
		/*
		 * setup jrc18
		 */
		Source< FloatType > jrc18 = loadJRC18();
		


		/*
		 * Show 
		 */
		BdvOptions options = BdvOptions.options().numRenderingThreads( 16 );

//		options = options.addTo( BdvFunctions.show( fafb, options ));
//		BdvFunctions.show( hemiSource, options );
//		BdvFunctions.show( jrc18, options );


		// fafb and jrc18
//		BdvOptions options = BdvOptions.options();
//		options = options.addTo( BdvFunctions.show( fafb, options ));
//		WarpedSource< FloatType > jrc18xfm = warpSource( jrc18, loadJRC18_to_FAFB() );
//		BdvFunctions.show( jrc18xfm, options );
		
		// hemi brain and tbars
//		options = options.addTo( BdvFunctions.show( hemiSource ));
//		BdvFunctions.show( hemiTbarsSource, options );

		// warped tbars and jrc18
//		WarpedSource< ? > tbarsWarped = warpSource( hemiTbarsSource, loadFlyem_to_JRC18() );
//		options = options.addTo( BdvFunctions.show( tbarsWarped ));
//		BdvFunctions.show( jrc18, options );


		// hemi brain and jrc18
//		WarpedSource< ? > hemiWarped = warpSource( hemiSource, loadFlyem_to_JRC18() );
//		BdvOptions options = BdvOptions.options();
//		options = options.addTo( BdvFunctions.show( hemiWarped ));
//
//		//WarpedSource< FloatType > jrc18xfm = warpSource( jrc18, loadFlyem_to_JRC18() );
//		BdvFunctions.show( jrc18, options );
		
		
		// warped hemi brain
//		WarpedSource< ? > hemiWarped = warpSource( hemiSource, loadFlyem_to_FAFB_affine(), loadFlyem_to_FAFB_affine() );
////		WarpedSource< ? > hemiWarped = warpSource( hemiSource, loadFlyem_to_FAFB() );
//		BdvStackSource< ? > bdv = BdvFunctions.show( hemiWarped );
//		options = options.addTo( bdv );
////		options = options.addTo( BdvFunctions.show( hemiSource ));
		
		
		


		// warped tbars and fafb
//		WarpedSource< ? > tbarsWarped = warpSource( hemiTbarsSource, loadFlyem_to_FAFB() );
//		WarpedSource< FloatType > jrc18xfm = warpSource( jrc18, loadJRC18_to_FAFB() );
//		options = options.addTo( BdvFunctions.show( tbarsWarped ));
//		BdvFunctions.show( fafb, options );
//		BdvFunctions.show( jrc18xfm, options );
		
		
		// affine hemibrain and fafb
//		options = options.addTo( BdvFunctions.show( hemiSource ));
//		BdvFunctions.show( fafb, options );
		
		

		// warped hemibrain and fafb
		AffineTransform3D id = new AffineTransform3D();
		WarpedSource< ? > hemiWarped = warpSource( hemiSource, loadFlyem_to_FAFB(), id );
		WarpedSource< ? > jrc18Warped = warpSource( jrc18, loadJRC18_to_FAFB(), id );

		BdvStackSource< ? > bdv = BdvFunctions.show( hemiWarped );
		options = options.addTo( bdv ); 

		BdvFunctions.show( fafb, options );
		BdvFunctions.show( jrc18Warped, options );

		
		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 2 ).setRange( 0, 1500 );
		bdv.getBdvHandle().getSetupAssignments().getConverterSetups().get( 2 ).setColor( 
				new ARGBType( ARGBType.rgba( 1, 0, 1, 1 )));
	
		double[] xfmparams = new double[]{ 
				0.8412054094940138, 0.0, 0.0, -435.7652173492281,
				0.0, 0.8412054094940138, 0.0, -234.6835520769816,
				0.0, 0.0, 0.8412054094940138, -116.3932851575259
		};

		AffineTransform3D viewerTransform = new AffineTransform3D();
		viewerTransform.set( xfmparams );
		bdv.getBdvHandle().getViewerPanel().setCurrentViewerTransform( viewerTransform );
	}
	
	//public static <T extends NumericType<T> & NativeType<T>> WarpedSource< T > warpSource( Source<T> s, InvertibleRealTransform xfm )
	public static <T> WarpedSource<T> warpSource( Source<T> s, InvertibleRealTransform xfm, AffineTransform3D forMipmap )
	{
		WarpedSource<T> xfmSrc = new WarpedSource<>( s, s.getName() + " warped");
		xfmSrc.updateTransform( new InverseRealTransform( xfm ) );
		//xfmSrc.updateForMipmap( forMipmap );
		xfmSrc.setIsTransformed( true );
		
		return xfmSrc;
	}

	public static InvertibleRealTransform loadFlyem_to_JRC18() throws Exception
	{
		String warpPath = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/antsA_1InverseWarp.nii";
//		String warpPath = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/antsA_1Warp.nii";
		String affinePath = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/antsA_0Affine.mat";

//		System.out.println("loading warp...");
//		InvertibleRealTransform warp = RenderTransformed.loadTransform( warpPath, false );
//		System.out.println("done.");
		InvertibleRealTransform affine = RenderTransformed.loadTransform( affinePath, false );
	
		InvertibleRealTransformSequence seq = new InvertibleRealTransformSequence();
//		seq.add( warp );
		seq.add( affine );
		

		return seq;
	}

	public static AffineTransform3D loadFlyem_to_FAFB_affine() throws IOException
	{
		String affinePathFlyem = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/antsA_0Affine.mat";
		AffineTransform3D affineFlyem = ANTSLoadAffine.loadAffine( affinePathFlyem );

		String affinePathFafb = "/groups/saalfeld/public/jrc2018/transformations/JRC2018F_FAFB/JRC2018F_FAFB0Affine.mat";
		AffineTransform3D affineFafb = ANTSLoadAffine.loadAffine( affinePathFafb );

		affineFlyem.preConcatenate( affineFafb );

		return affineFlyem;
	}
	
	public static InvertibleRealTransform loadFlyem_to_FAFB() throws Exception
	{

		String affinePathFlyem = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/antsA_0Affine.mat";
		InvertibleRealTransform affineFlyem = RenderTransformed.loadTransform( affinePathFlyem, false );
		
		String warpPathFlyem = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/antsA_1InverseWarp.nii";
		InvertibleRealTransform warpFlyem = RenderTransformed.loadTransform( warpPathFlyem, false );
	
		
		String affinePathFafb = "/groups/saalfeld/public/jrc2018/transformations/JRC2018F_FAFB/JRC2018F_FAFB0Affine.mat";
		InvertibleRealTransform affineFafb = RenderTransformed.loadTransform( affinePathFafb, false );

		String warpPathFafb = "/groups/saalfeld/public/jrc2018/transformations/JRC2018F_FAFB/JRC2018F_FAFB1InverseWarp.nii";
		InvertibleRealTransform warpFafb = RenderTransformed.loadTransform( warpPathFafb, false );
	

		InvertibleRealTransformSequence seq = new InvertibleRealTransformSequence();
		seq.add( warpFlyem );
		seq.add( affineFlyem );
		seq.add( warpFafb );
		seq.add( affineFafb );
	
		return seq;
	}

	public static InvertibleRealTransform loadJRC18_to_FAFB() throws Exception
	{
		String warpPath = "/groups/saalfeld/public/jrc2018/transformations/JRC2018F_FAFB/JRC2018F_FAFB1InverseWarp.nii";
		String affinePath = "/groups/saalfeld/public/jrc2018/transformations/JRC2018F_FAFB/JRC2018F_FAFB0Affine.mat";

		System.out.println("loading warp...");
		InvertibleRealTransform warp = RenderTransformed.loadTransform( warpPath, false );
		System.out.println("done.");
		InvertibleRealTransform affine = RenderTransformed.loadTransform( affinePath, false );
	
		InvertibleRealTransformSequence seq = new InvertibleRealTransformSequence();
		seq.add( warp );
		seq.add( affine );

		return seq;
	}

	public static Source< ? > loadFAFB( final SharedQueue queue ) throws IOException
	{
//		final AffineTransform3D fafb2nm = new AffineTransform3D();
//		fafb2nm.set(
//				4, 0.0, 0.0, 0.0, 
//				0.0, 4, 0.0, 0.0,
//				0.0, 0.0, 40, 0.0 );

		final AffineTransform3D fafb2um = new AffineTransform3D();
		fafb2um.set(
				0.004, 0.0, 0.0, 0.0, 
				0.0, 0.004, 0.0, 0.0,
				0.0, 0.0, 0.04, 0.0 );
		
		N5Reader n5fafb = new N5FSReader( "/nrs/saalfeld/FAFB00/v14_align_tps_20170818_dmg.n5/volumes/raw" );

//		RandomAccessibleIntervalMipmapSource< ? > fafbSource = openMipmaps( 
//				n5fafb, "/volumes/raw", 
//				//new double[]{ 0.004, 0.004, 0.004 },
//				new double[]{ 1, 1, 1 },
//				new UnsignedByteType() );
		
		Source<?> fafbSource = getRandomAccessibleIntervalMipmapSourceV( n5fafb, "fafb", queue );

		TransformedSource< ? > fafbnm = new TransformedSource<>( fafbSource );
//		fafbnm.setIncrementalTransform( fafb2nm );
		fafbnm.setIncrementalTransform( fafb2um );

		return fafbnm;
	}
	
	//public static RraItvlPair<FloatType> loadJRC18()
	public static Source<FloatType> loadJRC18()
	{
		AffineTransform3D jrc18_toMicrons = new AffineTransform3D();
		jrc18_toMicrons.set( 0.6214809, 0, 0 );
		jrc18_toMicrons.set( 0.6214809, 1, 1 );
		jrc18_toMicrons.set( 0.6214809, 2, 2 );
		
		String jrc18FPath = "/groups/saalfeld/public/jrc2018/JRC2018_FEMALE_20x_gen1_iso.nrrd";
		Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
		File imFile = new File( jrc18FPath );
		ImagePlus ip = nr.load( imFile.getParent(), imFile.getName());
		Img< FloatType > jrc18 =  ImageJFunctions.wrapFloat( ip );
		
		AffineRandomAccessible< FloatType, AffineGet > rra = RealViews.affine( 
					Views.interpolate( Views.extendZero(jrc18), 
					new NLinearInterpolatorFactory<>()), 
				jrc18_toMicrons );

		Interval itvl = RenderUtil.transformInterval( jrc18_toMicrons, jrc18 );
//		return new RraItvlPair<>( rra, itvl );
		
		return new RealRandomAccessibleIntervalSource< FloatType >( rra, itvl, new FloatType(), "jrc18" );
	}

	public static Source< ? > loadHemiTbarsLo() throws IOException
	{
		AffineTransform3D hemiTbars_toMicrons = new AffineTransform3D();
		hemiTbars_toMicrons.set( 0.75, 0, 0 );
		hemiTbars_toMicrons.set( 0.75, 1, 1 );
		hemiTbars_toMicrons.set( 0.75, 2, 2 );

		String hemiTbarsPath = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/tbar_render_resliceReverse_hdr_d4.nrrd";
		Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
		File imFile = new File( hemiTbarsPath );
		ImagePlus ip = nr.load( imFile.getParent(), imFile.getName());
		Img< FloatType > hemiTbars =  ImageJFunctions.wrapFloat( ip );
		
		AffineRandomAccessible< FloatType, AffineGet > rra = RealViews.affine( 
					Views.interpolate( Views.extendZero(hemiTbars), 
					new NLinearInterpolatorFactory<>()), 
				hemiTbars_toMicrons );

		Interval itvl = RenderUtil.transformInterval( hemiTbars_toMicrons, hemiTbars );
		
		return new RealRandomAccessibleIntervalSource< FloatType >( rra, itvl, new FloatType(), "jrc18" );

	}

	public static Source< ? > loadHemiToFafb() throws IOException
	{

		//double res = 0.008;
		double res = 0.005859375; // incorrect but what was used for the bridge :(

		AffineTransform3D hemi_toMicrons = new AffineTransform3D();
		hemi_toMicrons.set( res, 0, 0 );
		hemi_toMicrons.set( res, 1, 1 );
		hemi_toMicrons.set( res, 2, 2 );

		double nx = 34427;
		double ny = 39725;
		double nz = 41394;
		AffineTransform3D flip = new AffineTransform3D();
		flip.set(
				0.0, 0.0, -1.0, nx * res,
				0.0, 1.0, 0.0, 0.0,
				-1.0, 0.0, 0.0, ny * res);

		
		String affinePathFlyem = "/groups/saalfeld/public/flyem_hemiBrainAlign/jrc18/antsA/antsA_0Affine.mat";
		AffineTransform3D affineFlyem = ANTSLoadAffine.loadAffine( affinePathFlyem );
	
		String affinePathFafb = "/groups/saalfeld/public/jrc2018/transformations/JRC2018F_FAFB/JRC2018F_FAFB0Affine.mat";
		AffineTransform3D affineFAFB = ANTSLoadAffine.loadAffine( affinePathFafb );

		

		
		N5Reader n5hemi = new N5FSReader( "/nrs/flyem/data/tmp/Z0115-22.export.n5/22-34" );
		Source<?> sourceRaw = getRandomAccessibleIntervalMipmapSource( n5hemi, "hemibrain" );

		AffineTransform3D total = hemi_toMicrons.copy();
		total.preConcatenate( flip );
		total.preConcatenate( affineFlyem );
		total.preConcatenate( affineFAFB );

		TransformedSource< ? > hemiSourceXfm = new TransformedSource<>( sourceRaw );
		hemiSourceXfm.setIncrementalTransform( total );
		
		return hemiSourceXfm;
	}

	public static Source< ? > loadHemi( final SharedQueue queue ) throws IOException
	{

		final AffineTransform3D toFlyEm = new AffineTransform3D();
		toFlyEm.set(
				0.0, 0.0, -1.0, 34427, 
				1.0, 0.0, 0.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );


		//double res = 0.008;
		double res = 0.005859375; // incorrect but what was used for the bridge :(

		AffineTransform3D hemi_toMicrons = new AffineTransform3D();
		hemi_toMicrons.set( res, 0, 0 );
		hemi_toMicrons.set( res, 1, 1 );
		hemi_toMicrons.set( res, 2, 2 );

		double nx = 34427;
		double ny = 39725;
		double nz = 41394;
		AffineTransform3D flip = new AffineTransform3D();
		flip.set(
				0.0, 0.0, -1.0, nx * res,
				0.0, 1.0, 0.0, 0.0,
				-1.0, 0.0, 0.0, ny * res);


//		String flipPath = "/groups/saalfeld/public/flyem_hemiBrainAlign/cmtk_a/toFlyem/toflyem.mat";
//		AffineTransform3D flip = ANTSLoadAffine.loadAffine( flipPath );
//		System.out.println("flipXfm: " + flip );
		
		N5Reader n5hemi = new N5FSReader( "/nrs/flyem/data/tmp/Z0115-22.export.n5/22-34" );
		//Source<?> sourceRaw = getRandomAccessibleIntervalMipmapSource( n5hemi, "hemibrain" );
		Source<?> sourceRaw = getRandomAccessibleIntervalMipmapSourceV( n5hemi, "hemibrain", queue );

		AffineTransform3D total = hemi_toMicrons.copy();
		total.preConcatenate( flip );
		//total.concatenate( flip.inverse() );

		TransformedSource< ? > hemiSourceXfm = new TransformedSource<>( sourceRaw );
		hemiSourceXfm.setIncrementalTransform( total );
		
		return hemiSourceXfm;
	}
	
	public static Source< UnsignedByteType > loadHemiOLD( boolean flyEmSpace ) throws IOException
	{
		final AffineTransform3D toFlyEm = new AffineTransform3D();
		toFlyEm.set(
				0.0, 0.0, -1.0, 34427, 
				1.0, 0.0, 0.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );


		AffineTransform3D hemi_tonm = new AffineTransform3D();
		hemi_tonm.set( 8, 0, 0 );
		hemi_tonm.set( 8, 1, 1 );
		hemi_tonm.set( 8, 2, 2 );

		
		double nx = 34427;
		double ny = 39725;
		double nz = 41394;
		AffineTransform3D flip = new AffineTransform3D();
		flip.set(
				0.0, 0.0, -1.0, nx,
				0.0, 1.0, 0.0, 0.0,
				0.0, 0.0, -1.0, nz );


//		String flipPath = "/groups/saalfeld/public/flyem_hemiBrainAlign/cmtk_a/toFlyem/toflyem.mat";
//		AffineTransform3D flip = ANTSLoadAffine.loadAffine( flipPath );
//		System.out.println("flipXfm: " + flip );
		
		

		/* 
		 * setup hemi brain
		 */
		N5Reader n5hemi = new N5FSReader( "/nrs/flyem/data/tmp/Z0115-22.export.n5" );
		
		return null;


//		RandomAccessibleIntervalMipmapSource< UnsignedByteType > hemiSource = openMipmaps( 
//				n5hemi, "/22-34", 
//				new double[]{ 0.08, 0.08, 0.08 },
//				new UnsignedByteType() );
	
//		if( flyEmSpace )
//		{
//			TransformedSource< UnsignedByteType > hemiSourceFlyem = new TransformedSource<>( hemiSource );
//			hemiSourceFlyem.setIncrementalTransform( toFlyEm );
//			return hemiSourceFlyem;
//		}
//		else
//		{
//			System.out.println( "n5 flipped space");
//			AffineTransform3D total = hemi_toMicrons.copy();
//			//total.preConcatenate( flip );
////			total.concatenate( flip );
//
//			TransformedSource< UnsignedByteType > hemiSourceXfm = new TransformedSource<>( hemiSource );
//			hemiSourceXfm.setIncrementalTransform( total );
//
//			//return hemiSourceXfm;
//			
//			return hemiSource;
//		}
	}

	@SuppressWarnings("unchecked")
	private static RandomAccessibleIntervalMipmapSource< ? > getRandomAccessibleIntervalMipmapSourceV(
			final N5Reader n5,
			final String name,
			final SharedQueue queue ) throws IOException
	{

		//N5ExportMetadataReader metadata = N5ExportMetadata.openForReading( n5 );

		final double[][] scales = getScales( n5 );
		final RandomAccessibleInterval< ? >[] scaleLevelImgs = new RandomAccessibleInterval[ scales.length ];
//		for ( int s = 0; s < scales.length; ++s )
//			scaleLevelImgs[ s ] = N5Utils.openVolatile( n5, getScaleGroupPath( s ) );

		for ( int s = 0; s < scales.length; ++s )
		{
			RandomAccessibleInterval<?> rai = N5Utils.openVolatile( n5, getScaleGroupPath( s ) );
			scaleLevelImgs[ s ] = VolatileViews.wrapAsVolatile( rai, queue, 
					new CacheHints(LoadingStrategy.VOLATILE, 0, true));
			//scaleLevelImgs[ s ] = VolatileViews.wrapAsVolatile( 
		}
		

		final RandomAccessibleIntervalMipmapSource< ? > source = 
				new RandomAccessibleIntervalMipmapSource(
				scaleLevelImgs,
				new VolatileUnsignedByteType(),
				scales,
				new FinalVoxelDimensions("nm", 1, 1, 1 ),
				name );

		return source;
	}
	public static <T extends NativeType<T> & NumericType<T>> RandomAccessibleIntervalMipmapSource< T > openMipmaps( 
			final N5Reader n5, String datasetName, 
			final double[] spacingnm, T t ) throws IOException
	{

		final int numScales = n5.list(datasetName).length;
		final RandomAccessibleInterval<T>[] mipmaps = (RandomAccessibleInterval<T>[])new RandomAccessibleInterval[numScales];
		final double[][] scales = new double[numScales][];

//		final RandomAccessibleIntervalMipmapSource<?> mipmapSource =
//				new RandomAccessibleIntervalMipmapSource<>(
//						mipmaps,
//						new UnsignedByteType(),
//						scales,
//						voxelDimensions,
//						datasetName);

		for (int s = 0; s < numScales; ++s)
		{

			final int scale = 1 << s;
			final double inverseScale = 1.0 / scale;

			final RandomAccessibleInterval<T> source = N5Utils.open(n5, datasetName + "/s" + s);

//			final RandomAccessibleInterval<T> cachedSource = wrapAsVolatileCachedCellImg( 
//					source, new int[]{64, 64, 64});
//			mipmaps[s] = cachedSource;

			mipmaps[s] = source;
			scales[s] = new double[]{scale, scale, scale};
		}

//		FinalVoxelDimensions voxDims = new FinalVoxelDimensions( "nm", 
//				spacingMicrons[0], spacingMicrons[1], spacingMicrons[2]);

		FinalVoxelDimensions voxDims = new FinalVoxelDimensions( "nm", 1, 1, 1);
		
		//FinalVoxelDimensions voxDims = new FinalVoxelDimensions( "pixels", 1, 1, 1 );

		final RandomAccessibleIntervalMipmapSource<T> mipmapSource =
				new RandomAccessibleIntervalMipmapSource<>(
						mipmaps,
						t.copy(),
						scales,
						voxDims,
						datasetName);	
		
		return mipmapSource;
	}
	

	@SuppressWarnings("unchecked")
	private static < T extends NumericType< T > & NativeType< T > > RandomAccessibleIntervalMipmapSource< T > getRandomAccessibleIntervalMipmapSource(
			final N5Reader n5,
			final String name ) throws IOException
	{

		//N5ExportMetadataReader metadata = N5ExportMetadata.openForReading( n5 );

		final double[][] scales = getScales( n5 );
		final RandomAccessibleInterval< T >[] scaleLevelImgs = new RandomAccessibleInterval[ scales.length ];
		for ( int s = 0; s < scales.length; ++s )
			scaleLevelImgs[ s ] = N5Utils.openVolatile( n5, getScaleGroupPath( s ) );


		final RandomAccessibleIntervalMipmapSource< T > source = new RandomAccessibleIntervalMipmapSource<>(
				scaleLevelImgs,
				Util.getTypeFromInterval( scaleLevelImgs[ 0 ] ),
				scales,
				new FinalVoxelDimensions("nm", 1, 1, 1 ),
				//new FinalVoxelDimensions("um", 0.008, 0.008, 0.008 ),
				name );

		return source;
	}

	public static String getScaleGroupPath( final int scale )
	{
		return String.format( "s%d", scale );
	} 

	protected static final String nameKey = "name";
	protected static final String scalesKey = "scales";
	protected static final String downsamplingFactorsKey = "downsamplingFactors";
	protected static final String pixelResolutionKey = "pixelResolution";
	protected static final String affineTransformKey = "affineTransform";

	public static double[][] getScales( N5Reader n5Reader ) throws IOException
	{
		// check the root scales attribute
		// if it is not there, try the downsamplingFactors attribute for every dataset under the channel group
		double[][] scales = n5Reader.getAttribute( "/", scalesKey, double[][].class );

		if ( scales == null )
		{
			final int numScales = n5Reader.list( "" ).length;
			scales = new double[ numScales ][];
			for ( int scale = 0; scale < numScales; ++scale )
			{
				String scaleStringPath = String.format("s%d", scale );
				double[] downsamplingFactors = n5Reader.getAttribute( scaleStringPath, downsamplingFactorsKey, double[].class );
				if ( downsamplingFactors == null )
				{
					if ( scale == 0 )
					{
						downsamplingFactors = new double[ n5Reader.getDatasetAttributes( scaleStringPath ).getNumDimensions() ];
						Arrays.fill( downsamplingFactors, 1 );
					}
					else
					{
						throw new IllegalArgumentException( "downsamplingFactors are not specified for some datasets" );
					}
				}
				scales[ scale ] = downsamplingFactors;
			}
		}

		return scales;
	}
	
	@SuppressWarnings( "unchecked" )
	public static final <T extends NativeType<T>> RandomAccessibleInterval<T> wrapAsVolatileCachedCellImg(
			final RandomAccessibleInterval<T> source,
			final int[] blockSize) throws IOException {

		final long[] dimensions = Intervals.dimensionsAsLongArray(source);
		final CellGrid grid = new CellGrid(dimensions, blockSize);

		final RandomAccessibleLoader<T> loader = new RandomAccessibleLoader<T>(Views.zeroMin(source));

		final T type = Util.getTypeFromInterval(source);
		Set< AccessFlags > volatileSet = AccessFlags.setOf(AccessFlags.VOLATILE);

		final CachedCellImg<T, ?> img;
		final Cache<Long, Cell<?>> cache =
				new SoftRefLoaderCache().withLoader(LoadedCellCacheLoader.get(grid, loader, type, volatileSet));

//		if (GenericByteType.class.isInstance(type)) {
//			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(BYTE, volatileSet ));
//		} else if (GenericShortType.class.isInstance(type)) {
//			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(SHORT, volatileSet));
//		} else if (GenericIntType.class.isInstance(type)) {
//			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(INT, volatileSet));
//		} else if (GenericLongType.class.isInstance(type)) {
//			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(LONG, volatileSet));
//		} else if (FloatType.class.isInstance(type)) {
//			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(FLOAT, volatileSet));
//		} else if (DoubleType.class.isInstance(type)) {
//			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(DOUBLE, volatileSet));
//		} else {
//			img = null;
//		}

		if (GenericByteType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( PrimitiveType.BYTE, volatileSet ));
		} else if (GenericShortType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( PrimitiveType.SHORT, volatileSet));
		} else if (GenericIntType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( PrimitiveType.INT, volatileSet));
		} else if (GenericLongType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( PrimitiveType.LONG, volatileSet));
		} else if (FloatType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( PrimitiveType.FLOAT, volatileSet));
		} else if (DoubleType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( PrimitiveType.DOUBLE, volatileSet));
		} else {
			img = null;
		}

		return img;
	}

	public static class RraItvlPair <T>
	{
		public final RealRandomAccessible< T > rra;
		public final Interval itvl;
		public RraItvlPair( final RealRandomAccessible< T > rra, final Interval itvl )
		{
			this.rra = rra;
			this.itvl = itvl;
		}
	}
}
