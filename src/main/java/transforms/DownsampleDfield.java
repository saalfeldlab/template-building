package transforms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.DoubleStream;

import bigwarp.BigWarpExporter;
import evaluation.TransformComparison;
import io.DfieldIoHelper;
import io.IOHelper;
import io.WriteH5DisplacementField;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.iterator.RealIntervalIterator;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRandomAccessible;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import process.DownsampleGaussian;
import transforms.DownsampleDfieldErrors.SumMax;

@Command( version = "0.2.0-SNAPSHOT" )
public class DownsampleDfield< T extends RealType<T>, O extends RealType<O> & NativeType<O>, Q extends IntegerType<Q> & NativeType<Q>> 
	implements Callable<Void>
{
	public static enum DownsamplingMethod { SAMPLE, AVERAGE, GAUSSIAN };

	@Option( names = { "-i", "--input" }, required = true, description = "Displacement field path" )
	private String fieldPath;

	@Option( names = { "-o", "--output" }, required = true, description = "Output path" )
	private String outputPath;

	@Option( names = { "-f", "--factors" }, required = true, description = "Downsampling factors", split=",")
	private double[] factorsArg;

//	@Option( names = {"-r", "--resolution"}, required = false, 
//			description = "Res" )
//	private double[] targetResolution;

	@Option( names = {"-m", "--method"}, required = false, 
			description = "Downsampling method (sample,average,gaussian). default=\"gaussian\"" )
	private String method = "gaussian";

	@Option( names = { "-j", "--nThreads" }, required = false, description = "Number of threads for downsampling" )
	private int nThreads = 1;

	@Option( names = "--estimate-error", required = false, description = "Estimate errors for downsampling" )
	private boolean estimateError = false;

	@Option( names = { "--sourceSigma", "-s" }, required = false, description = "Sigma for source image (default = 0.5)", split=",")
	private double[] sourceSigmasIn = new double[] { 0.5 };

	@Option( names = { "--targetSigma", "-t" }, required = false, description = "Sigma for target image (default = 0.5)", split=",")
	private double[] targetSigmasIn = new double[] { 0.5 };

	@Option( names = { "--max-error", "-e" }, required = false, description = "Maximum error for estimation (default = 0.5)")
	private double maxError = Double.NaN;

	@Option( names = { "--downsample-recursive", "-d" }, required = false, description = "Downsample by factors until error is less than max error.")
	private boolean doDownsampling = false;

	@Option( names = {"-q", "--quantization-type"}, required = false, description = "Quantize to type (infer,short,byte)" )
	private String quantizationType = "";

	@Option( names = {"-c", "--clip"}, required = false, description = "Clip float values to maxError." )
	private boolean clip = false;

	public static void main( String[] args ) {
		CommandLine.call( new DownsampleDfield(), args );
	}

	@SuppressWarnings("unchecked")
	public Void call() {
		
		if( doDownsampling && Double.isNaN(maxError)) {
			System.err.println( "Must specify a maxError if downsample is selected." );
			return null;
		}

		if( clip && Double.isNaN(maxError)) {
			System.err.println( "Must specify a maxError if clip is selected." );
			return null;
		}

		if (!quantizationType.isEmpty() && Double.isNaN(maxError)) {
			System.err.println("Must specify a maxError if quantization is chosen.");
			return null;
		}

		if ( !quantizationType.isEmpty() && !DfieldIoHelper.isN5TransformBase( outputPath )) {
			System.err.println("Quantization options only valid for h5/n5 containers.");
			return null;
		}

		DfieldIoHelper io = new DfieldIoHelper();
		ANTSDeformationField dfieldobj;
		try
		{
			dfieldobj = io.readAsAntsField( fieldPath );
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
		resOut[ 0 ] = resIn[ 0 ] * factorsArg[ 0 ];
		resOut[ 1 ] = resIn[ 1 ] * factorsArg[ 1 ];
		resOut[ 2 ] = resIn[ 2 ] * factorsArg[ 2 ];

		final double[] sourceSigmas = DownsampleGaussian.checkAndFillArrays( sourceSigmasIn, nd, "source sigmas" );
		final double[] targetSigmas = DownsampleGaussian.checkAndFillArrays( targetSigmasIn, nd, "target sigmas" );

		RandomAccessibleInterval< O > dfieldDown;
		if (doDownsampling) {

			dfieldDown = downsampleDisplacementFieldToError(dfield, factorsArg,
					DownsamplingMethod.valueOf(method.toUpperCase()), sourceSigmas, targetSigmas, maxError,
					quantizationType, nThreads);
//			Object q = getQuantizationType( dfield );
//			dfieldDown = downsampleDisplacementFieldToError( dfield, factorsArg,
//					DownsamplingMethod.valueOf(method.toUpperCase()), sourceSigmas, targetSigmas, maxError, 
//					q, nThreads);
		} else {
			RandomAccessibleInterval<O> dfieldTmp = (RandomAccessibleInterval<O>) downsampleDisplacementField(dfield, factorsArg,
					DownsamplingMethod.valueOf(method.toUpperCase()), sourceSigmas, targetSigmas, 
					nThreads);

			if (!quantizationType.isEmpty()) {
				Q q = getQuantizationType(dfieldTmp);
				System.out.println( "converting to: " + quantizationType );
				dfieldDown = (RandomAccessibleInterval<O>) quantize(dfieldTmp, q, maxError);
			} else
				dfieldDown = dfieldTmp;
		}

//		if ( sample )
//			dfieldDown = Views.subsample( dfield, factors );
//		else
//		{

//			System.out.println( "source sigmas: " + Arrays.toString( sourceSigmas ) );
//			System.out.println( "target sigmas: " + Arrays.toString( targetSigmas ) );
//
//			double[] factorsD = Arrays.stream( factors ).mapToDouble( x -> ( double ) x ).toArray();
//			//dfieldDown = downsampleDisplacementField( dfield, factorsD, sourceSigmas, targetSigmas, nThreads );
//			dfieldDown = downsampleDisplacementFieldStacked( dfield, factorsD, sourceSigmas, targetSigmas, nThreads );
//		}

		if( estimateError )
		{
			System.out.println( "estimating error" );
			final RealIntervalIterator it = new RealIntervalIterator( 
					IOHelper.toRealInterval( Views.hyperSlice(dfield, 3, 0), resIn ), resIn );

			RandomAccessibleInterval<T> dfieldToUse;
			if (!quantizationType.isEmpty()) {
				dfieldToUse = (RandomAccessibleInterval<T>) unquantize( (RandomAccessibleInterval<Q>) dfieldDown, maxError );
			}
			else {
				dfieldToUse = (RandomAccessibleInterval<T>) dfieldDown;
			}

			final DoubleStream errStream = TransformComparison.errorStream(it,
					DfieldIoHelper.toDeformationField(dfield, new Scale( resIn )),
					DfieldIoHelper.toDeformationField(dfieldToUse, new Scale( resOut)));

			final SumMax sm = new SumMax( 0, Double.MIN_VALUE );
			errStream.forEach( x -> {
				sm.sum += x;
				if( x > sm.max )
					sm.max = x;
			});

			System.out.println( "avg err: " + (sm.sum / (Intervals.numElements(dfield)/3) ));
			System.out.println( "max err: " + sm.max);
			System.out.println( " ");

//			// TODO generalize to 2d
//			AffineTransform dfieldToPhysical = new AffineTransform( 4 );
//			dfieldToPhysical.set( factorsArg[0], 0, 0 );
//			dfieldToPhysical.set( factorsArg[1], 1, 1 );
//			dfieldToPhysical.set( factorsArg[2], 2, 2 );
//
//			AffineRandomAccessible< FloatType, AffineGet > dfieldSubInterpReal = 
//					RealViews.affine(
//							Views.interpolate( 
//								Views.extendZero( dfieldDown ),
//								new NLinearInterpolatorFactory< FloatType >()),
//						dfieldToPhysical );
//			
//			IntervalView< FloatType > dfieldSubInterpRaster = Views.interval( Views.raster( dfieldSubInterpReal ), dfield );
//			DownsampleDfieldErrors.compare( dfield, dfieldSubInterpRaster, nThreads );
//			System.out.println( " ");
		}

		DfieldIoHelper out = new DfieldIoHelper();
		out.spacing = resOut;
		try {
			out.write( dfieldDown, outputPath );
		}
		catch ( Exception e ) {
			e.printStackTrace();
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public <T extends RealType<T> & NativeType<T>, S extends RealType<S> & NativeType<S>, Q extends IntegerType<Q> & NativeType<Q>> 
		RandomAccessibleInterval<S> downsampleDisplacementFieldToError(
			final RandomAccessibleInterval< T > dfield, 
			final double[] factors,
			final DownsamplingMethod method,
			final double[] sourceSigmas, 
			final double[] targetSigmas, 
			final double maxError,
			final String quantizationType,
			int nThreads )
	{
		Q q = getQuantizationType(dfield, quantizationType, maxError );

		final Scale orig = new Scale(new double[] { 1, 1, 1 });
		final Scale down = orig.copy();

		final int maxDownsamplingSteps = 8;

		final Scale factor = new Scale(factors);
		double error = 0;
		RandomAccessibleInterval<S> dfieldOut = (RandomAccessibleInterval<S>) dfield;

		int i = 0;
		while (error < maxError && i < maxDownsamplingSteps ) {

			i++;

			final RandomAccessibleInterval< S > dfieldDown;
			final RandomAccessibleInterval< T > dfieldDownTmp = (RandomAccessibleInterval<T>) downsampleDisplacementField(
					dfieldOut, factors, method,
					sourceSigmas, targetSigmas, nThreads );

			if( q == null )
				dfieldDown = (RandomAccessibleInterval<S>) dfieldDownTmp;
			else
				dfieldDown = (RandomAccessibleInterval<S>) quantize( dfieldDownTmp, q, maxError );


			down.preConcatenate(factor);

			// compare downsampled to original
			final RealIntervalIterator it = new RealIntervalIterator( Views.hyperSlice(dfield, 3, 0), new double[] { 1, 1, 1 });
			final DoubleStream errStream = TransformComparison.errorStream(it,
					DfieldIoHelper.toDeformationField(dfield, orig),
					DfieldIoHelper.toDeformationField(dfieldDown, down));

			// estimate error
			OptionalDouble maxOpt = errStream.max();
			double err = maxOpt.getAsDouble();
			System.out.println( "err: " + err );
			error = err;
			if (error < maxError)
				dfieldOut = (RandomAccessibleInterval<S>) dfieldDown;

			// if error is larger than threshold, break and return current dfield
		}
		return dfieldOut;
	}

	@SuppressWarnings("unchecked")
	public static <T extends RealType<T> & NativeType<T>, S extends RealType<S> & NativeType<S>> S inferBestQuantizationType(
			final RandomAccessibleInterval<T> dfield, 
			final double maxError ) {

		final int nd = dfield.numDimensions() - 1;
		final double quantizationMultiplier = 2 * Math.sqrt(maxError * maxError / nd);
		System.out.println( "quantizationMultiplier " + quantizationMultiplier );
		final double maxCoordDisplacement = WriteH5DisplacementField.getMaxAbs( Views.flatIterable(dfield) );

		// check if bytes work?
		if (quantizationMultiplier * 127.0 > maxCoordDisplacement)
			return (S) new ByteType();
		else if (quantizationMultiplier * 32767.0 > maxCoordDisplacement)
			return (S) new ShortType();
		else
			return null;
	}

	public static <T extends RealType<T> & NativeType<T>, Q extends NativeType<Q> & IntegerType<Q>> Q getQuantizationType(
			final RandomAccessibleInterval<T> dfield,
			final String quantizationType,
			final double maxError )
	{
		if( quantizationType.toUpperCase().equals("INFER"))
			return inferBestQuantizationType( dfield, maxError );
		else if( quantizationType.toUpperCase().equals("SHORT"))
			return (Q) new ShortType();
		else if( quantizationType.toUpperCase().equals("BYTE"))
			return (Q) new ByteType();
		else
			return null;
	}

	public Q getQuantizationType(
			final RandomAccessibleInterval<O> dfield )
	{
		if( quantizationType.toUpperCase().equals("INFER"))
			return inferBestQuantizationType( dfield, maxError );
		else if( quantizationType.toUpperCase().equals("SHORT"))
			return (Q) new ShortType();
		else if( quantizationType.toUpperCase().equals("BYTE"))
			return (Q) new ByteType();
		else
			return null;
	}

	public static <T extends RealType<T> & NativeType<T>, Q extends NativeType<Q> & IntegerType<Q>> 
		RandomAccessibleInterval<Q> quantize(
			final RandomAccessibleInterval<T> dfield,
			final Q outputType,
			final double maxError ) {

		assert( outputType != null );

		final int nd = dfield.numDimensions() - 1;
		final double quantizationMultiplier = 2 * Math.sqrt(maxError * maxError / nd);
		return convertQuantize( dfield, outputType, quantizationMultiplier );
	}

	@SuppressWarnings("unchecked")
	public static <T extends RealType<T> & NativeType<T>, Q extends NativeType<Q> & IntegerType<Q>> RandomAccessibleInterval<T> unquantize(
			final RandomAccessibleInterval<Q> dfield, final double maxError) {

		final int nd = dfield.numDimensions() - 1;
		final double quantizationMultiplier = 2 * Math.sqrt(maxError * maxError / nd);
		return (RandomAccessibleInterval<T>) convertUnquantize(dfield, new DoubleType(), quantizationMultiplier);
	}

	public static <T extends RealType<T> & NativeType<T>, Q extends NativeType<Q> & IntegerType<Q>> 
		RandomAccessibleInterval<Q> convertQuantize(
			final RandomAccessibleInterval<T> dfield,
			final Q outputType,
			final double m) {

		return Converters.convert(dfield, new Converter<T, Q>() {
			@Override
			public void convert(final T input, final Q output) {
				output.setInteger(Math.round(input.getRealDouble() / m));
			}
		}, outputType.copy());
	}

	public static <T extends RealType<T>, Q extends IntegerType<Q>> RandomAccessibleInterval<T> convertUnquantize(
			final RandomAccessibleInterval<Q> dfield, final T outputType, final double m) {

		return Converters.convert(dfield, new Converter<Q, T>() {
			@Override
			public void convert(final Q input, final T output) {
				output.setReal( input.getRealDouble() * m  );
			}
		}, outputType.copy());
	}

	public static <T extends RealType<T> & NativeType<T>, Q extends NativeType<Q> & IntegerType<Q>> 
		RandomAccessibleInterval< T > downsampleDisplacementField(
			final RandomAccessibleInterval< T > dfield, 
			final double[] factors,
			final DownsamplingMethod method,
			final double[] sourceSigmas, 
			final double[] targetSigmas, 
			int nThreads )
	{
		long[] lfactors = toDiscreteFactors(factors);
		switch( method ) {
		case SAMPLE:
			if( lfactors == null ) {
				System.err.println( "factors must be discrete for SAMPLING downsampling");
				return null;
			}
			return downsampleDisplacementFieldSample( dfield, lfactors );

		case AVERAGE:
			if( lfactors == null ) {
				System.err.println( "factors must be discrete for AVERAGE downsampling");
				return null;
			}
			return downsampleDisplacementFieldAverage( dfield, lfactors, nThreads );
			
		case GAUSSIAN:
			return downsampleDisplacementField( dfield, factors, sourceSigmas, targetSigmas, nThreads );
		
		default:
			return null;
		}
	}

	public static long[] toDiscreteFactors(final double[] factors, final double eps) {
		long[] out = new long[factors.length];
		for (int i = 0; i < factors.length; i++) {
			out[i] = (long) Math.round(factors[i]);
			if (Math.abs(out[i] - factors[i]) > eps)
				return null;
		}
		return out;
	}

	public static long[] toDiscreteFactors(final double[] factors) {
		return toDiscreteFactors(factors, 1e-9);
	}

	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval< T > downsampleDisplacementFieldStacked(
			final RandomAccessibleInterval< T > dfield, 
			final double[] factors,
			final double[] sourceSigmas, 
			final double[] targetSigmas, 
			int nThreads )
	{
		int vectorDim = dfield.numDimensions() - 1;
		Interval spatialInterval = DownsampleGaussian.inferOutputIntervalFromFactors( Views.hyperSlice( dfield, vectorDim, 0 ), factors );
		NLinearInterpolatorFactory< T > interpFactory = new NLinearInterpolatorFactory<T>();

		ArrayList<RandomAccessibleInterval<T>> vecList = new ArrayList<>();
		for( int i = 0; i < dfield.numDimensions() - 1; i++ )
		{
			ArrayImgFactory< T > factory = new ArrayImgFactory<>( Util.getTypeFromInterval( dfield ));
			ArrayImg< T, ? > dfieldComponent = factory.create( spatialInterval );

			IntervalView< T > dv = Views.hyperSlice( dfield, vectorDim, i );
			DownsampleGaussian.resampleGaussian( 
					dv, dfieldComponent, new long[ spatialInterval.numDimensions() ], interpFactory, factors, sourceSigmas, targetSigmas, nThreads, 1e-6 );

			vecList.add( dfieldComponent );
		}

		return Views.stack( vecList );
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

//			DownsampleGaussian.resampleGaussianInplace( 
//					src, dst, offset, interpFactory, factors, sourceSigmas, targetSigmas, nThreads, 1e-6 );

			DownsampleGaussian.resampleGaussian( 
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
	
	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval< T > downsampleDisplacementField(
			final RandomAccessibleInterval< T > dfield, 
			final double[] factors,
			int nThreads )
	{
		int vectorDim = dfield.numDimensions() - 1;
		Interval outputIntervalTmp = DownsampleGaussian.inferOutputIntervalFromFactors( Views.hyperSlice( dfield, vectorDim, 0 ), factors );

		long[] outdims = new long[ dfield.numDimensions() ];
		outputIntervalTmp.dimensions( outdims );
		outdims[ vectorDim ] = dfield.dimension( vectorDim );

		ArrayImgFactory< T > factory = new ArrayImgFactory<>( Util.getTypeFromInterval( dfield ));
		ArrayImg< T, ? > dfieldDown = factory.create( outdims );

		return dfieldDown;
	}
	
	/**
	 * Downsample a displacement field by sampling.
	 * 
	 * 
	 * @param <T>
	 * @param dfield displacement field with vector in last index
	 * @param factors downsampling factors in spatial dimensions 
	 * @return
	 */
	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval< T > downsampleDisplacementFieldSample(
			final RandomAccessibleInterval< T > dfield, 
			final long[] factors)
	{
		assert( factors.length == dfield.numDimensions()-1);

		final long[] factorsToUse = new long[ factors.length + 1] ;
		for( int i = 0; i < factors.length; i++ ) {
			factorsToUse[ i ] = factors[ i ];
		}
		factorsToUse[ factors.length ] = 1;

		return Views.subsample(dfield, factorsToUse);
	}
	
	public static long[] validateDownsampleFactors( long[] factors, int nd )
	{
		final long[] out;
		if( factors.length == nd )
			out = factors;
		else
		{
			out = new long[ nd ];
			for( int i = 0; i < nd; i++ )
				if( i < factors.length )
					out[ i ] = factors[ i ];
				else 
					out[ i ] = 1;
		}	
		return out;
	}
	
	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> downsampleDisplacementFieldAverage(
			RandomAccessibleInterval<T> dfield,
			long[] factorsIn )
	{
		long[] factors = validateDownsampleFactors( factorsIn, dfield.numDimensions() );

		ArrayImgFactory< T > factory = new ArrayImgFactory<>( Util.getTypeFromInterval( dfield ));
		ArrayImg< T, ? > out = factory.create( resampledDfieldSize( dfield, factors ));

		final RandomAccess< T > dfRa = Views.extendMirrorDouble( dfield ).randomAccess();
		final IntervalIterator it = new IntervalIterator( factors );
		final long N = Intervals.numElements(it);
		System.out.println( "downsampleDisplacementFieldAverage");
		System.out.println( "nelems: " + N );

		final ArrayCursor< T > c = out.cursor();
		while( c.hasNext() )
		{
			double avgval = 0;
			
			c.fwd();
			for( int d = 0; d < dfRa.numDimensions(); d++ )
				dfRa.setPosition( factors[ d ] * c.getLongPosition( d ), d );
			
			it.reset();
			while( it.hasNext() )
			{
				it.fwd();
				
				for( int d = 0; d < dfRa.numDimensions(); d++ )
					dfRa.setPosition( factors[ d ] * c.getLongPosition( d ), d );
			
				dfRa.move( it );
				avgval += dfRa.get().getRealDouble();
			}
			c.get().setReal( avgval / N );
		}
		
		return out;
	}
	
	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> downsampleDisplacementFieldAverage(
			final RandomAccessibleInterval<T> dfield,
			final long[] subsample_factors,
			final int nThreads )
	{
		if( nThreads <= 1 )
			return downsampleDisplacementFieldAverage( dfield, subsample_factors );
		else
			return downsampleDisplacementFieldAverageImpl( dfield, subsample_factors, nThreads );
	}

	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> downsampleDisplacementFieldAverageImpl(
			final RandomAccessibleInterval<T> dfield,
			final long[] subsample_factors,
			final int nThreads )
	{
		long[] factors = validateDownsampleFactors( subsample_factors, dfield.numDimensions() );
		System.out.println( "downsampleDisplacementFieldAverageParallel");
		long[] interval = resampledDfieldSize( dfield, subsample_factors );

		ArrayImgFactory< T > factory = new ArrayImgFactory<>( Util.getTypeFromInterval( dfield ));
		ArrayImg< T, ? > out = factory.create( resampledDfieldSize( dfield, subsample_factors ));

//		System.out.println( "out sz : " + Util.printInterval( out ));
		
		final int dim2split = 2;

		final long[] splitPoints = new long[ nThreads + 1 ];
		long N = out.dimension( dim2split );
		long del = ( long )( N / nThreads ); 
		splitPoints[ 0 ] = 0;
		splitPoints[ nThreads ] = out.dimension( dim2split );
		for( int i = 1; i < nThreads; i++ )
		{
			splitPoints[ i ] = splitPoints[ i - 1 ] + del;
		}
		System.out.println( "dim2split: " + dim2split );
		System.out.println( "split points: " + Arrays.toString( splitPoints ));

		ExecutorService threadPool = Executors.newFixedThreadPool( nThreads );
		LinkedList<Callable<Boolean>> jobs = new LinkedList<Callable<Boolean>>();
		for( int i = 0; i < nThreads; i++ )
		{
			final long start = splitPoints[ i ];
			final long end   = splitPoints[ i+1 ];

			jobs.add( new Callable<Boolean>()
			{
				public Boolean call()
				{
					final IntervalIterator it = new IntervalIterator( factors );
					final long N = Intervals.numElements(it);

					final RandomAccess< T > dfRa = Views.extendMirrorDouble( dfield ).randomAccess();
					
					double intervalPixelCount = factors[ 0 ] * factors[ 1 ] * factors[ 2 ]; 
					System.out.println( "nelems: " + intervalPixelCount );
					
					final FinalInterval subItvl = BigWarpExporter.getSubInterval( out, dim2split, start, end );
					final IntervalView< T > subTgt = Views.interval( out, subItvl );
					final Cursor< T > c = subTgt.cursor();
					
					while( c.hasNext() )
					{
						double avgval = 0;

						c.fwd();
						for( int d = 0; d < dfRa.numDimensions(); d++ )
							dfRa.setPosition( factors[ d ] * c.getLongPosition( d ), d );
						
						it.reset();
						while( it.hasNext() )
						{
							it.fwd();
							for( int d = 0; d < dfRa.numDimensions(); d++ )
								dfRa.setPosition( factors[ d ] * c.getLongPosition( d ), d );

							dfRa.move( it );
							avgval += dfRa.get().getRealDouble();
						}
						c.get().setReal( avgval / N );
					}
					
					return true;
				}
			});
		}
		
		try
		{
			List< Future< Boolean > > futures = threadPool.invokeAll( jobs );
			for( Future<Boolean> f : futures )
				f.get();

			threadPool.shutdown(); // wait for all jobs to finish
		}
		catch ( InterruptedException e1 )
		{
			e1.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		
		return out;
	}	

	public static long[] resampledDfieldSize( final Dimensions dims, final double[] downsampleFactors )
	{
		int nd = dims.numDimensions();
		long[] out = new long[ nd ];
		for( int i = 0; i < nd; i++ ) {
			if (i == (nd - 1))
				out[i] = dims.dimension(i);
			else
				out[i] = (long) Math.ceil(dims.dimension(i) / downsampleFactors[i]);
		}
		return out;
	}
	
	public static long[] resampledDfieldSize( final Dimensions dims, final long[] downsampleFactors )
	{
		int nd = dims.numDimensions();
		long[] out = new long[ nd ];
		for( int i = 0; i < nd; i++ ) {
			if (i == (nd - 1))
				out[i] = dims.dimension(i);
			else
				out[i] = (long) Math.ceil(dims.dimension(i) / downsampleFactors[i]);
		}
		return out;
	}	

}

