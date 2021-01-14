package process;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.spark.supplier.N5WriterSupplier;
import org.janelia.saalfeldlab.transform.io.TransformReader;

import io.IOHelper;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import util.FieldOfView;

@Command( version = "0.0.1-SNAPSHOT" )
public class TransformN5 implements Callable<Void>, Serializable
{
	private static final long serialVersionUID = 2341611842505632347L;

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

	@Option( names = { "-p", "--image-pixel-to-physical" }, required = false, split = ",", 
			description = "The resolution at which to write the output. Overrides reference image." )
	private double[] imagePixelToPhysical;

	@Option( names = { "-f", "--reference" }, required = false, 
			description = "A reference image specifying the output size and resolution." )
	private String referenceImagePath;

//	@Option( names = { "-offset-attribute"}, required = false, description = "Attribute storing offset.")
//	private String offsetAttribute = "offset";
//
//	@Option( names = { "-resolution-attribute"}, required = false, description = "Attribute storing resolution (pixel spacing).")
//	private String resolutionAttribute = "resolution";

	private transient FinalInterval renderInterval;
	
	private transient AffineGet inputPixelToPhysical;

	private static final int MAX_PARTITIONS = 15000;

	public static void main(String[] args) throws Exception
	{
		TransformN5 alg = new TransformN5();
		CommandLine.call( alg, args );

		System.exit(0);
	}

	/**
	 * Parses inputs to determine output size, resolution, etc.
	 * 
	 *  
	 */
	public void setup( final N5Reader n5 )
	{



		ValuePair< long[], double[] > sizeAndRes = null;
		if( referenceImagePath != null && !referenceImagePath.isEmpty() )
		{
			IOHelper io = new IOHelper();
			sizeAndRes = io.readSizeAndResolution( new File( referenceImagePath ));
			renderInterval = new FinalInterval( sizeAndRes.getA() );

			if ( outputResolution == null )
				outputResolution = sizeAndRes.getB();
		}

		FieldOfView fov = FieldOfView.parse( 3, sizeAndRes, 
				null,
				null,
				renderInterval, 
				outputResolution );

        if( imagePixelToPhysical != null )
        {
            AffineTransform3D tmp = new AffineTransform3D();
            tmp.set( imagePixelToPhysical );
            inputPixelToPhysical = tmp;
        }
        else
        {
            inputPixelToPhysical = IOHelper.pixelToPhysicalN5( n5, inDataset );
        }
	}

	@Override
	public Void call() throws Exception
	{
		final N5WriterSupplier n5Supplier = () -> new N5FSWriter( n5Base.getAbsolutePath() );

		setup( n5Supplier.get() );

		try ( final JavaSparkContext sparkContext = new JavaSparkContext( new SparkConf()
				.setAppName( "TransformN5" )
				.set( "spark.serializer", "org.apache.spark.serializer.KryoSerializer" )
			) )
		{

			transform(
					sparkContext,
					n5Supplier,
					inDataset,
					inputPixelToPhysical.getRowPackedCopy(),
					outputDataset,
					Intervals.dimensionsAsLongArray( renderInterval ),
					outputResolution,
					new double[ 3 ],
					interpolation,
					transformFiles,
					null );
		}
		return null;
	}

	public static <T extends RealType<T> & NativeType<T>, S extends RealType<S> & NativeType<S> > void transform(
			final JavaSparkContext sparkContext,
			final N5WriterSupplier n5Supplier,
			final String inputDatasetPath,
			final double[] affineParams,
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
				inputDatasetPath, affineParams,
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
			final double[] affineParams,
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

		sparkContext.parallelize( blockIndexes, Math.min( blockIndexes.size(), MAX_PARTITIONS ) ).foreach( blockIndex ->
		{

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

			// contains the physical transformation
			final RealTransformSequence physicalTransform = TransformReader.readTransforms( transformFiles );

			// we need to tack on the conversion from physical to pixel space first
			Scale resOutXfm = null;
			final RealTransformSequence totalTransform;
			if ( outputResolutions != null )
			{
				totalTransform = new RealTransformSequence();
				resOutXfm = new Scale( outputResolutions );
				totalTransform.add( resOutXfm );
				totalTransform.add( physicalTransform );
			}
			else 
			{
				totalTransform = physicalTransform;
			}

			totalTransform.add( paramsToAffine( affineParams ).inverse() );
			
			/*
			 * Get the source and transform it
			 */
			final N5Writer n5Local = n5Supplier.get();
			final RandomAccessibleInterval< T > source = N5Utils.open( n5Local, inputDatasetPath );


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

			// copy
			RealTransformRealRandomAccessible<T,?>.RealTransformRealRandomAccess srcAccess = imgXfm.realRandomAccess();
			Cursor<S> c = Views.iterable( targetBlock ).localizingCursor();

			while( c.hasNext() )
			{
				c.fwd();
				srcAccess.setPosition( c );
				c.get().setReal( srcAccess.get().getRealDouble() );
			}

			// write the block
			N5Utils.saveNonEmptyBlock( targetBlock, n5Local, outputDatasetPath, blockGridPosition, defaultValue );
		} );
	}

	public static AffineGet paramsToAffine( final double[] affineParams )
	{
		int N = affineParams.length;
		if ( N == 2 )
		{
			AffineTransform affine = new AffineTransform( 1 );
			affine.set(affineParams);
			return affine;
		}
		else if ( N == 6 )
		{
			AffineTransform2D affine = new AffineTransform2D();
			affine.set(affineParams);
			return affine;
		}
		else if ( N == 12 )
		{
			AffineTransform3D affine = new AffineTransform3D();
			affine.set(affineParams);
			return affine;
		}

		return null;
	}

}
