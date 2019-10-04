package net.imglib2.realtransform;

import java.util.concurrent.Callable;

import com.google.common.io.Files;

import ij.ImagePlus;
import io.DfieldIoHelper;
import io.IOHelper;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.GenericComposite;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.1.1-SNAPSHOT" )
public class DisplacementFieldMagnitude implements Callable< Void >
{
	@Option( names = { "-i", "--input" }, required = true, description = "Displacement field input." )
	private String inputPath;

	@Option( names = { "-o", "--output" }, required = false, description = "Output path for displacement field magnitude. "
			+ "If this is not provided the default output adds a suffix to the input file." )
	private String outputPath;

	@Option( names = { "-s", "--suffix" }, required = false, description = "Output suffix" )
	private String suffix = "_mag";

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Print a help message" )
	private boolean help;
	
	private String output;

	private ANTSDeformationField dfield;

	private FloatImagePlus< FloatType > magImg;

	public static void main( String[] args )
	{
		CommandLine.call( new DisplacementFieldMagnitude(), args );
	}
	
	public void setup()
	{
		if( outputPath != null && !outputPath.isEmpty())
		{
			output = outputPath;
		}
		else
		{
			String ext = Files.getFileExtension( inputPath );
			output = inputPath.replaceAll( "."+ ext, suffix + "." + ext );
			System.out.println( "output path: " + output );
		}

		DfieldIoHelper dio = new DfieldIoHelper();
		try
		{
			setDfield( dio.readAsDeformationField( inputPath ) );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}

	@Override
	public Void call()
	{
		setup();
		run();

		return null;
	}
	
	public void run()
	{
		RandomAccessibleInterval< FloatType > dfieldRai = dfield.getImg();
		final int nc = (int)dfieldRai.dimension( dfieldRai.numDimensions() - 1 );
		
		CompositeIntervalView< FloatType, ? extends GenericComposite< FloatType > > dfieldVec = Views.collapse( dfieldRai );
		
		// allocate output
		setMagImg( ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( dfieldVec ) ) );
		
		System.out.println("computing");
		LoopBuilder.setImages( dfieldVec, getMagImg() ).forEachPixel( 
				(i,o) -> { o.setReal( magnitude( i, nc ) );}
			);
		
		ImagePlus ip = getMagImg().getImagePlus();
		ip.getCalibration().pixelWidth = dfield.getResolution()[ 0 ];
		ip.getCalibration().pixelHeight = dfield.getResolution()[ 1 ];
		ip.getCalibration().pixelDepth = dfield.getResolution()[ 2 ];
		ip.setDimensions( 1, ( int )dfield.getImg().dimension( 2 ), 1 );

		System.out.println("writing");
		IOHelper.write( ip, output );
	}
	
	public static <T extends RealType<T>> double magnitude( final Composite<T> c, final int N )
	{
		double out = 0.0;
		for( int i = 0; i < N; i++ )
		{
			double v = c.get( i ).getRealDouble();
			out += ( v * v );
		}

		return Math.sqrt( out );
	}


	public ANTSDeformationField getDfield()
	{
		return dfield;
	}

	public void setDfield( ANTSDeformationField dfield )
	{
		this.dfield = dfield;
	}

	public String getOutput()
	{
		return output;
	}

	public void setOutput( String output )
	{
		this.output = output;
	}

	public FloatImagePlus< FloatType > getMagImg()
	{
		return magImg;
	}

	public void setMagImg( FloatImagePlus< FloatType > magImg )
	{
		this.magImg = magImg;
	}

}
