package process;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.transform.io.TransformReader;

import io.IOHelper;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.img.planar.PlanarCursor;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ValuePair;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.2.0-SNAPSHOT" )
public class TransformInverseAccuracy implements Callable< Void >
{
	
	@Option( names = { "-f", "--forward" }, required = false, description = "Forward transformation." )
	private List< String > fwdTransformFiles = new ArrayList<>();

	@Option( names = { "-i", "--inverse" }, required = false, description = "Inverse transformation." )
	private List< String > invTransformFiles = new ArrayList<>();

	@Option( names = { "-e", "--ref" }, required = false, description = "Reference volume." )
	private String referenceImagePath;

	@Option( names = { "-o", "--output" }, required = false, description = "Output volume path." )
	private String outputPath;

	@Option( names = { "-s", "--outputImageSize" }, required = false,
			description = "Size / field of view of image output in pixels.  Comma separated, e.g. \"200,250,300\". "
					+ "Overrides reference image."  )
	private String outputSize;

	@Option( names = { "-r", "--output-resolution" }, required = false, split = ",", 
			description = "The resolution at which to write the output. Overrides reference image." )
	private double[] outputResolution;

	private RealTransform fwdXfm;

	private RealTransform invXfm;

	private Interval renderInterval;

	public static void main( String[] args )
	{
		CommandLine.call( new TransformInverseAccuracy(), args );
	}

	public TransformInverseAccuracy(){ }
	
	public TransformInverseAccuracy( final RealTransform fwdXfm, final RealTransform invXfm )
	{
		this.fwdXfm = fwdXfm;
		this.invXfm = invXfm;
	}

	public Void call() throws Exception
	{
		setup();
		process();
		return null;
	}
	
	public void setup()
	{
		fwdXfm = TransformReader.readTransforms( fwdTransformFiles );
		invXfm = TransformReader.readTransforms( invTransformFiles );
		
		Optional<ValuePair< long[], double[] >> sizeAndRes = Optional.empty();
		if ( referenceImagePath != null && !referenceImagePath.isEmpty() && new File( referenceImagePath ).exists() )
		{
			IOHelper io = new IOHelper();
			sizeAndRes = Optional.of( io.readSizeAndResolution( new File( referenceImagePath ) ));
			if( sizeAndRes.isPresent() )
			{
				renderInterval = new FinalInterval( sizeAndRes.get().getA() );
				outputResolution = sizeAndRes.get().getB();
			}
		}
		else if( fwdTransformFiles.size() == 1 )
		{
			String transformFile = fwdTransformFiles.get( 0 );
			if( transformFile.contains( ".nrrd" ) || transformFile.contains( ".nii" ) || transformFile.contains( ".h5" ))
			{
				try
				{
					sizeAndRes = Optional.of( TransformReader.transformSizeAndRes( transformFile ));
					if( sizeAndRes.isPresent() )
					{
						renderInterval = new FinalInterval( sizeAndRes.get().getA() );
						outputResolution = sizeAndRes.get().getB();
					}
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

	}

	public void process()
	{
		int nd = renderInterval.numDimensions();
		RealPoint p = new RealPoint( nd );
		RealPoint mid = new RealPoint( nd );
		RealPoint res = new RealPoint( nd );

		Scale3D pix2Physical = new Scale3D( outputResolution );
		ImagePlusImgFactory< FloatType > factory = new ImagePlusImgFactory<>( new FloatType() );
		ImagePlusImg< FloatType, ? > diffImg = factory.create( renderInterval );
		
		PlanarCursor< FloatType > c = diffImg.cursor();

		while ( c.hasNext() )
		{
			c.fwd();
			pix2Physical.apply( c, p );
			fwdXfm.apply( p, mid );
			invXfm.apply( mid, res );

			double dist = 0;
			for( int i = 0; i < nd; i++ )
			{
				double diff = res.getDoublePosition( i ) - p.getDoublePosition( i );
				dist += ( diff * diff );
			}
			dist = Math.sqrt( dist );
			c.get().setReal( dist );
		}

		IOHelper.write( diffImg.getImagePlus(), outputPath );
	}

}
