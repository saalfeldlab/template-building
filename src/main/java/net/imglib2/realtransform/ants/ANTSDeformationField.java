package net.imglib2.realtransform.ants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.AffineTransform4DRepeated3D;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import loci.formats.FormatException;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import jitk.spline.XfmUtils;

public class ANTSDeformationField implements InvertibleRealTransform
{

	private RandomAccessibleInterval<FloatType> def;
	private RealRandomAccessible<FloatType> defField;
//	private RealRandomAccess<FloatType> defFieldAccess;
	
	private double[] resolution;
	
	private int lastDim;
	private int numDimOut;
	private Interval defInterval;
	
	public ANTSDeformationField( ImagePlus ip )
	{
		this( ImageJFunctions.wrap( ip ) ,
			  new double[]{
					  ip.getCalibration().pixelWidth,
					  ip.getCalibration().pixelHeight,
					  ip.getCalibration().pixelDepth });
	}
	
	public ANTSDeformationField( RandomAccessibleInterval<FloatType> def, double[] resolution )
	{
		this.def = def;
		this.resolution = resolution;

		lastDim = def.numDimensions() - 1;
		numDimOut = (int)def.dimension( lastDim );
//		IntervalView< FloatType > def = Views.permute( defRaw, 2, 3 );
		
		System.out.println( "lastDim: " + lastDim );
		System.out.println( "numDimOut: " + numDimOut );
		
		final FloatType NAN = new FloatType( Float.NaN );
		
		defInterval = def;
		if( resolution == null )
		{
//			defField = Views.interpolate( 
//					Views.extendZero( def ), new NLinearInterpolatorFactory< FloatType >());
			
//			System.out.println( "extend NAN ");
//			defField = Views.interpolate( 
//					Views.extendValue( def, NAN ), new NLinearInterpolatorFactory< FloatType >());

			System.out.println( "extend Border ");
			defField = Views.interpolate( 
					Views.extendBorder( def ), new NLinearInterpolatorFactory< FloatType >());
			
//			defFieldAccess = defField.realRandomAccess();
		}
		else
		{
			AffineTransform3D scaleXfm = new AffineTransform3D();
			scaleXfm.set( resolution[ 0 ], 0, 0 );
			scaleXfm.set( resolution[ 1 ], 1, 1 );
			scaleXfm.set( resolution[ 2 ], 2, 2 );

//			defField= RealViews.transform(
//					Views.interpolate( Views.extendZero( def ), new NLinearInterpolatorFactory< FloatType >()),
//					new AffineTransform4DRepeated3D( scaleXfm ));
			
//			System.out.println( "extend NAN ");
//			defField= RealViews.transform(
//					Views.interpolate( Views.extendValue( def, NAN ), new NLinearInterpolatorFactory< FloatType >()),
//					new AffineTransform4DRepeated3D( scaleXfm ));

			System.out.println( "extend Border ");
			defField= RealViews.transform(
					Views.interpolate( Views.extendBorder( def ), new NLinearInterpolatorFactory< FloatType >()),
					new AffineTransform4DRepeated3D( scaleXfm ));
			
		}
	}
	
	public ANTSDeformationField( File f ) throws FormatException, IOException
	{
		this( NiftiIo.readNifti( f ) );
	}

	private ANTSDeformationField( ANTSDeformationField other )
	{
		this.def = other.def;

		this.lastDim = other.lastDim;
		this.numDimOut = other.numDimOut;
		this.resolution = other.resolution;
		
		if( resolution == null )
		{
			defField = Views.interpolate( 
					Views.extendZero( def ), new NLinearInterpolatorFactory< FloatType >());
		}
		else
		{
			AffineTransform3D scaleXfm = new AffineTransform3D();
			scaleXfm.set( resolution[ 0 ], 0, 0 );
			scaleXfm.set( resolution[ 1 ], 1, 1 );
			scaleXfm.set( resolution[ 2 ], 2, 2 );

			defField= RealViews.transform(
					Views.interpolate( Views.extendZero( def ), new NLinearInterpolatorFactory< FloatType >()),
					new AffineTransform4DRepeated3D( scaleXfm ));
		}
	}
	
	public RealRandomAccessible< FloatType > getDefField()
	{
		return defField;
	}
	
	public Interval getDefInterval()
	{
		return defInterval;
	}
	
	public double[] getResolution()
	{
		return resolution;
	}
	
	private void load( File f ) throws FormatException, IOException
	{
		ImagePlus ip = NiftiIo.readNifti( f );
		Img< FloatType > defRaw = ImageJFunctions.wrap( ip );
		System.out.println( defRaw );
		
		Img<FloatType > def = defRaw;
		lastDim = def.numDimensions() - 1;
		numDimOut = (int)def.dimension( lastDim );
//		IntervalView< FloatType > def = Views.permute( defRaw, 2, 3 );
		
		System.out.println( "lastDim: " + lastDim );
		System.out.println( "numDimOut: " + numDimOut );
		
		defInterval = defRaw;
		
		System.out.println( 
				def.dimension( 0 ) + " " +
				def.dimension( 1 ) + " " +
				def.dimension( 2 ) + " " +
				def.dimension( 3 ));
		
		System.out.println( ip.getCalibration().pixelWidth );
		System.out.println( ip.getCalibration().pixelHeight );
		System.out.println( ip.getCalibration().pixelDepth );

		AffineTransform3D scaleXfm = new AffineTransform3D();
		scaleXfm.set( ip.getCalibration().pixelWidth,  0, 0 );
		scaleXfm.set( ip.getCalibration().pixelHeight, 1, 1 );
		scaleXfm.set( ip.getCalibration().pixelDepth,  2, 2 );
		
		defField = RealViews.affine(
				Views.interpolate( Views.extendZero( def ), new NLinearInterpolatorFactory< FloatType >()),
				new AffineTransform4DRepeated3D( scaleXfm ) );

	}

	@Override
	public int numSourceDimensions()
	{
		// TODO Auto-generated method stub
		return def.numDimensions() - 1;
	}

	@Override
	public int numTargetDimensions()
	{
		return numDimOut;
	}

	@Override
	public void apply( double[] source, double[] target )
	{
		applyInverse( target, source );
	}

	@Override
	public void apply( float[] source, float[] target )
	{
		applyInverse( target, source );
	}

	@Override
	public void apply( RealLocalizable source, RealPositionable target )
	{
		applyInverse( target, source );
	}
	
	@Override
	public void applyInverse( double[] source, double[] target )
	{
		RealRandomAccess< FloatType > defFieldAccess = defField.realRandomAccess();
		for( int d = 0; d < target.length; d++ )
			defFieldAccess.setPosition( target[ d ], d);

		defFieldAccess.setPosition( 0.0, lastDim );
		
//		boolean isOutOfBounds = false;
//		for( int d = 0; d < numDimOut; d++ )
//		{
//			defFieldAccess.setPosition( d, lastDim );
//			if( Double.isNaN( defFieldAccess.get().getRealDouble() ))
//			{
//				isOutOfBounds = true;
//				break;
//			}
//		}
//		
//		if( isOutOfBounds )
//		{
//			System.out.println( "nan");
//			for( int d = 0; d < numDimOut; d++ )
//				source[ d ] = 0.0;
//			
//			return;
//		}
		
		System.arraycopy( target, 0, source, 0, source.length );
		for( int d = 0; d < numDimOut; d++ )
		{
//			System.out.println( "def " + d + " - " + defFieldAccess.get() );
			source[ d ] += defFieldAccess.get().getRealDouble();
			defFieldAccess.fwd( lastDim );
		}
//		System.out.println( "target: " + XfmUtils.printArray( target ));
//		System.out.println( "source: " + XfmUtils.printArray( source ));
	}

	@Override
	public void applyInverse( float[] source, float[] target )
	{
		RealRandomAccess< FloatType > defFieldAccess = defField.realRandomAccess();
		for( int d = 0; d < target.length; d++ )
			defFieldAccess.setPosition( target[ d ], d);

		defFieldAccess.setPosition( 0.0, lastDim );
		
//		if( Double.isNaN( defFieldAccess.get().getRealDouble() ))
//		{
//			System.out.println( "nan");
//			for( int d = 0; d < numDimOut; d++ )
//				source[ d ] = 0.0f;
//			
//			return;
//		}
		
		System.arraycopy( target, 0, source, 0, source.length );
		for( int d = 0; d < numDimOut; d++ )
		{
			source[ d ] += defFieldAccess.get().getRealDouble();
			defFieldAccess.fwd( lastDim );
		}
	}

	@Override
	public void applyInverse( RealPositionable source, RealLocalizable target )
	{
		RealRandomAccess< FloatType > defFieldAccess = defField.realRandomAccess();
//		System.out.println("applyInverse rp rl");
		for( int d = 0; d < target.numDimensions(); d++ )
			defFieldAccess.setPosition( target.getDoublePosition( d ), d );

		defFieldAccess.setPosition( 0.0, lastDim );
		
//		defFieldAccess.setPosition( new double[]{
//				target.getDoublePosition( 0 ),
//				target.getDoublePosition( 1 ),
//				target.getDoublePosition( 2 ),
//				0
//		}); 
		
//		if( Double.isNaN( defFieldAccess.get().getRealDouble() ))
//		{
//			System.out.println( "nan");
//			for( int d = 0; d < numDimOut; d++ )
//				source.setPosition( 0.0, d );
//
//			return;
//		}
		
		double[] newpos = new double[ 3 ];
		for( int d = 0; d < numDimOut; d++ )
		{
			newpos[ d ] = target.getDoublePosition( d ) + defFieldAccess.get().getRealDouble();
			source.setPosition( newpos[ d ], d );

			defFieldAccess.fwd( lastDim );
		}
	}

	@Override
	public InvertibleRealTransform inverse()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public InvertibleRealTransform copy()
	{
		return new ANTSDeformationField( this );
	}
	
	public static <T extends RealType<T>> RandomAccessibleInterval< T > doit( 
			RandomAccessibleInterval<T> img,
			double[] resolutions, 
			ANTSDeformationField df )
	{
		RealRandomAccessible< T > ipimgReal = Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory< T >());
		RealRandomAccessible< T > ipimgRealRes;
		AffineTransform3D scaleXfm = null;
		if( resolutions != null)
		{
			System.out.println( "IMG USING RESOLUTION" );
			scaleXfm = new AffineTransform3D();
			scaleXfm.set( resolutions[ 0 ], 0, 0 );
			scaleXfm.set( resolutions[ 1 ], 1, 1 );
			scaleXfm.set( resolutions[ 2 ], 2, 2 );

			ipimgRealRes = RealViews.transform( ipimgReal, scaleXfm );
		}
		else
			ipimgRealRes = ipimgReal;

		RealTransformRandomAccessible< T, InverseRealTransform > ipimgXfmReal = RealViews.transform( ipimgRealRes, df );
		RealTransformRandomAccessible<T, InverseRealTransform> ipimgXfmRealOrig;
		
		ipimgXfmRealOrig = ipimgXfmReal;
		if( resolutions != null )
		{
			ipimgXfmRealOrig = RealViews.transform( ipimgXfmReal, scaleXfm.inverse() );
		}
		else
		{
			ipimgXfmRealOrig = ipimgXfmReal;
		}

		RandomAccessibleInterval< T > ipimgXfm = Views.interval( Views.raster( ipimgXfmRealOrig ), img );
		
//		ipimgXfmRealOrig = ipimgXfmReal;
//		Interval outInterval;
//		if( resolutions != null )
//		{
//			outInterval = largestIntervalFromRealInterval( img, resolutions );
//		}
//		else
//		{
//			outInterval = img;
//		}
//
//		RandomAccessibleInterval< T > ipimgXfm = Views.interval( Views.raster( ipimgXfmRealOrig ), outInterval );

		return ipimgXfm;
	}
	
	public static RandomAccessibleInterval<FloatType> axisAlignedDefField( 
			float mag, int d, final Interval interval )
	{		
		final FloatType ZERO = new FloatType( 0.0f );
		final FloatType  MAG = new FloatType( mag );

		RandomAccessibleInterval< FloatType > zeroRai = ConstantUtils.constantRandomAccessibleInterval( ZERO, 3, interval );
		RandomAccessibleInterval< FloatType > magRai = ConstantUtils.constantRandomAccessibleInterval( MAG, 3, interval );

		ArrayList<RandomAccessibleInterval< FloatType >> imglist = new ArrayList<RandomAccessibleInterval< FloatType >>();
		for( int i = 0; i < 3; i++ )
		{
			if( i == d )
				imglist.add( magRai );
			else
				imglist.add( zeroRai );
		}

		return Views.stack( imglist );
	}
	
	public static Interval largestIntervalFromRealInterval( RealInterval interval, double[] resolutions )
	{
		int nd = interval.numDimensions();
		long[] min = new long[ nd ];
		long[] max = new long[ nd ];

		for( int d = 0; d < resolutions.length; d++ )
		{
			min[ d ] = (long)Math.ceil( resolutions[ d ] * interval.realMin( d ));
			max[ d ] = (long)Math.floor( resolutions[ d ] * interval.realMax( d ));
		}
		return new FinalInterval( min, max );
	}
	
	public static void main( String[] args ) throws FormatException, IOException
	{
//		String fname = "/nobackup/saalfeld/john/wong_alignment/brains_2016Mar/groupwiseRegAll/MY20160301_r3_ss2527lc10_frup65_rnai_flyf_00002_baselinedeformed.nii";
//		String fname = "/data-ssd/john/antsDefFieldTests/mytest-1-2-3.nii";
		String fname = "/data-ssd/john/antsDefFieldTests/SSD_exp0029_voldat_flyf_train_Warp_resaveMipav.nii";
//		String fname = "/data-ssd/john/antsDefFieldTests/SSD_exp0029_voldat_flyf_train_Warp_resaveMipav_x5.nii";
//		String fname = "/data-ssd/john/antsDefFieldTests/defField_x_iso_ants.nii";

//		df.defField.numDimensions();
		
//		File srcF = new File( "/data-ssd/john/antsDefFieldTests/test_cyl_exp0029.nii" );
		File srcF = new File( "/data-ssd/john/antsDefFieldTests/test_cyl_exp0029_2a.nii" );
//		File srcF = new File( "/data-ssd/john/antsDefFieldTests/test_fimg.nii" );
		ImagePlus ip = NiftiIo.readNifti( srcF );
		RandomAccessibleInterval<UnsignedByteType> ipimg = ImageJFunctions.wrap( ip );

		
		ANTSDeformationField df = new ANTSDeformationField( new File( fname ) );
//		ANTSDeformationField df = new ANTSDeformationField( 
//				axisAlignedDefField( 1.0f, 0, ipimg ));


//		IntervalView< FloatType > xdef = 
//				Views.hyperSlice( 
//						Views.interval(
//								Views.raster( df.defField ), 
//								largestIntervalFromRealInterval( df.defInterval, df.resolution )),
//						3, 0 );
//		
//		Bdv bdvDefSlc = BdvFunctions.show( xdef, "def" );


//		doit( ipimg, df );


		double[] resolution = new double[]{ 1.0, 1.0, 10.0 };
//		double[] resolution = null;
		RealFloatConverter<UnsignedByteType> conv = new RealFloatConverter<UnsignedByteType>(); 
		RandomAccessibleInterval< FloatType > ipimgconv = Converters.convert( ipimg, conv, new FloatType() );
		RandomAccessibleInterval< FloatType > res = doit( ipimgconv, resolution, df );

		System.out.println( 
				res.dimension( 0 ) + " " +
				res.dimension( 1 ) + " " +
				res.dimension( 2 ));

//		System.out.println( "computing nnz ");
//		System.out.println( "nnz res: " + numNonZero( res ) );


//		BdvOptions bdvopts = BdvOptions.options();
//		bdvopts.numRenderingThreads( 8 );
//		Bdv bdvRes = BdvFunctions.show( res, "img", bdvopts );

//		IJ.save( ImageJFunctions.wrap( res, "res" ), "/data-ssd/john/antsDefFieldTests/test_cyl_exp0029_2a_xfmImglib_small.tif" );


//		double[] p = new double[]{ 5,5,5 };
//		double[] q = new double[3];
		
//		double[] p = new double[]{ 197, 351, 12 };
//		double[] q = new double[3];
//		
//		df.applyInverse( q, p );
//		System.out.println( "q: " + XfmUtils.printArray( q ));


//		RandomAccess< UnsignedByteType > ra = ipimgXfm.randomAccess();
//		ra.setPosition( new int[]{ 87, 164, 10 } );
//		System.out.println( ra.get() );
		
//		IntervalView< UnsignedByteType > imcrp2d = Views.interval( Views.hyperSlice( ipimgXfm, 2, 10 ), new long[]{80, 150 }, new long[]{ 100, 175 } );
//		
//		
//		new ImageJ();
//		ImageJFunctions.wrap( imcrp2d, "subim" ).show();
		
//		Converter< UnsignedByteType, UnsignedByteType > c = new Converter<UnsignedByteType,UnsignedByteType>(){
//
//			@Override
//			public void convert( UnsignedByteType input, UnsignedByteType output )
//			{
//				output.setReal( input.getRealDouble() * 255 );
//			}
//			
//		};
		
//		RandomAccessibleInterval< UnsignedByteType > ipimgxfmC = Converters.convert( ipimgXfm, c, new UnsignedByteType() );
		
//		Bdv bdv = BdvFunctions.show( ipimgXfm, "img xfm" );
		
		//new ImageJ();
		
//		ImageJFunctions.
		
//		ImagePlus ipout = ImageJFunctions.wrap( ipimgXfm, "img xfm" );
//		IJ.save( ipout, "/data-ssd/john/antsDefFieldTests/test_cyl_exp0029_xfm.tif" );
		
//		RealRandomAccess< FloatType > dfra = df.defField;
//		dfra.setPosition( new double[]{ 2, 2, 2, 0 }  );
//		System.out.println( "2 2 2 0: " + dfra.get());
		
//		Views.raster( df.defField )
//		RandomAccessible< FloatType > aa = Views.raster( df.defField );
//		Bdv bdvSP = BdvFunctions.show( Views.interval( aa, df.defInterval ), "df" );
//		
//		dfra.setPosition( new double[]{ 180, 91, 240, 1 }  );
//		System.out.println( "180, 91, 240, 1: " + dfra.get());
		
//		dfra.setPosition( new double[]{ 270, 270, 200, 1 }  );
//		System.out.println( "270, 270, 200, 1: " + dfra.get());
		
//		for( double d = -5.0; d < 20; d = d + 1.0 )
//		for( double c = 0.0; c < 4; c = c + 1.0 ) for( double z = 0.0; z < 20; z = z + 1.0 )	
//		{
//			dfra.setPosition( new double[]{ 2, 2, z, c }  );
//			System.out.println( "z " +  z + ", c " + c + " : " + dfra.get());
//		}
	}

	public static <S extends RealType<S>> int numNonZero( RandomAccessibleInterval<S> img )
	{
		Cursor<S> c = Views.flatIterable( img ).cursor();
		int num = 0;
		while(c.hasNext()){
			S val = c.next();
			if( val.getRealDouble() != 0 ){
				num++;
			}
		}
		return num;
	}
	
	public static <S extends RealType<S>> double[] centroid( RandomAccessibleInterval<S> img )
	{
		double[] centroid = new double[ img.numDimensions() ];
		Cursor<S> c = Views.flatIterable( img ).cursor();
		double mass = 0;
		while(c.hasNext()){
			S val = c.next();
			if( val.getRealDouble() > 0 ){
				mass += val.getRealDouble();
				
				for( int d = 0; d < img.numDimensions(); d++ )
					centroid[ d ] += c.getDoublePosition( d );

			}
		}
		
		for( int d = 0; d < img.numDimensions(); d++ )
			centroid[ d ] /= mass;
		
		return centroid;
	}
	
}
