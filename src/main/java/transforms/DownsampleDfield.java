package transforms;

import java.util.Arrays;
import java.util.concurrent.Callable;

import io.DfieldIoHelper;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import process.DownsampleGaussian;

@Command( version = "0.0.2-SNAPSHOT" )
public class DownsampleDfield implements Callable<Void>
{

	@Option( names = { "-d", "--dfield" }, required = true, description = "Displacement field path" )
	private String fieldPath;

	@Option( names = { "-o", "--output" }, required = true, description = "Output path" )
	private String outputPath;

	@Option( names = { "-f", "--factors" }, required = true, description = "Downsampling factors", split=",")
	private long[] factorsArg;

//	@Option( names = {"-r", "--resolution"}, required = false, 
//			description = "Res" )
//	private double[] targetResolution;

	@Option( names = { "-j", "--nThreads" }, required = false, description = "Number of threads for downsampling" )
	private int nThreads = 8;

	@Option( names = "--sample", required = false, description = "Sample for downsampling instead of averaging" )
	private boolean sample = false;

	@Option( names = "--estimateError", required = false, description = "Estimate errors for downsampling" )
	private boolean estimateError = false;

	@Option( names = { "--sourceSigma", "-s" }, description = "Sigma for source image (default = 0.5)", split=",")
	private double[] sourceSigmasIn = new double[] { 0.5 };

	@Option( names = { "--targetSigma", "-t" }, description = "Sigma for target image (default = 0.5)", split=",")
	private double[] targetSigmasIn = new double[] { 0.5 };


	public static void main( String[] args )
	{
		CommandLine.call( new DownsampleDfield(), args );
	}

	public Void call()
	{
		long[] factors = new long[ 4 ];
		if( factorsArg.length >= 4 )
			factors = factorsArg;
		else
		{
			System.arraycopy( factorsArg, 0, factors, 0, factorsArg.length );
			factors[ 3 ] = 1;
		}

		DfieldIoHelper io = new DfieldIoHelper();
		ANTSDeformationField dfieldobj;
		try
		{
			dfieldobj = io.readAsDeformationField( fieldPath );
		}
		catch ( Exception e1 )
		{
			e1.printStackTrace();
			return null;
		}
		RandomAccessibleInterval< FloatType > dfield = dfieldobj.getImg();
		int nd = dfield.numDimensions();
		
		double[] resIn = new double[ 3 ];
		resIn[ 0 ] = dfieldobj.getResolution()[ 0 ];
		resIn[ 1 ] = dfieldobj.getResolution()[ 1 ];
		resIn[ 2 ] = dfieldobj.getResolution()[ 2 ];


		double[] resOut = new double[ 3 ];
		resOut[ 0 ] = resIn[ 0 ] * factors[ 0 ];
		resOut[ 1 ] = resIn[ 1 ] * factors[ 1 ];
		resOut[ 2 ] = resIn[ 2 ] * factors[ 2 ];


		RandomAccessibleInterval< FloatType > dfieldDown = null;
		if ( sample )
			dfieldDown = Views.subsample( dfield, factors );
		else
		{
			double[] sourceSigmas = DownsampleGaussian.checkAndFillArrays( sourceSigmasIn, nd, "source sigmas" );
			double[] targetSigmas = DownsampleGaussian.checkAndFillArrays( targetSigmasIn, nd, "target sigmas" );

			System.out.println( "source sigmas: " + Arrays.toString( sourceSigmas ) );
			System.out.println( "target sigmas: " + Arrays.toString( targetSigmas ) );

			double[] factorsD = Arrays.stream( factors ).mapToDouble( x -> ( double ) x ).toArray();
			dfieldDown = downsampleDisplacementField( dfield, factorsD, sourceSigmas, targetSigmas, nThreads );
		}

		if( estimateError )
		{
			System.out.println( "estimating error" );
			AffineTransform dfieldToPhysical = new AffineTransform( 4 );
			dfieldToPhysical.set( factors[0], 0, 0 );
			dfieldToPhysical.set( factors[1], 1, 1 );
			dfieldToPhysical.set( factors[2], 2, 2 );

			AffineRandomAccessible< FloatType, AffineGet > dfieldSubInterpReal = 
					RealViews.affine(
							Views.interpolate( 
								Views.extendZero( dfieldDown ),
								new NLinearInterpolatorFactory< FloatType >()),
						dfieldToPhysical );
			
			IntervalView< FloatType > dfieldSubInterpRaster = Views.interval( Views.raster( dfieldSubInterpReal ), dfield );
			DownsampleDfieldErrors.compare( dfield, dfieldSubInterpRaster, nThreads );
		}

		DfieldIoHelper out = new DfieldIoHelper();
		out.spacing = resOut;
		try
		{
			out.write( dfieldDown, outputPath );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		return null;
	}

	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval< T > downsampleDisplacementField(
			final RandomAccessibleInterval< T > dfield, 
			final double[] factors,
			final double[] sourceSigmas, 
			final double[] targetSigmas, 
			int nThreads )
	{
		int vectorDim = dfield.numDimensions() - 1;
		Interval outputIntervalTmp = DownsampleGaussian.inferOutputIntervalFromFactors( Views.hyperSlice( dfield, vectorDim, 0 ), factors );

		long[] outdims = new long[ dfield.numDimensions() ];
		outputIntervalTmp.dimensions( outdims );
		outdims[ vectorDim ] = dfield.dimension( vectorDim );

		ArrayImgFactory< T > factory = new ArrayImgFactory<>( Util.getTypeFromInterval( dfield ));
		ArrayImg< T, ? > dfieldDown = factory.create( outdims );
		downsampleDisplacementField( dfield, dfieldDown, factors, sourceSigmas, targetSigmas, nThreads );
		return dfieldDown;
	}
	
	public static <T extends RealType<T> & NativeType<T>> void downsampleDisplacementField(
			final RandomAccessibleInterval< T > dfield, 
			final RandomAccessibleInterval< T > dfieldDown, 
			final double[] factors,
			final double[] sourceSigmas, 
			final double[] targetSigmas, 
			int nThreads )
	{
		int vectorDim = dfield.numDimensions() - 1;
		assert dfield.dimension( vectorDim ) == dfieldDown.dimension( vectorDim );

		System.out.println( "src interval: " + Util.printInterval( dfield ));
		System.out.println( "dst interval: " + Util.printInterval( dfieldDown ));

		long nd = dfield.dimension( vectorDim );
		final long[] offset = new long[]{ 0, 0, 0 };
		NLinearInterpolatorFactory< T > interpFactory = new NLinearInterpolatorFactory<T>();
		
		for( int i = 0; i < nd; i++ )
		{
			System.out.println( "downsampling vector component " + i );
			IntervalView< T > src = Views.hyperSlice( dfield, vectorDim, i );
			IntervalView< T > dst = Views.hyperSlice( dfieldDown, vectorDim, i );

			DownsampleGaussian.resampleGaussianInplace( 
					src, dst, offset, interpFactory, factors, sourceSigmas, targetSigmas, nThreads, 1e-6 );
		}
	}
	

	public static <T extends RealType<T>> AffineRandomAccessible< T, AffineGet > upsampleDisplacementCoordinate( final RandomAccessibleInterval<T> dfield, final int coord, final AffineTransform3D xfm )
	{
		return RealViews.affine(
			Views.interpolate( Views.extendZero( Views.hyperSlice( dfield, 3, coord )), 
					new NLinearInterpolatorFactory< T >()),
			xfm);
	}
	
}

