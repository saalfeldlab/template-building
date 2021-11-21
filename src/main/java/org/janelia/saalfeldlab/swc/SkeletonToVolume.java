package org.janelia.saalfeldlab.swc;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import ij.ImagePlus;
import io.IOHelper;
import net.imglib2.FinalInterval;
import net.imglib2.Point;
import net.imglib2.RealPoint;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.img.planar.PlanarRandomAccess;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.util.ValuePair;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import process.RenderTransformed;

public class SkeletonToVolume implements Callable< Void > {
	
	@Option( names = { "-i", "--input" }, required = true, description = "Skeleton path" )
	private String skeletonPath; 

	@Option( names = { "-o", "--output" }, required = true, description = "Output image file" )
	private String outputPath;

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

	private FinalInterval renderInterval;

	public static void main( String... args )
	{
		new CommandLine(new SkeletonToVolume()).execute(args);
		System.exit(0);
	}
	
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
	}

	@Override
	public Void call() throws Exception
	{
		setup();

		final File skeletonFile = Paths.get( skeletonPath ).toFile();
		final File outputFile = Paths.get( outputPath ).toFile();
		
		final int nd = renderInterval.numDimensions();
		final ImagePlusImgFactory< UnsignedByteType > factory = new ImagePlusImgFactory<>( new UnsignedByteType() );
		final ImagePlusImg< UnsignedByteType, ? > imgout = factory.create( renderInterval );

		final Swc skeleton = Swc.read(skeletonFile);
		final PlanarRandomAccess<UnsignedByteType> ra = imgout.randomAccess();

		int numSet = 0;
		final Scale xfm = new Scale( outputResolution );
		final RealPoint p = new RealPoint( nd );
		final Point pix = new Point( nd );
		for( SwcPoint pt : skeleton.getPoints() )
		{
			p.setPosition(pt.x, 0);
			p.setPosition(pt.y, 1);
			p.setPosition(pt.z, 2);
			xfm.applyInverse(p, p);

			pix.setPosition( (int)Math.round(p.getDoublePosition(0)), 0);
			pix.setPosition( (int)Math.round(p.getDoublePosition(1)), 1);
			if( nd > 2 )
				pix.setPosition( (int)Math.round(p.getDoublePosition(2)), 2);

			if( Intervals.contains(imgout, pix))
			{
				ra.setPosition(pix);
				ra.get().set(255);
				numSet++;
			}
		}

		System.out.println( String.format("set %d points", numSet ));

		ImagePlus ipout = imgout.getImagePlus();
		ipout.getCalibration().pixelWidth = outputResolution[ 0 ];
		ipout.getCalibration().pixelHeight = outputResolution[ 1 ];
		if( nd > 2 )
			ipout.getCalibration().pixelDepth = outputResolution[ 2 ];

		IOHelper.write( ipout, outputFile );

		return null;
	}
}
