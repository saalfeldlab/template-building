package process;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.ZProjector;
import io.nii.NiftiIo;
import loci.formats.FormatException;

public class ZProject
{

	public static void main( String[] args ) throws FormatException, IOException
	{
		String out = args [ 0 ];
		String in = args[ 1 ];
		String method = args[ 2 ];
		
		ImagePlus ip = null;
		if( in.endsWith( "nii" ) || in.endsWith( "nii.gz" ))
		{
			ip = NiftiIo.readNifti( new File( in ) );
		}
		else
		{
			ip = IJ.openImage( in );
		}
		
		ZProjector projector = new ZProjector( ip );
		projector.setStopSlice( ip.getNSlices() );
		if( method == "MAX" )
		{
			projector.setMethod( ZProjector.MAX_METHOD );
		}
		else
		{
			projector.setMethod( ZProjector.AVG_METHOD );
		}
		
		projector.doProjection();
		ImagePlus projIp = projector.getProjection();
		IJ.save( projIp, out );
	}
}