package io.dfield;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;

@Command( version = "0.2.0-SNAPSHOT" )
public class ResaveDfield implements Callable<Void>
{

	@Option(names = { "-i", "--input", "--dfield" }, required = true)
	private String fieldPath;

	@Option(names = { "-o", "--output" }, required = true)
	private String output;

	@Option(names = { "-e", "--encoding" }, required = false)
	private String encoding = "raw";

	public static void main(String[] args)
	{
		CommandLine.call( new ResaveDfield(), args );	
	}

	public Void call()
	{
		ImagePlus baseIp = null;
		if( fieldPath.endsWith( "nii" ))
		{
			try
			{
				baseIp =  NiftiIo.readNifti( new File( fieldPath ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		
		Img<FloatType> img = ImageJFunctions.convertFloat( baseIp );
		

		return null;
	}

}
