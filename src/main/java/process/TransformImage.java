package process;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import io.IOHelper;
import net.imglib2.FinalInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import util.RenderUtil;

/**
 * Renders images transformed/registered.
 * 
 * Currently the final (implied) upsampling transform is hard-coded in : it
 * resmples up by a factor of 4 in x and y and 2 in z. After that, it upsamples
 * z again by a factor of (~ 2.02) to resample the images to a resolution of
 * 0.188268 um isotropic.
 * 
 * 
 * @author John Bogovic
 *
 */
@Command( version = "0.0.2-SNAPSHOT" )
public class TransformImage implements Callable< Void >, BiConsumer< File, File >
{

	@Option( names = { "-i", "--input" }, required = true, description = "Image file to transform" )
	private List< String > inputFiles = new ArrayList<>();

	@Option( names = { "-o", "--output" }, required = true, description = "Output file for transformed image" )
	private List< String > outputFiles = new ArrayList<>();

	@Option( names = { "-t", "--transform" }, required = false, description = "Transformation file." )
	private List< String > transformFiles = new ArrayList<>();

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

	@Option( names = { "-j", "--nThreads" }, required = false, description = "Number of rendering threads (default=1)" )
	private int nThreads = 1;

	@Option( names = { "-v", "--version" }, required = false, versionHelp = true, description = "Prints version information and exits." )
	private boolean version;

	private RealTransformSequence totalTransform;

	private FinalInterval renderInterval;

	final Logger logger = LoggerFactory.getLogger( TransformImage.class );

	public static void main( String... args )
	{
		CommandLine.call( new TransformImage(), args );
		System.exit(0);
	}

	/**
	 * Parses inputs to determine output size, resolution, etc.
	 * 
	 *  
	 */
	public void setup()
	{
		if( referenceImagePath != null && !referenceImagePath.isEmpty() && new File( referenceImagePath ).exists() )
		{
			IOHelper io = new IOHelper();
			ValuePair< long[], double[] > sizeAndRes = io.readSizeAndResolution( new File( referenceImagePath ));
			renderInterval = new FinalInterval( sizeAndRes.getA() );
			
			if ( outputResolution == null )
				outputResolution = sizeAndRes.getB();
		}
		
		if( outputSize != null && !outputSize.isEmpty() )
			renderInterval = RenderTransformed.parseInterval( outputSize );

		// contains the physical transformation
		RealTransformSequence physicalTransform = TransformReader.readTransforms( transformFiles );

		// we need to tack on the conversion from physical to pixel space first
		Scale resOutXfm = null;
		if ( outputResolution != null )
		{
			totalTransform = new RealTransformSequence();
			resOutXfm = new Scale( outputResolution );
			//System.out.println( resOutXfm );
			totalTransform.add( resOutXfm );
			totalTransform.add( physicalTransform );
		}
		else 
			totalTransform = physicalTransform;
	}

	public Void call()
	{
		assert outputFiles.size() == inputFiles.size();

		setup();

		for ( int i = 0; i < outputFiles.size(); i++ )
		{
			File input = new File( inputFiles.get( i ));
			File output = new File( outputFiles.get( i ));
			accept( input, output );
		}
		return null;
	}

	public void accept( File input, File output )
	{
		process( input, output );
	}

	public < T extends RealType< T > & NativeType< T > > void process( File input, File output )
	{
		logger.debug( "output resolution : " + Arrays.toString( outputResolution ));
		logger.debug( "output size       : " + Util.printInterval( renderInterval ));

		IOHelper io = new IOHelper();
		//RandomAccessibleInterval<T> rai = io.getRai();
		RealRandomAccessible< T > img = io.readPhysical( input, interpolation );
		IntervalView< T > imgXfm = Views.interval( 
				Views.raster( new RealTransformRandomAccessible<>( img, totalTransform ) ),
				renderInterval );

		logger.info( "allocating" );
		ImagePlusImgFactory< T > factory = new ImagePlusImgFactory<>( (T)io.getType() );
		ImagePlusImg< T, ? > imgout = factory.create( renderInterval );

		logger.info( "copying with " + nThreads + " threads." );
		try
		{
			//RenderUtil.copyToImageStackIterOrder( imgXfm, imgout, nThreads );
			RenderUtil.copyToImageStack( imgXfm, imgout, nThreads );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			logger.error( "copying failed" );
			return;
		}

		logger.info( "writing to: " + output );
		ImagePlus ipout = imgout.getImagePlus();
		ipout.getCalibration().pixelWidth = outputResolution[ 0 ];
		ipout.getCalibration().pixelHeight = outputResolution[ 1 ];
		ipout.getCalibration().pixelDepth = outputResolution[ 2 ];
		if ( io.getIp() != null )
			ipout.getCalibration().setUnit( io.getIp().getCalibration().getUnit() );

		IOHelper.write( ipout, output );
	}

}
