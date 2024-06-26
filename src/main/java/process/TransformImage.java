package process;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5URI;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.Common;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.graph.TransformGraph;
import org.janelia.saalfeldlab.n5.universe.metadata.ome.ngff.v05.graph.TransformPath;
import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import io.IOHelper;
import net.imglib2.FinalInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.RealTransform;
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
 * @param <T>
 *
 */
@Command( version = "0.2.0-SNAPSHOT" )
public class TransformImage<T extends RealType<T> & NativeType<T>> implements Callable< Void >, BiConsumer< String, String >
{

	@Option( names = { "-i", "--input" }, required = true, description = "Image file to transform" )
	private List< String > inputFiles = new ArrayList<>();

	@Option( names = { "-o", "--output" }, required = true, description = "Output file for transformed image" )
	private List< String > outputFiles = new ArrayList<>();

	@Option( names = { "-t", "--transform" }, required = false, description = "Transformation file." )
	private List< String > transformFiles = new ArrayList<>();

	@Option(names = {"-ct", "--coordinate-transforms"}, required = false, description = "Path to coordinate transformations.")
	private String coordinateTransformPath;

	@Option(names = {"-is", "--input-coordinate-system"}, required = false, description = "")
	private String inputCoordinateSystem;

	@Option(names = {"-os", "--output-coordinate-system"}, required = false, description = "")
	private String outputCoordinateSystem;

	@Option( names = { "--interpolation" }, required = false, description = "Interpolation {LINEAR, NEAREST, BSPLINE, LANCZOS}" )
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

	@Option( names = { "-e", "--extend-option" }, required = false,
			description = "Extending out-of-bounds options. [boundary, mirror, mirror2, (numerical-value)]" )
	private String extendOption = "0";

	@Option( names = { "-j", "--nThreads" }, required = false, description = "Number of rendering threads (default=1)" )
	private int nThreads = 1;

	@Option( names = { "-v", "--version" }, required = false, versionHelp = true, description = "Prints version information and exits." )
	private boolean version;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Print this help message" )
	private boolean help;

	private RealTransform totalTransform;

	private FinalInterval renderInterval;

	final Logger logger = LoggerFactory.getLogger( TransformImage.class );

	@SuppressWarnings("rawtypes")
	public static void main( String... args )
	{
		new CommandLine(new TransformImage()).execute(args);
		System.exit(0);
	}

	/**
	 * Parses inputs to determine output size, resolution, etc.
	 */
	public void setup()
	{
		if( referenceImagePath != null && !referenceImagePath.isEmpty() && new File( referenceImagePath ).exists() )
		{
			final IOHelper io = new IOHelper();
			final ValuePair< long[], double[] > sizeAndRes = io.readSizeAndResolution( new File( referenceImagePath ));
			renderInterval = new FinalInterval( sizeAndRes.getA() );

			if ( outputResolution == null )
				outputResolution = sizeAndRes.getB();
		}

		if( outputSize != null && !outputSize.isEmpty() )
			renderInterval = RenderTransformed.parseInterval( outputSize );

		// contains the physical transformation
		final RealTransform physicalTransform = readTransforms();

		// we need to tack on the conversion from physical to pixel space first
		Scale resOutXfm = null;
		if ( outputResolution != null )
		{
			final RealTransformSequence transformSequence = new RealTransformSequence();
			resOutXfm = new Scale(outputResolution);
			transformSequence.add(resOutXfm);
			transformSequence.add(physicalTransform);
			totalTransform = transformSequence;
		}
		else
			totalTransform = physicalTransform;
	}

	private RealTransform readTransforms() {
		final RealTransform physicalTransform;
		if( transformFiles != null && transformFiles.size() > 0 )
			return TransformReader.readTransforms(transformFiles);
		else if (coordinateTransformPath != null && inputCoordinateSystem != null && outputCoordinateSystem != null) {
			return readTransformFromGraph();
		}
		return new RealTransformSequence();
	}

	private RealTransform readTransformFromGraph() {

		N5URI n5uri;
		try {
			n5uri = new N5URI(coordinateTransformPath);
		} catch (final URISyntaxException e) {
			return null;
		}

		final N5Reader n5 = new N5Factory().gsonBuilder(Common.gsonBuilder()).openReader(n5uri.getContainerPath());
		final TransformGraph graph = Common.openGraph(n5, n5uri.getGroupPath());

		final HashSet<String> coordinateSystems = new HashSet<>();
		graph.getTransforms().stream().forEach(t -> {
			coordinateSystems.add(t.getInput());
			coordinateSystems.add(t.getOutput());
		});

		if (!outputCoordinateSystem.equals(inputCoordinateSystem)) {

			// return graph.path(outputCoordinateSystem, inputCoordinateSystem).map(p -> {
			// return p.totalTransform(n5);
			// }).orElse(new RealTransformSequence());

			final Optional<TransformPath> pathOpt = graph.path(outputCoordinateSystem, inputCoordinateSystem);
			if (pathOpt.isPresent()) {
				final TransformPath path = pathOpt.get();
				final RealTransform tform = path.totalTransform(n5);
				return tform;
			}
			return new RealTransformSequence();

		}
		else
			return new RealTransformSequence();
	}

	@Override
	public Void call()
	{
		assert outputFiles.size() == inputFiles.size();

		setup();

		for ( int i = 0; i < outputFiles.size(); i++ )
		{
			accept(inputFiles.get(i), outputFiles.get(i));
		}
		return null;
	}

	@Override
	public void accept( String input, String output )
	{

		process(input, output);
	}

	public void process( String inputPath, String outputPath )
	{
		final File inputFile = Paths.get( inputPath ).toFile();
		final File outputFile = Paths.get( outputPath ).toFile();

		logger.debug( "output resolution : " + Arrays.toString( outputResolution ));
		logger.debug( "output size       : " + Util.printInterval( renderInterval ));
		final int nd = renderInterval.numDimensions();

		final IOHelper io = new IOHelper();
		//RandomAccessibleInterval<T> rai = io.getRai();
		final RealRandomAccessible< T > img = io.readPhysical( inputFile, interpolation, extendOption );
		final IntervalView< T > imgXfm = Views.interval(
				Views.raster( new RealTransformRandomAccessible<>( img, totalTransform ) ),
				renderInterval );

		logger.info( "allocating" );
		final ImagePlusImgFactory< T > factory = new ImagePlusImgFactory<>( (T)io.getType() );
		final ImagePlusImg< T, ? > imgout = factory.create( renderInterval );

		logger.info( "copying with " + nThreads + " threads." );
		try
		{
			//RenderUtil.copyToImageStackIterOrder( imgXfm, imgout, nThreads );
			RenderUtil.copyToImageStack( imgXfm, imgout, nThreads );
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
			logger.error( "copying failed" );
			return;
		}

		logger.info( "writing to: " + outputPath );
		final ImagePlus ipout = imgout.getImagePlus();
		ipout.getCalibration().pixelWidth = outputResolution[ 0 ];
		ipout.getCalibration().pixelHeight = outputResolution[ 1 ];

		if( nd > 2 )
			ipout.getCalibration().pixelDepth = outputResolution[ 2 ];

		if ( io.getIp() != null )
			ipout.getCalibration().setUnit( io.getIp().getCalibration().getUnit() );

		IOHelper.write( ipout, outputFile );
	}

}
