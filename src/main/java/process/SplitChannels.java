package process;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.ChannelSplitter;
import io.IOHelper;
import io.nii.NiftiIo;
import loci.formats.FormatException;


public class SplitChannels {

	public static void main(String[] args) throws FormatException, IOException
	{
		String imF = args[ 0 ];
		String destDir = args[ 1 ];
		String ext = args[ 2 ];

		ImagePlus ip = null;
		if( imF.endsWith( "nii" ))
		{
			
			ip = NiftiIo.readNifti( new File( imF ));
		}
		else if( imF.endsWith("lsm"))
		{
			org.imagearchive.lsm.reader.Reader reader = new org.imagearchive.lsm.reader.Reader();
			ip = reader.open( imF );
		}
		else
		{
			try{ 
			ip = IJ.openImage( imF );
			} 
			catch( Exception e )
			{
				e.printStackTrace();
			}
		}

		ImagePlus[] channels = ChannelSplitter.split( ip );

		
		String useExt = ext;
		if( !useExt.startsWith("."))
			useExt = "." + useExt;
		
		for( ImagePlus ch : channels )
		{
			String outName = ch.getTitle().replaceFirst( "\\..+",  useExt);
			String outF = destDir + File.separator + outName;
			
			System.out.println("saving to: " + outF );
			IOHelper.write( ch, outF );
		}
	}
}
