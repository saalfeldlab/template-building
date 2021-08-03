package transforms;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.AffineImglib2IO;
import io.DfieldIoHelper;
import io.IOHelper;
import mpicbg.models.AffineModel3D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.ConstantUtils;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.SubsampleIntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.GenericComposite;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Estimates an affine transform from a displacement field. 
 * 
 * Optionally exports that displacement field (d_new) with the affine "removed".
 * Removing the affine means that the resul (d_new) followed by the affine
 * is equivalent to the original displacement field.
 * 
 * @author John Bogovic
 *
 * @param <T> displacement field type
 */
@Command( version = "0.1.1-SNAPSHOT")
public class AffineFromDisplacement<T extends RealType<T> & NativeType<T>> implements Callable< Void >
{
	@Option( names = { "-i", "--input" }, required = true, description = "Input displacement field" )
	private List< String > inputFiles = new ArrayList<>();

	@Option( names = { "-o", "--out-suffix" }, required = false, description = "Output displacement field suffix" )
	private String dfieldSuffix = "_noAffine.nrrd";

	@Option( names = { "-a", "--affine-suffix" }, required = false, description = "Output displacement field suffix" )
	private String affineSuffix = "_affine.txt";

	@Option( names = { "-s", "--sample-step" }, required = false, description = "Sampling step" )
	private int step = 4;

	@Option( names = { "-q", "--num-threads" }, required = false, description = "Sampling step" )
	private int nThreads = 1;

	@Option( names = { "--do-warp" }, required = false, description = "Write displacementfield with affine part removed" )
	private Boolean doWarp = false;

	@Option( names = { "--filter" }, required = false, description = "Filter: ignore (0,0,0) vectors" )
	private Boolean filter = true;

	@Option( names = { "-m", "--mask" }, required = false, description = "Mask for affine estimation. "
			+ "Only coordinates where mask > threshold are used to estimate affine" )
	private String maskPath;

	@Option( names = { "-t", "--mask-threshold" }, required = false, description = "Mask threshold (default = 0)" )
	private Double threshold = 0.0;

	public AffineFromDisplacement() { }
	
	@SuppressWarnings( "unchecked" )
	public static void main( String[] args )
	{
		CommandLine.call( new AffineFromDisplacement(), args );
		System.exit(0);
	}

	public static <T extends RealType<T>> RandomAccessible<BoolType> buildMask( String maskPath, double threshold ) {

		if( maskPath != null ) {
		IOHelper io = new IOHelper();
			RandomAccessibleInterval<T> maskRaw = (RandomAccessibleInterval<T>) io.readRai(maskPath);
			ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval<T>> maskExt = Views.extendBorder( maskRaw );
			return Converters.convert(maskExt, (t,b) -> b.set( t.getRealDouble() > threshold), new BoolType());
		}
		else {
			return ConstantUtils.constantRandomAccessible( new BoolType( true ), 3);
		}
	}

	public Void call()
	{
		final RandomAccessible<BoolType> mask = buildMask( maskPath, threshold ) ;

		DfieldIoHelper dfieldIo = new DfieldIoHelper();
		for( String dfieldPath : inputFiles )
		{
			String outputPrefix = dfieldPath.substring( 0, dfieldPath.lastIndexOf( '.' ));
			System.out.println( "out prefix: " + outputPrefix );
			
			ANTSDeformationField dfield = null;
			try
			{
				dfield = dfieldIo.readAsAntsField( dfieldPath );
				System.out.println( "dfield: " + dfield );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				continue;
			}

			AffineTransform3D affine;
			try
			{
				affine = estimateAffine( dfield.getImg(), mask, dfield.getResolution(), step, true );
				System.out.println( affine );
				AffineImglib2IO.writeXfm( new File( outputPrefix + affineSuffix ), affine );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				continue;
			}
			
			if( doWarp )
			{
				System.out.println( "do warp" );
				removeAffineComponentAndWrite( 
						new File( outputPrefix + dfieldSuffix),
						affine, dfield.getImg(), Util.getTypeFromInterval( dfield.getImg() ),
						dfield.getDefField(), dfield.getResolution(), 
						filter,
						nThreads );
			}
		}

		return null;
	}
		
	public static < T extends RealType< T > & NativeType< T > > void writeDfield( 
			final String outputFile, final RandomAccessibleInterval<T> dfieldIn,
			final double[] outputResolution ) throws Exception
	{
			ImagePlusImgFactory< T > factory = new ImagePlusImgFactory<>( Util.getTypeFromInterval( dfieldIn ) );
			ImagePlusImg< T, ? > dfieldraw = factory.create( dfieldIn );

			RandomAccessibleInterval< T > dfield = DfieldIoHelper.vectorAxisPermute( dfieldraw, 3, 3 );
			DfieldIoHelper dfieldIo = new DfieldIoHelper();
			dfieldIo.spacing = outputResolution; // naughty
			try
			{
				dfieldIo.write( dfield, outputFile );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
			}
	}

	/**
	 * Removes the affine part from a displacement field 
	 * 
	 * The resulting displacement field is such that the result followed by the affine
	 * is identical to the original displacement field.
	 * 
	 * @param displacementField the displacement field 
	 * @param affine the affine part 
	 * @param filter if true, ignores vectors in the field equal to (0,0,0)
	 */
	public static <S extends RealType<S>, T extends RealType< T > & NativeType<T>> void removeAffineComponentAndWrite(
			final File outFile,
			final AffineTransform3D affine,
			final Interval intervalIn,
			final T t,
			final RealRandomAccessible< S > displacementFieldIn,
			final double[] outputResolution,
			final boolean filter,
			final int numThreads )
	{
		Interval intervalOut = DfieldIoHelper.dfieldIntervalVectorFirst3d( intervalIn );
		Scale3D pixelToPhysical = new Scale3D( outputResolution );

		ImagePlusImgFactory< T > factory = new ImagePlusImgFactory<>( t );
		ImagePlusImg< T, ? > dfieldraw = factory.create( intervalOut );
		try
		{

			RandomAccessibleInterval< T > dfield = DfieldIoHelper.vectorAxisPermute( dfieldraw, 3, 3 );
			if( numThreads == 1 )
				removeAffineComponent( affine, dfield, displacementFieldIn, pixelToPhysical, filter );
			else
				removeAffineComponent( affine, dfield, displacementFieldIn, pixelToPhysical, filter, numThreads );

			DfieldIoHelper dfieldIo = new DfieldIoHelper();
			dfieldIo.spacing = outputResolution; // naughty
			dfieldIo.write( dfieldraw, outFile.getAbsolutePath() );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			return;
		}

	}

	/**
	 * Removes the affine part from a displacement field 
	 */
	public static <S extends RealType<S>,T extends RealType< T >> void removeAffineComponent(
			final AffineTransform3D affine,
			final RandomAccessibleInterval< T > displacementFieldOut,
			final RealRandomAccessible< S > displacementFieldInPhysical,
			final AffineGet pixelToPhysical,
			final boolean filter )
	{
		final int VECTOR_DIM = 3;
		System.out.println("removing affine from dfield");

		CompositeIntervalView< T, ? extends GenericComposite< T > > vectorField = Views.collapse( displacementFieldOut );
		Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( vectorField ).cursor();
		
		RealRandomAccess< S > realRa = displacementFieldInPhysical.realRandomAccess();

		RealPoint physPt = new RealPoint( 3 );
		RealPoint affineResult = new RealPoint( 3 );
		RealPoint physicalOutputPoint = new RealPoint( 3 );
		RealPoint physicalDestinationPoint = new RealPoint( 3 );

		while ( c.hasNext() )
		{
			GenericComposite< T > outputVector = c.next();

			pixelToPhysical.apply( c, physPt );
			for ( int i = 0; i < 3; i++ )
				realRa.setPosition( physPt.getDoublePosition( i ), i );

			realRa.setPosition( 0, VECTOR_DIM ); // first vector coordinate
			if ( filter )
			{
				boolean allZeros = true;
				for ( int i = 0; i < 3; i++ )
				{
					if( realRa.get().getRealFloat() != 0.0f )
					{
						allZeros = false;
						break;
					}
					realRa.fwd( VECTOR_DIM );
				}
				
				if( allZeros )
					continue;
			}
			
			// go to real space
			pixelToPhysical.apply( c, physicalOutputPoint );

			// what real coordinate does this tranformation go to
			realRa.setPosition( 0, 3 ); // first vector coordinate
			for ( int d = 0; d < 3; d++ )
			{
				physicalDestinationPoint.setPosition(
						physicalOutputPoint.getDoublePosition( d ) + realRa.get().getRealDouble(),
						d );
				realRa.fwd( VECTOR_DIM );
			}

			// remove the affine part
			affine.applyInverse( affineResult, physicalDestinationPoint  );
	
			// set the output
			for ( int i = 0; i < 3; i++ )
				outputVector.get( i ).setReal(
						 affineResult.getDoublePosition( i ) - physicalOutputPoint.getDoublePosition( i ) );

		}
	}

	/**
	 * Removes the affine part from a displacement field 
	 */
	public static <S extends RealType<S>,T extends RealType< T >> void removeAffineComponent(
			final AffineTransform3D affine,
			final RandomAccessibleInterval< T > displacementFieldOut,
			final RealRandomAccessible< S > displacementFieldIn,
			final AffineGet pixelToPhysical,
			final boolean filter,
			final int numThreads )
	{
		System.out.println("removing affine from dfield");

		CompositeIntervalView< T, ? extends GenericComposite< T > > vectorField = Views.collapse( displacementFieldOut );
	
		ExecutorService threadPool = Executors.newFixedThreadPool( numThreads );
		LinkedList< Callable< Boolean > > jobs = new LinkedList< Callable< Boolean > >();
		for ( int task = 0; task < numThreads; task++ )
		{
			final int index = task;
			jobs.add( new Callable< Boolean >()
			{
				public Boolean call()
				{
					final RealPoint tmp1 = new RealPoint( 3 );
					final RealPoint tmp2 = new RealPoint( 3 );
					final RealPoint tmp3 = new RealPoint( 3 );

					final Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( vectorField ).cursor();
					final RealRandomAccess< S > realRa = displacementFieldIn.realRandomAccess();
					
					final AffineTransform3D affineCopy = affine.copy();
					final AffineGet pix2PhysCopy = pixelToPhysical.copy();

					c.jumpFwd( index );
					while ( c.hasNext() )
					{
						c.jumpFwd( numThreads );
						removeAffinePart( c, affineCopy,
								realRa, pix2PhysCopy,
								filter, tmp1, tmp2, tmp3 );
								
					}
					return true;
				}
			});
		}

		List< Future< Boolean > > futures;
		try
		{
			futures = threadPool.invokeAll( jobs );
			List< Boolean > results = new ArrayList< Boolean >();
			for ( Future< Boolean > f : futures )
				results.add( f.get() );
		}
		catch ( InterruptedException e )
		{
			e.printStackTrace();
		}
		catch ( ExecutionException e )
		{
			e.printStackTrace();
		}

	}
	
	/**
	 * Subroutine for removing an affine froma a vector displacement field.
	 * Cursor must be set to the correct position before calling this method (the cursor position is not modified).
	 * 
	 * @param c cursor holding displacement vector as a composite
	 * @param affine the affine to remove
	 * @param realRa original displacement 
	 * @param pixelToPhysical transform from coordinate of 
	 * @param filter ignore the zero vector?
	 * @param tmp1 temporary storage 1 (to reuse)
	 * @param tmp2 temporary storage 2 (to reuse)
	 */
	public static <T extends RealType<T>, S extends RealType<S>, V extends Composite<T>> void removeAffinePart( 
			final Cursor<V> c,
			final AffineGet affine,
			final RealRandomAccess< S > realRa,
			final AffineGet pixelToPhysical,
			final boolean filter,
			final RealPoint tmp1,
			final RealPoint tmp2,
			final RealPoint tmp3 )
	{
		final int VECTOR_DIM = 3;
		Composite< T > outputVector = c.get();

		RealPoint physPt = tmp1;
		pixelToPhysical.apply( c, physPt );
		for ( int i = 0; i < 3; i++ )
			realRa.setPosition( physPt.getDoublePosition( i ), i );

		realRa.setPosition( 0, VECTOR_DIM ); // first vector coordinate
		if ( filter )
		{
			boolean allZeros = true;
			for ( int i = 0; i < 3; i++ )
			{
				if( realRa.get().getRealFloat() != 0.0f )
				{
					allZeros = false;
					break;
				}
				realRa.fwd( VECTOR_DIM );
			}
	
			if( allZeros )
				return;
		}
		
		// go to real space
		RealPoint physicalOutputPoint = tmp1;
		RealPoint physicalDestinationPoint = tmp2;
		pixelToPhysical.apply( c, physicalOutputPoint );

		// what real coordinate does this tranformation go to
		realRa.setPosition( 0, 3 ); // first vector coordinate
		for ( int d = 0; d < 3; d++ )
		{
			physicalDestinationPoint.setPosition(
					physicalOutputPoint.getDoublePosition( d ) + realRa.get().getRealDouble(),
					d );
			realRa.fwd( VECTOR_DIM );
		}

		// remove the affine part
		RealPoint affineResult = tmp3;
		affine.applyInverse( affineResult, physicalDestinationPoint  );

		// set the output
		for ( int i = 0; i < 3; i++ )
			outputVector.get( i ).setReal(
					 affineResult.getDoublePosition( i ) - physicalOutputPoint.getDoublePosition( i ) );
	}

	/**
	 * Removes the affine part from a displacement field 
	 * @param displacementField the displacement field 
	 * @param affine the affine part 
	 * @param filter if true, ignores vectors in the field equal to (0,0,0)
	 */
	public static <T extends RealType< T >> void removeAffineComponentLegacy(
			RandomAccessibleInterval< T > displacementField, 
			AffineTransform3D affine,
			boolean filter )
	{

		CompositeIntervalView< T, ? extends GenericComposite< T > > vectorField = Views
				.collapse( displacementField );
		Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( vectorField )
				.cursor();

		RealPoint affineResult = new RealPoint( 3 );
		RealPoint y = new RealPoint( 3 );

		while ( c.hasNext() )
		{
			GenericComposite< T > vector = c.next();

			if ( filter && 
					vector.get( 0 ).getRealDouble() == 0 && 
					vector.get( 1 ).getRealDouble() == 0 && 
					vector.get( 2 ).getRealDouble() == 0 )
			{
				continue;
			}

			for ( int i = 0; i < 3; i++ )
				y.setPosition( c.getDoublePosition( i ) + vector.get( i ).getRealDouble(), i );

			affine.applyInverse( affineResult, y  );
			for ( int i = 0; i < 3; i++ )
				vector.get( i ).setReal(
						 affineResult.getDoublePosition( i ) - c.getDoublePosition( i ) );
		}
	}

	public static <T extends RealType< T >> AffineTransform3D estimateAffine(
			final RandomAccessibleInterval< T > displacementField,
			final RandomAccessible< BoolType > mask,
			final double[] resolution,
			int step, boolean filter )
					throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		
		assert resolution.length == displacementField.numDimensions() - 1;

		final double[] resolutionAndStep = new double[ resolution.length ];
		for( int d = 0; d < resolution.length; d++ )
		{
			resolutionAndStep[ d ] = step * resolution[ d ];
		}

		CompositeIntervalView< T, ? extends GenericComposite< T > > tmp = Views.collapse( displacementField );
		System.out.println( "collapsed INTERVAL: " + Util.printInterval( tmp ));

		SubsampleIntervalView< ? extends GenericComposite< T > > vectorField = Views.subsample( tmp, step );
		System.out.println( "sub INTERVAL: " + Util.printInterval( vectorField ));

		final Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( vectorField ).localizingCursor();
		final RandomAccess<BoolType> maskRa = Views.subsample(mask, step).randomAccess();

		ArrayList<PointMatch> matches = new ArrayList<PointMatch>( (int)Intervals.numElements( vectorField ));
		int numSkipped = 0;

		while( c.hasNext())
		{
			GenericComposite< T > vector = c.next();
			maskRa.setPosition(c);
			boolean use = maskRa.get().get();

			if(	!use ||  
					(filter &&
					vector.get( 0 ).getRealDouble() == 0  && 
					vector.get( 1 ).getRealDouble() == 0  && 
					vector.get( 2 ).getRealDouble() == 0) )
			{
				numSkipped++;
				continue;

			}

			matches.add( new PointMatch( 
					new Point( new double[]{
							resolutionAndStep[ 0 ] * c.getDoublePosition( 0 ),
							resolutionAndStep[ 1 ] * c.getDoublePosition( 1 ),
							resolutionAndStep[ 2 ] * c.getDoublePosition( 2 ),
					}),
					new Point( new double[]{
							resolutionAndStep[ 0 ] * c.getDoublePosition( 0 ) + vector.get( 0 ).getRealDouble(),
							resolutionAndStep[ 1 ] * c.getDoublePosition( 1 ) + vector.get( 1 ).getRealDouble(),
							resolutionAndStep[ 2 ] * c.getDoublePosition( 2 ) + vector.get( 2 ).getRealDouble()
					})
				));
		}

		System.out.println( "skipped " + numSkipped + " points.");
		System.out.println( "fitting affine with " + matches.size() + " point matches.");

		AffineModel3D estimator = new AffineModel3D();
		estimator.fit( matches );

		AffineTransform3D affine = new AffineTransform3D();
		affine.set( estimator.getMatrix( new double[ 12 ] ));
		return affine;

	}

	public static <T extends RealType< T >> AffineTransform3D estimateAffineRaw(
			RandomAccessibleInterval< T > displacementField )
					throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		// IntervalView< T > physicalSpace = Views.hyperSlice( displacementField, 3, 0 ); 
		CompositeIntervalView< T, ? extends GenericComposite< T > > vectorField = Views.collapse( displacementField );

		int N = 0;
		Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( vectorField ).cursor();
		while( c.hasNext())
		{
			c.fwd();
			N++;
		}
		c.reset();

		int i = 0;
		double[][] p = new double[ 3 ][ N ];
		double[][] q = new double[ 3 ][ N ];
		double[] w = new double[ N ];
		while( c.hasNext())
		{
			GenericComposite< T > vector = c.next();

			p[ 0 ][ i ] = c.getDoublePosition( 0 );
			p[ 1 ][ i ] = c.getDoublePosition( 1 );
			p[ 2 ][ i ] = c.getDoublePosition( 2 );

			q[ 0 ][ i ] = c.getDoublePosition( 0 ) + vector.get( 0 ).getRealDouble();
			q[ 1 ][ i ] = c.getDoublePosition( 1 ) + vector.get( 1 ).getRealDouble();
			q[ 2 ][ i ] = c.getDoublePosition( 2 ) + vector.get( 2 ).getRealDouble();
		}

		Arrays.fill( w, 1.0 );
		AffineModel3D estimator = new AffineModel3D();
		estimator.fit( p, q, w );

		AffineTransform3D affine = new AffineTransform3D();
		affine.set( estimator.getMatrix( new double[12] ));
		return affine;
	}	

}
