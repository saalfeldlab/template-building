package process;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.spark.supplier.N5WriterSupplier;
import org.janelia.saalfeldlab.transform.io.TransformReader;

import bdv.export.Downsample;
import io.DfieldIoHelper;
import io.IOHelper;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorARGBFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.DeformationFieldTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.0.1-SNAPSHOT" )
public class TransformN5 implements Callable<Void>
{
	@Option( names = { "-n", "--n5-root" }, required = true, description = "N5 root directory" )
	private File n5Base;

	@Option( names = { "-o", "--output" }, required = true, description = "Output file" )
	private String outputDataset;

	@Option( names = { "-i", "--input" }, required = true, description = "Input dataset." )
	private String inDataset;

	@Option( names = { "-t", "--transform" }, required = false, description = "Transformation files.")
	private List<String> transformFiles;

	@Option( names = { "--interpolation" }, required = false, description = "Interpolation {LINEAR, NEAREST, LANCZOS}" )
	private String interpolation = "LINEAR";

	@Option( names = { "-s", "--outputImageSize" }, required = false,
			description = "Size / field of view of image output in pixels.  Comma separated, e.g. \"200,250,300\". "
					+ "Overrides reference image."  )
	private String outputSize;

	@Option( names = { "-r", "--output-resolution" }, required = false, split = ",", 
			description = "The resolution at which to write the output. Overrides reference image." )
	private double[] outputResolution;

	@Option( names = { "-f", "--reference" }, required = false, 
			description = "A reference image specifying the output size and resolution." )
	private String referenceImagePath;

	@Option( names = { "-offset-attribute"}, required = false, description = "Attribute storing offset.")
	private String offsetAttribute = "offset";

	@Option( names = { "-resolution-attribute"}, required = false, description = "Attribute storing resolution (pixel spacing).")
	private String resolutionAttribute = "resolution";

	private FinalInterval renderInterval;
	
	private AffineGet inputPixelToPhysical;

	private static final int MAX_PARTITIONS = 15000;

	public static void main(String[] args) throws Exception
	{
		/*
		 * TESTS
		 */
//		String dfieldTestPath = "/home/john/tmp/n5Transform/dfield.nrrd";
//		DeformationFieldTransform<FloatType> dfield = loadDfield( dfieldTestPath );
		
//		AffineTransform3D dfieldPix2Phys = dfieldPixelToPhysical(dfieldTestPath);
//		System.out.println( dfieldPix2Phys );
		
//		double[] res = new double[ 3 ];
//		dfield.apply( new double[]{ 320, 220, 14 } , res);
//		System.out.println( "res: "  + Arrays.toString( res ));

		CommandLine.call( new TransformN5(), args );

		System.exit(0);
	}

	
//	public static DeformationFieldTransform<FloatType> loadDfield( String dfieldPath ) throws Exception
//	{
//		DfieldIoHelper dio = new DfieldIoHelper();
//		RandomAccessibleInterval<FloatType> dfieldImg = dio.read( dfieldPath );
//
//		IOHelper io = new IOHelper();
//		ValuePair<long[], double[]> sizeAndRes = io.readSizeAndResolution( dfieldPath );
//		AffineTransform3D xfm = new AffineTransform3D();
//		xfm.set( sizeAndRes.getB()[ 0 ], 0, 0);
//		xfm.set( sizeAndRes.getB()[ 1 ], 1, 1);
//		xfm.set( sizeAndRes.getB()[ 2 ], 2, 2);
//
//		System.out.println( dfieldImg );
//
//		return new DeformationFieldTransform<>(
//				RealViews.affine(
//					Views.interpolate(
//						Views.extendBorder(Views.hyperSlice( dfieldImg, 3, 0 )),
//						new NLinearInterpolatorFactory<>()), 
//					xfm ),
//				RealViews.affine(
//					Views.interpolate(
//						Views.extendBorder(Views.hyperSlice( dfieldImg, 3, 1 )),
//						new NLinearInterpolatorFactory<>()), 
//					xfm ),
//				RealViews.affine(
//					Views.interpolate(
//						Views.extendBorder(Views.hyperSlice( dfieldImg, 3, 2 )),
//						new NLinearInterpolatorFactory<>()),
//					xfm ));
//	}
	
	/**
	 * Parses inputs to determine output size, resolution, etc.
	 * 
	 *  
	 */
	public void setup( final N5Reader n5 )
	{
		System.out.println("setup");
		if( referenceImagePath != null && !referenceImagePath.isEmpty() )
		{
			System.out.println("ref");
			IOHelper io = new IOHelper();
			io.setResolutionAttribute( resolutionAttribute );
			io.setOffsetAttribute( offsetAttribute );

			ValuePair< long[], double[] > sizeAndRes = io.readSizeAndResolution( new File( referenceImagePath ));
			renderInterval = new FinalInterval( sizeAndRes.getA() );
			
			System.out.println( "res: " + Arrays.toString( sizeAndRes.b ));
			
			if ( outputResolution == null )
				outputResolution = sizeAndRes.getB();
		}
		
		if( outputSize != null && !outputSize.isEmpty() )
			renderInterval = RenderTransformed.parseInterval( outputSize );

		inputPixelToPhysical = IOHelper.pixelToPhysicalN5( n5, inDataset, resolutionAttribute, offsetAttribute );
		System.out.println( "inputPixelToPhysical " + inputPixelToPhysical );
	}

	@Override
	public Void call() throws Exception
	{
		System.out.println("call");

		final N5WriterSupplier n5Supplier = () -> new N5FSWriter( n5Base.getAbsolutePath() );

		setup( n5Supplier.get() );

		
//		try ( final JavaSparkContext sparkContext = new JavaSparkContext( new SparkConf()
//				.setAppName( "TransformN5" )
//				.set( "spark.serializer", "org.apache.spark.serializer.KryoSerializer" )
//			) )
//		{

			transform(
//					sparkContext,
					null,
					n5Supplier,
					inDataset,
					inputPixelToPhysical,
					outputDataset,
					Intervals.dimensionsAsLongArray( renderInterval ),
					outputResolution,
					new double[ 3 ],
					interpolation,
					transformFiles,
					null );

//		}
		return null;
	}

	public static <T extends RealType<T> & NativeType<T>, S extends RealType<S> & NativeType<S> > void transform(
			final JavaSparkContext sparkContext,
			final N5WriterSupplier n5Supplier,
			final String inputDatasetPath,
			final AffineGet inputPixelToPhysical,
			final String outputDatasetPath,
			final long[] outputDimensions,
			final double[] outputResolutions,
			final double[] outputOffset,
			final String interpolation,
			final List<String> transformFiles,
			final S defaultType
			) throws IOException
	{
		transform( sparkContext, n5Supplier, 
				inputDatasetPath, inputPixelToPhysical,
				outputDatasetPath, outputDimensions, outputResolutions, outputOffset,
				interpolation, transformFiles, defaultType, null );
	}
	
	@SuppressWarnings("unchecked")
	public static 
		<T extends RealType<T> & NativeType<T>, S extends RealType<S> & NativeType<S>, R extends RealType<R> & NativeType<R>> 
		void transform(
			final JavaSparkContext sparkContext,
			final N5WriterSupplier n5Supplier,
			final String inputDatasetPath,
			final AffineGet inputPixelToPhysical,
			final String outputDatasetPath,
			final long[] outputDimensions,
			final double[] outputResolutions,
			final double[] outputOffset,
			final String interpolation,
			final List<String> transformFiles,
			final S defaultType,
			final int[] blockSize ) 
		throws IOException
	{
		final N5Writer n5 = n5Supplier.get();
		if ( !n5.datasetExists( inputDatasetPath ) )
			throw new IllegalArgumentException( "Input N5 dataset " + inputDatasetPath + " does not exist" );
		if ( n5.datasetExists( outputDatasetPath ) )
			throw new IllegalArgumentException( "Output N5 dataset " + outputDatasetPath + " already exists" );

		final DatasetAttributes inputAttributes = n5.getDatasetAttributes( inputDatasetPath );
		final int dim = inputAttributes.getNumDimensions();

		if ( Arrays.stream( outputDimensions ).min().getAsLong() < 1 )
			throw new IllegalArgumentException( "Degenerate output dimensions: " + Arrays.toString( outputDimensions ) );

		final int[] outputBlockSize = blockSize != null ? blockSize : inputAttributes.getBlockSize();
		n5.createDataset(
				outputDatasetPath,
				outputDimensions,
				outputBlockSize,
				inputAttributes.getDataType(),
				inputAttributes.getCompression()
			);

		final CellGrid outputCellGrid = new CellGrid( outputDimensions, outputBlockSize );
		final long numDownsampledBlocks = Intervals.numElements( outputCellGrid.getGridDimensions() );
		final List< Long > blockIndexes = LongStream.range( 0, numDownsampledBlocks ).boxed().collect( Collectors.toList() );

//		sparkContext.parallelize( blockIndexes, Math.min( blockIndexes.size(), MAX_PARTITIONS ) ).foreach( blockIndex ->
//		{

		for( Long blockIndex : blockIndexes )
		{
			System.out.println("working on block: " + blockIndex );

			final CellGrid cellGrid = new CellGrid( outputDimensions, outputBlockSize );
			final long[] blockGridPosition = new long[ cellGrid.numDimensions() ];
			cellGrid.getCellGridPositionFlat( blockIndex, blockGridPosition );

			long[] targetMin = new long[ dim ], targetMax = new long[ dim ];
			final int[] cellDimensions = new int[ dim ];
			cellGrid.getCellDimensions( blockGridPosition, targetMin, cellDimensions );
			for ( int d = 0; d < dim; ++d )
			{
				targetMax[ d ] = targetMin[ d ] + cellDimensions[ d ] - 1;
			}

			final Interval targetInterval = new FinalInterval( targetMin, targetMax );

			/*
			 * Load the transform
			 */
			RealTransformSequence totalTransform = new RealTransformSequence();

			// contains the physical transformation
			RealTransformSequence physicalTransform = TransformReader.readTransforms( transformFiles );
			// we need to tack on the conversion from physical to pixel space first
			Scale resOutXfm = null;
			if ( outputResolutions != null )
			{
				resOutXfm = new Scale( outputResolutions );
				//System.out.println( resOutXfm );
				totalTransform.add( resOutXfm );
				totalTransform.add( physicalTransform );
			}
			else 
				totalTransform = physicalTransform;

			totalTransform.add( inputPixelToPhysical.inverse());
			
			/*
			 * Get the source and transform it
			 */
			final N5Writer n5Local = n5Supplier.get();
			final RandomAccessibleInterval< T > source = N5Utils.open( n5Local, inputDatasetPath );
	

			/*
			 * TEST WITH NO TRANSFORM
			 */
//			S defaultValue = (S)Util.getTypeFromInterval( source );
//			final RandomAccessibleInterval< S > targetBlockRaw = new ArrayImgFactory<>( defaultValue ).create( targetInterval );
//			IntervalView<S> targetBlock = Views.offset( targetBlockRaw, targetMin );
//			final Interval sourceInterval = new FinalInterval( targetMin, targetMax );
//			final RandomAccessibleInterval< T > sourceBlock = Views.offsetInterval( source, sourceInterval );
//			LoopBuilder.setImages( sourceBlock, targetBlock ).forEachPixel( (s,t) -> t.setReal( s.getRealDouble() ));


			S defaultValue;
			if( defaultType == null )
				defaultValue = (S)Util.getTypeFromInterval( source );
			else
				defaultValue = defaultType;

			RealTransformRandomAccessible<T,?> imgXfm = new RealTransformRandomAccessible<>( 
							Views.interpolate( Views.extendZero( source ), RenderTransformed.getInterpolator( interpolation, source )), 
							totalTransform );

			/*
			 * Build a block and copy into it
			 */
			final RandomAccessibleInterval< S > targetBlockRaw = new ArrayImgFactory<>( defaultValue ).create( targetInterval );
			IntervalView<S> targetBlock = Views.translate( targetBlockRaw, targetMin );

			System.out.println("tgt itvl : " + Util.printInterval( targetBlock ));
			
			// copy
			RealTransformRealRandomAccessible<T,?>.RealTransformRealRandomAccess srcAccess = imgXfm.realRandomAccess();
			Cursor<S> c = Views.iterable( targetBlock ).localizingCursor();

			System.out.println("copy");
			while( c.hasNext() )
			{
				c.fwd();
				srcAccess.setPosition( c );
				c.get().setReal( srcAccess.get().getRealDouble() );
			}

			// write the block
			System.out.println("write");
			N5Utils.saveNonEmptyBlock( targetBlock, n5Local, outputDatasetPath, blockGridPosition, defaultValue );

			System.out.println("done\n");
		}
//		} );	
	}

}
