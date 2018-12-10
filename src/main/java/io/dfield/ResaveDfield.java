package io.dfield;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;

public class ResaveDfield
{

	public static class Options implements Serializable
	{

		private static final long serialVersionUID = -5666039337474416226L;

		@Option( name = "-i", aliases = {"--input","--dfield"}, required = true, usage = "" )
		private String fieldPath;
		
		@Option( name = "-o", aliases = {"--output"}, required = true, usage = "" )
		private String output;
		
		@Option( name = "-e", aliases = {"--encoding"}, required = false, usage = "" )
		private String encoding = "raw";

		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);
			} catch (final CmdLineException e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}

		/**
		 * @return output path
		 */
		public String getOutput()
		{
			return output;
		}
		
		/**
		 * @return output path
		 */
		public String getField()
		{
			return fieldPath;
		}
		
		/**
		 * @return nrrd encoding
		 */
		public String getEncoding() {
			return encoding;
		}
	}
	public static void main(String[] args)
	{
		final Options options = new Options(args);
		
		String imF = options.getField();
		String fout = options.getOutput();
		String nrrdEncoding = options.getEncoding();

		ImagePlus baseIp = null;
		if( imF.endsWith( "nii" ))
		{
			try
			{
				baseIp =  NiftiIo.readNifti( new File( imF ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		
		Img<FloatType> img = ImageJFunctions.convertFloat( baseIp );
		System.out.println( Util.printInterval( img ));
		
//		long[] p = new long[]{ 76,48,27, 0 };
		
	}

}
