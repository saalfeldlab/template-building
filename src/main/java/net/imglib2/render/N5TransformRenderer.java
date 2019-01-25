package net.imglib2.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import net.imglib2.Interval;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.bdv.N5ExportMetadata;
import org.janelia.saalfeldlab.n5.bdv.N5ExportMetadataReader;
import org.janelia.saalfeldlab.n5.ij.N5ImagePlusMetadata;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.utility.parse.ParseUtils;

import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import process.RenderTransformed;

/**
 * Provides a main method for applying a spatial transform block-wise over an N5 file.
 *
 */
public class N5TransformRenderer {

	public static void main(String[] args) throws Exception
	{

		String n5in = args[ 0 ];
		String n5out = args[ 1 ];
		String outputIntervalF = args[ 2 ];
		
		// LOAD THE IMAGE
		String[] n5AndDataset = n5in.split(":");
		N5Reader n5reader = new N5FSReader( n5AndDataset[ 0 ]);
		String datasetIn = n5AndDataset[ 1 ];

		String[] outn5AndDataset = n5out.split(":");
		N5Writer n5writer = new N5FSWriter( outn5AndDataset[ 0 ] );
		String datasetOut = outn5AndDataset[ 1 ];

		System.out.println( "in: " + Arrays.toString( n5AndDataset ));
		System.out.println( "out: " + Arrays.toString( outn5AndDataset ));

		if( !n5reader.datasetExists(datasetIn))
		{
			System.err.println( "dataset " + datasetIn + " does not exist in " + n5AndDataset[0]);
			return; 
		}
		
		long[] datasetDimensions = n5reader.getDatasetAttributes(datasetIn).getDimensions();
		FinalInterval datasetInterval = new FinalInterval( datasetDimensions );

		VoxelDimensions resolutions;
		double rx = 1.0;
		double ry = 1.0;
		double rz = 1.0;
		try {

			double[] res = N5ImagePlusMetadata.getPixelSpacing(n5reader, datasetIn);
			rx = res[ 0 ];
			ry = res[ 1 ];
			rz = res[ 2 ];

		} catch (IOException e) {
			e.printStackTrace();
		}

		AffineTransform3D resInXfm = null;
		if( rx == 0 ){
			rx = 1.0;
			System.err.println( "WARNING: rx = 0 setting to 1.0" );
		}
		if( ry == 0 ){
			ry = 1.0;
			System.err.println( "WARNING: ry = 0 setting to 1.0" );
		}
		if( rz == 0 ){
			rz = 1.0;
			System.err.println( "WARNING: rz = 0 setting to 1.0" );
		}

		if( rx != 1.0  || ry != 1.0 || rz != 1.0 )
		{
			resInXfm = new AffineTransform3D();
			resInXfm.set( 	rx, 0.0, 0.0, 0.0, 
					  		0.0, ry, 0.0, 0.0, 
					  		0.0, 0.0, rz, 0.0 );
			System.out.println( "transform for input resolutions : " + resInXfm );
		}

		FinalInterval renderInterval = null;
		if( outputIntervalF.equals("infer"))
		{
			System.out.println("trying to infer output interval");
			renderInterval = RenderTransformed.inferOutputInterval( args, datasetInterval, new double[]{ rx, ry, rz });
			System.out.println("Rendering to interval: " + Util.printInterval( renderInterval ));
		}
		else
		{
			renderInterval = RenderTransformed.parseInterval( outputIntervalF );
		}
		
		boolean success = doIt( n5reader, datasetIn, n5writer, datasetOut, resInXfm, args, renderInterval );
		System.out.println(" finished successfully: " + success );

	}

	public static boolean doIt(
			N5Reader n5In,
			String datasetIn,
			N5Writer n5Out,
			String datasetOut,
			AffineTransform3D resInXfm,
			String[] args,
			FinalInterval renderInterval ) throws Exception
	{
		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();

		int nThreads = 8;

		int i = 3;
		double[] outputResolution = null;
		while( i < args.length )
		{
			boolean invert = false;
			if( args[ i ].equals( "-i" ))
			{
				invert = true;
				i++;
			}

			if( args[ i ].equals( "-q" ))
			{
				i++;
				nThreads = Integer.parseInt( args[ i ] );
				i++;
				System.out.println( "argument specifies " + nThreads + " threads" );
				continue;
			}

			//if( args[ i ].equals( "-t" ))
			//{
			//	i++;
			//	String interpArg = args[ i ].toLowerCase();
			//	if( interpArg.equals( "nearest" ) || interpArg.equals( "near"))
			//		 interp =  new NearestNeighborInterpolatorFactory<T>();

			//	i++;
			//	System.out.println( "using" + interp + " interpolation" );
			//	continue;
			//}

			if( args[ i ].equals( "-r" ))
			{
				i++;
				outputResolution = ParseUtils.parseDoubleArray( args[ i ] );
				i++;

				System.out.println( "output Resolution " + Arrays.toString( outputResolution ));
				continue;
			}

			if( invert )
				System.out.println( "loading transform from " + args[ i ] + " AND INVERTING" );
			else
				System.out.println( "loading transform from " + args[ i ]);

			InvertibleRealTransform xfm = RenderTransformed.loadTransform( args[ i ], invert );

			if( xfm == null )
			{
				System.err.println("  failed to load transform ");
				System.exit( 1 );
			}

			totalXfm.add( xfm );
			i++;
		}

		return run( n5In, datasetIn, n5Out, datasetOut, resInXfm, renderInterval, totalXfm, outputResolution, nThreads );
	}
	
	public static boolean run(
			final N5Reader n5reader,
			final String datasetIn,
			N5Writer n5writer,
			final String datasetOut,
			AffineTransform3D resInXfm,
			FinalInterval renderInterval,
			InvertibleRealTransform xfm,
			double[] outputResolutions,
			int nThreads ) throws IOException
	{
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();

		if( resInXfm != null )
			totalXfm.add( resInXfm );

		totalXfm.add( xfm );
		
		AffineTransform3D resOutXfm;
		if( outputResolutions != null )
		{
			resOutXfm = new AffineTransform3D();
			resOutXfm.set( 	outputResolutions[ 0 ], 0.0, 0.0, 0.0, 
					  		0.0, outputResolutions[ 1 ], 0.0, 0.0, 
					  		0.0, 0.0, outputResolutions[ 2 ], 0.0 );
			totalXfm.add( resOutXfm.inverse() );
		}

		// TODO expose this as an option
		// block size identical to input block size
		DatasetAttributes attributes = n5reader.getDatasetAttributes(datasetIn);
		long[] outputdims = Intervals.dimensionsAsLongArray(renderInterval);
		int[] blockSize = attributes.getBlockSize();
		DataType dataType = attributes.getDataType();

		n5writer.createDataset(
				datasetOut, 
				outputdims,
				blockSize, 
				dataType, 
				new GzipCompression());

		// set up output grid indices
		final CellGrid grid = new CellGrid( outputdims, blockSize );
		final long numOutputBlocks = Intervals.numElements( grid.getGridDimensions() );
		final List< Long > outputBlockIndexes = LongStream.range( 0, numOutputBlocks ).boxed().collect( Collectors.toList() );
		
		ExecutorService executors = Executors.newFixedThreadPool( nThreads );
		
		List<Callable<Boolean>> jobs = new ArrayList<>();

		for( long i : outputBlockIndexes )
		{
			executors.submit( new Callable<Boolean>(){
				@Override
				public Boolean call() throws Exception {
					
					//transformAndWriteBlock( i, n5reader, datasetIn, n5writer, datasetOut, totalXfm, N5Utils.forDataType( dataType ));
					transformAndWriteBlock( i, n5reader, datasetIn, n5writer, datasetOut, totalXfm );
                    
					return true;
				}
			});
		}
		
		try {
			List<Future<Boolean>> futures = executors.invokeAll( jobs );
			executors.shutdown(); // wait for all jobs to finish 
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	
//		System.out.println("transforming");
//		IntervalView< T > imgHiXfm = Views.interval( 
//				Views.raster( 
//					RealViews.transform(
//							Views.interpolate( Views.extendZero( baseImg ), interp ),
//							totalXfm )),
//				renderInterval );
//
//		IntervalView< T > outTranslated = Views.translate( out,
//				renderInterval.min( 0 ),
//				renderInterval.min( 1 ),
//				renderInterval.min( 2 ));

		return true;
	}	
	
	public static <T extends RealType<T> & NativeType<T>> void transformAndWriteBlock(
			long i,
			final N5Reader n5reader,
			final String datasetIn,
			N5Writer n5writer,
			final String datasetOut,
			InvertibleRealTransform totalXfm )
	{
		InterpolatorFactory<T,RandomAccessible<T>> interp =  new NLinearInterpolatorFactory<T>();

		DatasetAttributes attributes = null;
		RandomAccessibleInterval<T> img = null;
		try {

			img = N5Utils.open( n5reader, datasetIn );
			attributes = n5reader.getDatasetAttributes(datasetIn);

		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		long[] outputdims = attributes.getDimensions();
		int[] blockSize = attributes.getBlockSize();

		final CellGrid grid = new CellGrid( outputdims, blockSize );
		long[] outputBlockGridPosition = new long[ img.numDimensions() ];
		grid.getCellGridPositionFlat( i, outputBlockGridPosition );

		final long[] outputBlockMin = new long[ grid.numDimensions() ], outputBlockMax = new long[ grid.numDimensions() ];
		final int[] outputBlockDimensions = new int[ grid.numDimensions() ];
		grid.getCellDimensions( outputBlockGridPosition, outputBlockMin, outputBlockDimensions );
		for ( int d = 0; d < grid.numDimensions(); ++d )
			outputBlockMax[ d ] = outputBlockMin[ d ] + outputBlockDimensions[ d ] - 1;

		final Interval outputBlockInterval = new FinalInterval( outputBlockMin, outputBlockMax );
		System.out.println( "output interval: " + Util.printInterval( outputBlockInterval ));

		IntervalView< T > imgHiXfm = Views.interval( 
				Views.raster( 
					RealViews.transform(
							Views.interpolate( Views.extendZero( img ), interp ),
							totalXfm )),
				outputBlockInterval );

		try {

			N5Utils.saveBlock( Views.zeroMin( imgHiXfm ), n5writer, datasetOut, outputBlockGridPosition);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
