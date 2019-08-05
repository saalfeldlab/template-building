package transforms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import ij.IJ;
import ij.ImagePlus;
import io.AffineImglib2IO;
import io.DfieldIoHelper;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import loci.formats.FormatException;
import mpicbg.models.AffineModel3D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.SubsampleIntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.GenericComposite;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.1.1-SNAPSHOT")
public class AffineFromDisplacement<T> implements Callable< Void >
{
	public static final String FLAG_SKIP_WARP  = "--skip-warp";
	
	
	@Option( names = { "-i", "--input" }, required = true, description = "Input displacement field" )
	private List< String > inputFiles = new ArrayList<>();

	@Option( names = { "-o", "--out-suffix" }, required = false, description = "Output displacement field suffix" )
	private String dfieldSuffix = "_noAffine.nrrd";

	@Option( names = { "-a", "--affine-suffix" }, required = false, description = "Output displacement field suffix" )
	private String affineSuffix = "_affine.txt";

	@Option( names = { "-s", "--sample-step" }, required = false, description = "Sampling step" )
	private int step = 4;

	@Option( names = { "--do-warp" }, required = false, description = "Write displacementfield with affine part removed" )
	private Boolean doWarp = true;

	@Option( names = { "--filter" }, required = false, description = "Filter: ignore (0,0,0) vectors" )
	private Boolean filter = true;

	public AffineFromDisplacement() { }
	
	@SuppressWarnings( "unchecked" )
	public static void main( String[] args )
	{
		CommandLine.call( new AffineFromDisplacement(), args );
		System.exit(0);
	}

	public Void call()
	{

		DfieldIoHelper dfieldIo = new DfieldIoHelper();
		for( String dfieldPath : inputFiles )
		{
			
//			File inFile = new File( dfieldPath );
//			System.out.println( inFile.getName() );
			

			String outputPrefix = dfieldPath.substring( 0, dfieldPath.lastIndexOf( '.' ));
			System.out.println( "out prefix: " + outputPrefix );
			
			ANTSDeformationField dfield = null;
			try
			{
				dfield = dfieldIo.readAsDeformationField( dfieldPath );
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				continue;
			}
			
			AffineTransform3D affine;
			try
			{
				affine = estimateAffine( dfield.getImg(), dfield.getResolution(), step, true );
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
				removeAffineComponentAndWrite( 
						new File( outputPrefix + dfieldSuffix),
						affine, dfield.getImg(), Util.getTypeFromInterval( dfield.getImg() ),
						dfield.getDefField(), dfield.getResolution(), 
						filter );
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


	public static void oldmain( String[] args ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		int argIdx = 0;

		boolean doWarp = true;
		if( args[ argIdx ].equals( FLAG_SKIP_WARP ))
		{
			doWarp = false;
			argIdx++;
		}

		String outPath = args[ argIdx++ ];
		String filePath = args[ argIdx++ ];

		int step = Integer.parseInt( args[ argIdx++ ] );

		ImagePlus imp = null;
		double[] resolution = new double[3];
		Img< FloatType > displacement = null;
		if( filePath.endsWith( "nii" ))
		{
			try
			{
				imp = NiftiIo.readNifti( new File( filePath ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			imp = IJ.openImage( filePath );
		}
		resolution[ 0 ] = imp.getCalibration().pixelWidth;
		resolution[ 1 ] = imp.getCalibration().pixelHeight;
		resolution[ 2 ] = imp.getCalibration().pixelDepth;

		displacement = ImageJFunctions.convertFloat( imp );

		if ( displacement == null )
		{
			System.err.println( "Failed to load displacement field" );
			return;
		}

		System.out.println(
				"DISPLACEMENT INTERVAL: " + Util.printInterval( displacement ) );
		

		AffineTransform3D affine = estimateAffine( displacement, resolution, step, true );
		System.out.println( affine );

		try
		{
			AffineImglib2IO.writeXfm( new File( outPath + "_affine.txt" ), affine );
		} catch ( IOException e )
		{
			e.printStackTrace();
		}

		if ( doWarp )
		{
			System.out.println( "removing affine part from warp" );
			removeAffineComponentLegacy( displacement, affine, true );
			System.out.println( "saving warp" );


//				IJ.save( ImageJFunctions.wrap( displacement, "warp" ),
//						outPath + "_warp.tif" );

			Nifti_Writer.writeDisplacementField3d( imp, new File( outPath + "_warp.nii") );
//			}
		}
	}

	/**
	 * Removes the affine part from a displacement field 
	 * @param displacementField the displacement field 
	 * @param affine the affine part 
	 * @param filter if true, ignores vectors in the field equal to (0,0,0)
	 */
	public static <S extends RealType<S>, T extends RealType< T > & NativeType<T>> void removeAffineComponentAndWrite(
			File outFile,
			AffineTransform3D affine,
			Interval intervalIn,
			T t,
			RealRandomAccessible< S > displacementFieldInPhysical,
			double[] outputResolution,
			boolean filter )
	{
		Interval intervalOut = DfieldIoHelper.dfieldIntervalVectorFirst3d( intervalIn );
		Scale3D pixelToPhysical = new Scale3D( outputResolution );

		ImagePlusImgFactory< T > factory = new ImagePlusImgFactory<>( t );
		ImagePlusImg< T, ? > dfieldraw = factory.create( intervalOut );
		try
		{

			RandomAccessibleInterval< T > dfield = DfieldIoHelper.vectorAxisPermute( dfieldraw, 3, 3 );
			removeAffineComponent( affine, dfield, displacementFieldInPhysical, pixelToPhysical, filter );

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
			AffineTransform3D affine,
			RandomAccessibleInterval< T > displacementFieldOut,
			RealRandomAccessible< S > displacementFieldInPhysical,
			AffineGet pixelToPhysical,
			boolean filter )
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
			
//			System.out.println( "c: " + Util.printCoordinates( c ));
//			System.out.println( "x: " + Util.printCoordinates( x ));
//			System.out.println( "y: " + Util.printCoordinates( y ));
//			System.out.println( "z: " + Util.printCoordinates( affineResult ));
			
			// set the output
			for ( int i = 0; i < 3; i++ )
				outputVector.get( i ).setReal(
						 affineResult.getDoublePosition( i ) - physicalOutputPoint.getDoublePosition( i ) );

		}
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

	public static <T extends RealType< T >> void removeAffineComponentWRONG(
			RandomAccessibleInterval< T > displacementField, AffineTransform3D affine,
			boolean filter )
	{

		CompositeIntervalView< T, ? extends GenericComposite< T > > vectorField = Views
				.collapse( displacementField );
		Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( vectorField )
				.cursor();

		RealPoint affineResult = new RealPoint( 3 );
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

			affine.apply( c, affineResult );

			for ( int i = 0; i < 2; i++ )
				vector.get( i ).setReal(
						c.getDoublePosition( i ) + vector.get( i ).getRealDouble()
								- affineResult.getDoublePosition( i ) );
		}
	}

	public static <T extends RealType< T >> AffineTransform3D estimateAffine(
			final RandomAccessibleInterval< T > displacementField,
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

		Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( vectorField ).cursor();

		int i = 0;
		ArrayList<PointMatch> matches = new ArrayList<PointMatch>( (int)Intervals.numElements( vectorField ));
		int numSkipped = 0;
		while( c.hasNext())
		{
			GenericComposite< T > vector = c.next();

			if( filter &&
					vector.get( 0 ).getRealDouble() == 0  && 
					vector.get( 1 ).getRealDouble() == 0  && 
					vector.get( 2 ).getRealDouble() == 0 )
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

