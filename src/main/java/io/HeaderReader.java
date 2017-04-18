package io;

import java.io.IOException;

import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;

public class HeaderReader
{
	
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
