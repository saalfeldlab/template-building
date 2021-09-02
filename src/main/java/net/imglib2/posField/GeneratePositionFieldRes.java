package net.imglib2.posField;

import java.io.File;
import java.util.concurrent.Callable;


import ij.ImagePlus;
import io.IOHelper;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import util.FieldOfView;
import util.RenderUtil;

@Command( version = "0.1.1-SNAPSHOT",
		description="Creates an image where the intensity corresponds to the a "
				+ "coordinate of that pixels/voxel's spatial position.")
public class GeneratePositionFieldRes implements Callable<Void>
{

	@Option( names = { "-f", "--reference-image" }, required = false, description = "Input reference image (optional)." )
	private String imagePath;

	@Option( names = { "-o", "--output" }, required = false, description = "Output position field" )
	private String outputPath;

	@Option( names = { "-d", "--dimensions" }, required = false, description = "Image dimensions" )
	private long[] intervalDimsIn;

	@Option( names = { "-r", "--resolution" }, required = false, description = "Output resolution" )
	private double[] resolutionIn;

	@Option( names = { "-m", "--min", "--offset" }, required = false, description = "Offset of output field" )
	private double[] offsetIn;

	@Option( names = { "-c", "--coordinate" }, required = false, description = "Coordinate that intensity will correspond to" )
	private int coordinate = -1;
	
	private double[] resolution;

	private double[] offset;

	private long[] intervalDims;

	public static void main( String[] args ) throws ImgLibException
	{
		CommandLine.call( new GeneratePositionFieldRes(), args );
	}
	
	public void setup()
	{
		if( imagePath != null )
		{
			IOHelper io = new IOHelper();
//			RandomAccessibleInterval<?> rai = io.readRai( new File( imagePath ) );
//			//ImagePlus ip = io.readIp( imagePath );
//			resolution = io.getResolution();
//			intervalDims = Intervals.dimensionsAsLongArray( rai );

			FieldOfView fov = io.readFov(imagePath);
			resolution = fov.getSpacing();
			offset = fov.getPhysicalMin();
		}

		if( intervalDimsIn != null )
			intervalDims = intervalDimsIn;

		if( resolutionIn != null )
			resolution = resolutionIn;

		if( offsetIn != null )
			offset = offsetIn;
	}

	public Void call()
	{
		setup();

		FinalInterval interval = new FinalInterval( intervalDims );

		AffineTransform3D xfm = new AffineTransform3D();
		xfm.set( 	resolution[ 0 ], 0.0, 0.0, offset[ 0 ],
					0.0, resolution[ 1 ], 0.0, offset[ 1 ],
					0.0, 0.0, resolution[ 2 ], offset[ 2 ]);

		PositionRandomAccessible< FloatType, AffineTransform3D > pra = 
				new PositionRandomAccessible< FloatType, AffineTransform3D >(
						interval.numDimensions(), new FloatType(), xfm, coordinate );
		
		System.out.println( "pra ndims: " + pra.numDimensions() );

		int nd = interval.numDimensions();
		
		long[] min = new long[ nd+1 ];
		long[] max = new long[ nd+1 ];

		for( int d = 0; d < nd; d++ )
		{
			min[ d ] = interval.min( d );
			max[ d ] = interval.max( d );
		}

		if( coordinate < 0 )
		{
			min[ nd ] = 0;
			max[ nd ] = nd-1;
		}
		else
		{
			min[nd] = coordinate;
			max[nd] = coordinate;
		}

		FinalInterval outputInterval = new FinalInterval( min, max );
		RandomAccessibleInterval< FloatType > raiout = 
				Views.dropSingletonDimensions( Views.interval( pra, outputInterval ));
		

		// Permute so that slices are correctly interpreted as such (instead of channels )
		FloatImagePlus< FloatType > output = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( raiout ));

		System.out.println( raiout );
		System.out.println( Util.printInterval(raiout) );

		System.out.println( "output interval " + Util.printInterval( output) );

//		LoopBuilder.setImages( output, raiout ).forEachPixel( (y,x) -> { y.set( x.get() ); });

		int nc = 1;
		if( coordinate < 0 )
		{
			nc = 3;
			RenderUtil.copyToImageStack( 
					Views.permute( raiout, 3, 2 ), 
					Views.permute( output, 3, 2 ), 4 );
		}
		else
			RenderUtil.copyToImageStack( raiout, output, 4 );

		ImagePlus imp = output.getImagePlus();
		imp.getCalibration().pixelWidth  = resolution[ 0 ];
		imp.getCalibration().pixelHeight = resolution[ 1 ];
		imp.getCalibration().pixelDepth  = resolution[ 2 ];
		imp.setDimensions( nc, (int)intervalDims[2], 1 );

		IOHelper.write( imp, new File( outputPath ));

		return null;
	}

}
