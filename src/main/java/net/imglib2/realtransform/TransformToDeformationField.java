package net.imglib2.realtransform;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.BdvOptions;
import io.DfieldIoHelper;
import io.IOHelper;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import process.RenderTransformed;
import util.FieldOfView;

@Command( version = "0.2.0-SNAPSHOT" )
public class TransformToDeformationField implements Callable<Void>
{
	@Option( names = { "-t", "--transform" }, required = false, description = "Transformation file." )
	private List< String > transformFiles = new ArrayList<>();

	@Option( names = { "-s", "--outputImageSize" }, required = false,
			description = "Size / field of view of image output in pixels.  Comma separated, e.g. \"200,250,300\". "
					+ "Overrides reference image."  )
	private String outputSize;

	@Option( names = { "-m", "--interval-min", "--offset" }, required = false, split=",",
			description = "Offset of the output interval in physical units. Overrides reference image." )
	private double[] intervalMin;

	@Option( names = { "-x", "--interval-max"  }, required = false, split=",",
			description = "Maximum coordinate of output interval.  Overrides reference, and outputImageSize parameters." )
	private double[] intervalMax;

	@Option( names = { "-r", "--output-resolution" }, required = false, split = ",", 
			description = "The resolution at which to write the output. Overrides reference image." )
	private double[] outputResolution;

	@Option( names = { "-f", "--reference" }, required = false, 
			description = "A reference image specifying the output size and resolution." )
	private String referenceImagePath;

	@Option( names = { "-o", "--output" }, required = true, description = "Output file for transformed image" )
	private String outputFile;

	@Option( names = { "-q", "--num-threads" }, required = false, description = "Number of threads." )
	private int nThreads = 1;

//	@Option( names = { "--double" }, required = false, description = "Use double precision for output, otherwise the output is 32 bit floats." )
//	private boolean useDouble;

	private Interval renderInterval;

	private RealTransformSequence totalTransform;

	private AffineGet pixelToPhysical;

	final Logger logger = LoggerFactory.getLogger( TransformToDeformationField.class );

	public static void main( String[] args )
	{
		CommandLine.call( new TransformToDeformationField(), args );
	}
	
	public void setup()
	{
		// use size / resolution if the only input transform is a dfield
		// ( and size / resolution is not given )

		Optional<ValuePair< long[], double[] >> sizeAndRes = Optional.empty();
		if ( referenceImagePath != null && !referenceImagePath.isEmpty() && new File( referenceImagePath ).exists() )
		{
			IOHelper io = new IOHelper();
			sizeAndRes = Optional.of( io.readSizeAndResolution( new File( referenceImagePath ) ));
		}
		else if( transformFiles.size() == 1 )
		{
			String transformFile = transformFiles.get( 0 );
			if( transformFile.contains( ".nrrd" ) || transformFile.contains( ".nii" ) || transformFile.contains( ".h5" ))
			{
				try
				{
					sizeAndRes = Optional.of( TransformReader.transformSpatialSizeAndRes( transformFile ));
				}
				catch ( Exception e )
				{
					e.printStackTrace();
				}

			}
		}

		int ndims = 3; // TODO generalize
		if ( outputSize != null && !outputSize.isEmpty() )
			renderInterval = RenderTransformed.parseInterval( outputSize );


		FieldOfView fov = FieldOfView.parse( ndims, sizeAndRes, 
				Optional.ofNullable( intervalMin ), 
				Optional.ofNullable( intervalMax ), 
				Optional.ofNullable( renderInterval ),
				Optional.ofNullable( outputResolution ));
		fov.updatePixelToPhysicalTransform();

		// contains the physical transformation
		RealTransformSequence physicalTransform = TransformReader.readTransforms( transformFiles );

		// we need to tack on the conversion from pixel to physical space first
		pixelToPhysical = fov.getPixelToPhysicalTransform();
		renderInterval = fov.getPixel();
		outputResolution = fov.getSpacing();

//		if ( outputResolution != null )
//			pixelToPhysical = new Scale( outputResolution );
//		else
//		{
//			double[] ones = new double[ physicalTransform.numSourceDimensions() ];
//			Arrays.fill( ones, 1.0 );
//			pixelToPhysical = new Scale( ones );
//		}

		logger.info( "render interval: " + Util.printInterval( renderInterval ) );
		logger.info( "pixelToPhysical: " + pixelToPhysical );

		totalTransform = physicalTransform;
	}
	
	public Void call() throws Exception
	{
		setup();
		process( totalTransform, new FloatType() );

		return null;
	}

	public < T extends RealType< T > & NativeType< T > > void process( RealTransform transform, T t ) throws Exception
	{
		assert renderInterval.numDimensions() == transform.numTargetDimensions() || renderInterval.numDimensions() == transform.numTargetDimensions() + 1;

		// make sure the output interval has an extra dimension
		// and if its a nrrd, the vector dimension has to be first,
		// otherwise it goes last
		if ( renderInterval.numDimensions() == transform.numTargetDimensions() )
		{

			long[] dims = new long[ transform.numTargetDimensions() + 1 ];

			if ( outputFile.endsWith( "nrrd" ) )
			{
				dims[ 0 ] = transform.numTargetDimensions();
				for ( int d = 0; d < transform.numTargetDimensions(); d++ )
				{
					dims[ d + 1 ] = renderInterval.dimension( d );
				}
			}
			else
			{
				for ( int d = 0; d < transform.numTargetDimensions(); d++ )
				{
					dims[ d ] = renderInterval.dimension( d );
				}
				dims[ transform.numTargetDimensions() ] = transform.numTargetDimensions();
			}

			FinalInterval renderIntervalNew = new FinalInterval( dims );
			renderInterval = renderIntervalNew;
		}

		logger.info( "allocating" );
		ImagePlusImgFactory< T > factory = new ImagePlusImgFactory<>( t );
		ImagePlusImg< T, ? > dfieldraw = factory.create( renderInterval );

		RandomAccessibleInterval< T > dfield = DfieldIoHelper.vectorAxisPermute( dfieldraw, 3, 3 );

		logger.info( "processing with " + nThreads + " threads." );
		transformToDeformationField( transform, dfield, pixelToPhysical, nThreads );

		logger.info( "writing" );
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
		logger.info( "done" );
	}

	public <T extends RealType<T> & NativeType<T>> void compare( 
			final RealTransform transform, final RandomAccessibleInterval<T> dfield )
	{
		DeformationFieldTransform<T> dfieldTransform = new DeformationFieldTransform<>( dfield );
		RealTransformSequence dfieldWithPix2Phys = new RealTransformSequence();
		dfieldWithPix2Phys.add( new Scale( outputResolution ));
		dfieldWithPix2Phys.add( dfieldTransform );

		RealPoint p = new RealPoint( transform.numSourceDimensions() );
		RealPoint qOrig = new RealPoint( transform.numTargetDimensions() );
		RealPoint qNew  = new RealPoint( transform.numTargetDimensions() );

		final int vecdim = dfield.numDimensions() - 1;
		IntervalIterator it = new IntervalIterator( Views.hyperSlice( dfield, vecdim, 0 ));
		while( it.hasNext() )
		{
			p.setPosition( it );
				
			transform.apply( p, qOrig );
			dfieldWithPix2Phys.apply( p, qNew );

		}
	}

	public static <T extends RealType<T> & NativeType<T>> void transformToDeformationField( 
			final RealTransform transform, final RandomAccessibleInterval<T> dfield, AffineGet pixelToPhysical, int nThreads )
	{
		if( nThreads == 1 )
		{
			transformToDeformationField( transform, dfield, pixelToPhysical );
			return;
		}

		assert ( dfield.numDimensions() == transform.numSourceDimensions() + 1 );

		final int vecdim = dfield.numDimensions() - 1;
		final int step = nThreads;

		ExecutorService exec = Executors.newFixedThreadPool( nThreads );
		ArrayList<Callable<Void>> jobs = new ArrayList<Callable<Void>>();
		for( int i = 0; i < nThreads; i++ )
		{
            final int start = i;
			jobs.add( new Callable<Void>()
			{
				@Override
				public Void call() throws Exception
				{
					final RealPoint p = new RealPoint( transform.numSourceDimensions() );
					final RealPoint q = new RealPoint( transform.numTargetDimensions() );
					final RealTransform transformCopy = transform.copy();
					final AffineGet pixelToPhysicalCopy = pixelToPhysical.copy();

					final RandomAccess< T > dfieldRa = dfield.randomAccess();
					final IntervalIterator it = new IntervalIterator( Views.hyperSlice( dfield, vecdim, 0 ));

					it.jumpFwd( start );
					while( it.hasNext() )
					{
						it.jumpFwd( step );
						pixelToPhysicalCopy.apply( it, p );

						// set position
						for( int d = 0; d < it.numDimensions(); d++ )
						{
							dfieldRa.setPosition( it.getIntPosition( d ), d );
						}

						// apply the transform
						transformCopy.apply( p, q );

						// set the result
						for( int d = 0; d < it.numDimensions(); d++ )
						{
							dfieldRa.setPosition( d, vecdim );
							dfieldRa.get().setReal( q.getDoublePosition( d ) - p.getDoublePosition( d ) );
						}
					}
					return null;
				}
			});
		}

		try
		{
			List< Future< Void > > futures = exec.invokeAll( jobs );
			for ( Future< Void > f : futures )
			{
				try
				{
					f.get();
				}
				catch ( ExecutionException e )
				{
					e.printStackTrace();
				}
			}

			exec.shutdown();

			// Wait a while for existing tasks to terminate
			if ( !exec.awaitTermination( 60, TimeUnit.MINUTES ) )
			{
				exec.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if ( !exec.awaitTermination( 60, TimeUnit.SECONDS ) )
					System.err.println( "Pool did not terminate" );
			}
		}
		catch ( InterruptedException e )
		{
			e.printStackTrace();
			// (Re-)Cancel if current thread also interrupted
			exec.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}
	
	public static <T extends RealType<T> & NativeType<T>> void transformToDeformationField( 
			final RealTransform transform, final RandomAccessibleInterval<T> dfield, final AffineGet pixelToPhysical )
	{
		assert ( dfield.numDimensions() == transform.numSourceDimensions() + 1 );

		final RealPoint p = new RealPoint( transform.numSourceDimensions() );
		final RealPoint q = new RealPoint( transform.numTargetDimensions() );
		
		int vecdim = dfield.numDimensions() - 1;
		final IntervalIterator it = new IntervalIterator( Views.hyperSlice( dfield, vecdim, 0 ));
		final RandomAccess< T > dfieldRa = dfield.randomAccess();
		while( it.hasNext() )
		{
			it.fwd();
			pixelToPhysical.apply( it, p );

			// set position
			for( int d = 0; d < it.numDimensions(); d++ )
			{
				dfieldRa.setPosition( it.getIntPosition( d ), d );
			}

			// apply the transform
			transform.apply( p, q );

			// set the result
			for( int d = 0; d < it.numDimensions(); d++ )
			{
				dfieldRa.setPosition( d, vecdim );
				dfieldRa.get().setReal( q.getDoublePosition( d ) - p.getDoublePosition( d ) );
			}
		}
	}

}
