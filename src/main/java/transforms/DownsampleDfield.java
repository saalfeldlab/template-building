package transforms;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.LongStream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.common.collect.Streams;

import ij.IJ;
import ij.ImagePlus;
import io.WritingHelper;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import sc.fiji.io.Nrrd_Reader;

public class DownsampleDfield
{
	
	public static class Options implements Serializable
	{

		private static final long serialVersionUID = -5666039337474416226L;

		@Option( name = "-d", aliases = {"--dfield"}, required = true, usage = "Displacement field path" )
		private String fieldPath;
		
		@Option( name = "-o", aliases = {"--output"}, required = true, usage = "Output path" )
		private String outputPath;

		@Option( name = "-f", aliases = {"--factors"}, required = false, usage = "Downsampling factors" )
		private String factors = "";

		@Option( name = "-j", aliases = {"--nThreads"}, required = false, usage = "Number of threads for downsampling" )
		private int nThreads = 8;
		
		@Option( name = "--sample", required = false, usage = "Sample for downsampling instead of averaging" )
		private boolean sample = false;
		
		@Option( name = "--estimateError", required = false, usage = "Estimate errors for downsampling" )
		private boolean estimateError = false;
		
		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);
			} catch (final CmdLineException e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}

	}

	public static void main( String[] args )
	{
//		new ImageJ();

		final Options options = new Options(args);
		
		ImagePlus dfieldIp = null;
		if( options.fieldPath.endsWith( "nii" ))
		{
			try
			{
				System.out.println("loading nii");
				dfieldIp =  NiftiIo.readNifti( new File( options.fieldPath ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
			System.out.println("done");
		}
		else if( options.fieldPath.endsWith( "nrrd" ))
		{
			// This will never work since the Nrrd_Reader can't handle 4d volumes, actually
			Nrrd_Reader nr = new Nrrd_Reader();
			File imFile = new File( options.fieldPath );
			dfieldIp = nr.load( imFile.getParent(), imFile.getName());
		}
		else
		{
			dfieldIp = IJ.openImage( options.fieldPath );
		}
		
		System.out.println( dfieldIp );
		
//		dfieldIp.show();
		
		
		String[] factorArrays = options.factors.split( "," );
		
		System.out.println( options.factors );
		System.out.println( Arrays.toString( factorArrays ));
		
		long[] factors;
		if( factorArrays.length >= 4 )
			factors = Arrays.stream( factorArrays ).mapToLong( Long::parseLong ).toArray();
		else
			factors = Streams.concat(
					Arrays.stream( factorArrays ).mapToLong( Long::parseLong ), 
					LongStream.of( 1 ) ).toArray();
		
		
		
		Img< FloatType > dfield = ImageJFunctions.wrapFloat( dfieldIp );
		
		double[] resIn = new double[ 3 ];
		resIn[ 0 ] = dfieldIp.getCalibration().pixelWidth;
		resIn[ 1 ] = dfieldIp.getCalibration().pixelHeight;
		resIn[ 2 ] = dfieldIp.getCalibration().pixelDepth;

		double[] resOut = new double[ 3 ];
		resOut[ 0 ] = resIn[ 0 ] * factors[ 0 ];
		resOut[ 1 ] = resIn[ 1 ] * factors[ 1 ];
		resOut[ 2 ] = resIn[ 2 ] * factors[ 2 ];

		RandomAccessibleInterval< FloatType > dfieldDownAvg = DownsampleDfieldErrors.downsampleAverage( dfield, factors, options.nThreads );

		if( options.estimateError )
		{
			AffineTransform dfieldToPhysical = new AffineTransform( 4 );
			dfieldToPhysical.set( factors[0], 0, 0 );
			dfieldToPhysical.set( factors[1], 1, 1 );
			dfieldToPhysical.set( factors[2], 2, 2 );
			
			IntervalView< FloatType > dfieldSubInterp = Views.interval( 
				Views.raster( 
					RealViews.affine( 
						Views.interpolate( 
							Views.extendZero( dfieldDownAvg ),
							new NLinearInterpolatorFactory< FloatType >()),
					dfieldToPhysical )),
				dfield);
			
			DownsampleDfieldErrors.compare( dfield, dfieldSubInterp, options.nThreads );
		}
		
		try
		{
			ImagePlus ip = ((ImagePlusImg<?,?>)dfieldDownAvg).getImagePlus();
			ip.getCalibration().pixelWidth = resOut[ 0 ];
			ip.getCalibration().pixelHeight = resOut[ 1 ];
			ip.getCalibration().pixelDepth = resOut[ 2 ];

			WritingHelper.write( ip, options.outputPath);
		} catch ( ImgLibException e )
		{
			e.printStackTrace();
		}
	}

}

