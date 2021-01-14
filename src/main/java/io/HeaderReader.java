package io;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Callable;

import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import net.imglib2.util.ValuePair;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.2.0-SNAPSHOT" )
public class HeaderReader implements Callable<Void>
{
	@Option(names = {"--input", "-i"}, description = "Input image file" )
	private String inputFilePath;

	public static void main( String[] args )
	{
		CommandLine.call( new HeaderReader(), args );
	}

	public Void call()
	{
		IOHelper io = new IOHelper();
		ValuePair<long[], double[]> hdr = io.readSizeAndResolution( inputFilePath );
		System.out.println( "size : " + Arrays.toString( hdr.getA()));
		System.out.println( "res  : " + Arrays.toString( hdr.getB()));

		return null;
	}

	/**
	 * Returns an ImageReader from which one can obtain image dimensions and other metadata
	 * without having to read all the pixel data
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws FormatException
	 */
	public static ImageReader readDimBioformats( String path ) throws IOException, FormatException
	{
		 System.out.println("readDimBioformats" );
		 ImporterOptions options = new ImporterOptions();
		 options.setId(path);
		 ImportProcess process = new ImportProcess(options);
		 if( !process.execute() )
			 return null;

		 System.out.println( " " );
		 ImageReader reader = process.getImageReader();
		 System.out.println( "nx : " + reader.getSizeX() );
		 System.out.println( "ny : " + reader.getSizeY() );
		 System.out.println( "nz : " + reader.getSizeZ() );
		 System.out.println( "nc : " + reader.getSizeC() );
		 System.out.println( "nt : " + reader.getSizeT() );
		 return reader;
	}
}
