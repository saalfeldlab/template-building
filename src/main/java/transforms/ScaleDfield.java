package transforms;

import java.util.concurrent.Callable;

import io.DfieldIoHelper;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.2.0-SNAPSHOT" )
public class ScaleDfield implements Callable<Void>
{

	@Option( names = { "-i", "--input" }, required = true, description = "Displacement field path" )
	private String fieldPath;

	@Option( names = { "-o", "--output" }, required = true, description = "Output path" )
	private String outputPath;

	@Option( names = { "-s", "--scale" }, required = true, description = "Scaling factor" )
	private double factorsArg;

//	@Option( names = { "-j", "--nThreads" }, required = false, description = "Number of threads for downsampling" )
//	private int nThreads = 8;

	public static void main( String[] args )
	{
		CommandLine.call( new ScaleDfield(), args );
	}

	public Void call()
	{
		DfieldIoHelper io = new DfieldIoHelper();
		RandomAccessibleInterval< FloatType > dfield;
		try
		{
			dfield = io.read( fieldPath );
		}
		catch ( Exception e1 )
		{
			e1.printStackTrace();
			return null;
		}

		// scale
		Cursor< FloatType > c = Views.iterable( dfield ).cursor();
		while( c.hasNext() )
		{
			c.next().mul( factorsArg );
		}

		try
		{
			io.write( dfield, outputPath );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}

		return null;
	}

}

