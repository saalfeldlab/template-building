package transforms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.transform.io.TransformReader;

import io.ConvertAffine;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.1.1-SNAPSHOT",
		description="Concatenates affine spatial transformations.")
public class ConcatenateAffines implements Callable< Void >
{

	@Option(names = {"-i", "--input"}, description = "Input affine transforms.  When multiple affines are provided, "
			+ "the output will be that transformation resulting from applying the transforms in the order they are given, "
			+ "NOT the result of multiplying the matrices (left-to-right) in the given order.",
			required = true )
	private List<String> affinePaths;

	@Option(names = {"-o", "--output"}, description = "Output affine file name.", required = false )
	private String outputPath;

	@Option( names = { "-v", "--version" }, required = false, versionHelp = true, description = "Prints version information and exits." )
	private boolean version;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Print this help message" )
	private boolean help;

	public static void main( String[] args )
	{
		CommandLine.call( new ConcatenateAffines(), args );
		System.exit(0);
	}

	public Void call()
	{
		int i = 0;
		ArrayList<AffineTransform3D> affineList = new ArrayList<>();

		while ( i < affinePaths.size() )
		{
			InvertibleRealTransform xfm = TransformReader.readInvertible( affinePaths.get( i ) );
			if( xfm instanceof AffineTransform )
			{
				affineList.add( TransformReader.from( (AffineTransform)xfm ) );
			}
			else if( xfm instanceof AffineTransform3D )
			{
				affineList.add( (AffineTransform3D)xfm );
			}
			else
			{
				System.err.println( "file " + affinePaths.get( i ) + " can't be interpreted as an affine.");
				i++;
				continue;
			}
			i++;
		}

		AffineTransform3D result = concatenate( affineList );
		if( outputPath != null && !outputPath.isEmpty() )
		{
			try
			{
				ConvertAffine.write( result, outputPath );
			}
			catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			System.out.println( result );
		}


		return null;
	}
	
	public static AffineTransform3D concatenate( List<AffineTransform3D> affineList )
	{
		AffineTransform3D result = new AffineTransform3D();
		affineList.stream().forEach( x -> { result.preConcatenate( x ); });
		return result;
	}
}
