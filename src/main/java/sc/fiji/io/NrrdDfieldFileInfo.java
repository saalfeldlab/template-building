package sc.fiji.io;

import ij.io.FileInfo;
import ij.measure.Calibration;

public class NrrdDfieldFileInfo extends FileInfo {

	public int dimension=0;
	public int[] sizes;
	public String encoding="";
	public String[] centers=null;
	public int spaceDims=0;
	public double[] spaceOrigin;
	public boolean[] isVector;

	double[][] spaceDirs=null;
	double[] spacings=null;

	public void setSpaceDirs(double [][] spaceDirs){
//		if(spaceDirs.length!=dimension)  throw new RuntimeException
//			("NRRD: Mismatch between spaceDirs ("+spaceDirs.length+") and image dimension ("+dimension+")");

		if(spaceDims==0){
			spaceDims=spaceDirs[0].length;
		} else if(spaceDirs[0].length!=spaceDims)  throw new RuntimeException
			("NRRD: Mismatch between spaceDirs ("+spaceDirs[0].length+") and space dimension ("+spaceDims+")");
		this.spaceDirs = spaceDirs;
		if(spacings==null) spacings=new double[spaceDims];
		for(int i=0;i<spaceDims;i++){

			double spacing2=0.0;
			for(int j=0;j<spaceDims;j++){
				spacing2+=spaceDirs[i][j]*spaceDirs[i][j];
			}
			spacings[i]=spacing2;
			if(i==0) pixelWidth=Math.sqrt(spacing2);
			if(i==1) pixelHeight=Math.sqrt(spacing2);
			if(i==2) pixelDepth=Math.sqrt(spacing2);
		}
	}
	
	public void setSpacing(double[] spacings){
		if(spaceDims!=0 && spaceDims!=spacings.length)
			throw new RuntimeException
			("NRRD: Mismatch between spacings ("+spacings.length+") and space dimension ("+spaceDims+")");
		spaceDims=spacings.length;
		for(int i=0;i<spaceDims;i++){
			if(i==0) pixelWidth=spacings[0];
			if(i==1) pixelHeight=spacings[1];
			if(i==2) pixelDepth=spacings[2];			
		}
	}
	
	public double[][] getSpaceDirs(){
		if(spaceDirs==null){
			// Initialise spaceDirs if required
			spaceDirs=new double[spaceDims][spaceDims];	
			for(int i=0;i<spaceDims;i++) {
				for(int j=0;j<spaceDims;j++){
					if(i==j && i==0) spaceDirs[0][0]=pixelWidth;
					else if (i==j && i==1) spaceDirs[1][1]=pixelHeight;
					else if (i==j && i==2) spaceDirs[2][2]=pixelDepth;
					else spaceDirs[i][j]=0.0;
				}
			}
		}
		return spaceDirs;
	}

	public void setSpace(String space) {
		spaceDims=0;
		if (space.equals("right-anterior-superior") || space.equals("RAS")) spaceDims=3;
		else if (space.equals("left-anterior-superior") || space.equals("LAS")) spaceDims=3;
		else if (space.equals("left-posterior-superior") || space.equals("LPS")) spaceDims=3;
		else if (space.equals("right-anterior-superior-time") || space.equals("RAST")) spaceDims=4;
		else if (space.equals("left-anterior-superior-time") || space.equals("LAST")) spaceDims=4;
		else if (space.equals("left-posterior-superior-time") || space.equals("LPST")) spaceDims=4;
		else if (space.equals("scanner-xyz")) spaceDims=3;
		else if (space.equals("scanner-xyz-time"	)) spaceDims=4;
		else if (space.equals("3D-right-handed")) spaceDims=3;
		else if (space.equals("3D-left-handed")) spaceDims=3;
		else if (space.equals("3D-right-handed-time")) spaceDims=4;
		else if (space.equals("3D-left-handed-time")) spaceDims=4;				
		else throw new RuntimeException("NRRD: Unrecognised coordinate space: "+space);
	}

	public void setSpaceOrigin (double[] spaceOrigin){
		if(spaceOrigin.length!=spaceDims) throw new RuntimeException
			("NRRD: mismatch between dimensions of space origin ("+spaceOrigin.length
					+") and space dimension ("+spaceDims+")");
					
		this.spaceOrigin=spaceOrigin;
		// TOFIX - this order of allocations is not a given!
		// NB xOrigin are in pixels, whereas axismins are of course
		// in units; these are converted later
		
		// TODO - not sure whether it is worth implementing this for
		// non-orthogonal axes
//		if(i==0) spatialCal.xOrigin=spaceOrigin[0];
//		if(i==1) spatialCal.yOrigin=spaceOrigin[1];
//		if(i==2) spatialCal.zOrigin=spaceOrigin[2];

	}
	
	public double[] getSpaceOrigin (){
		if(spaceOrigin==null){
			for(int i=0;i<spaceDims;i++) spaceOrigin[i]=0.0;
		}
		return spaceOrigin;
	}
	
	public Calibration updateCalibration(Calibration cal){

		cal.pixelWidth=pixelWidth;
		cal.pixelHeight=pixelHeight;
		cal.pixelDepth=pixelDepth;
		
		// The ImageJ origin is the position in pixels of the origin
		// with respect to the first pixel
		
		// The nrrd origin is the position in units of the centre of the 
		// first pixel with respect to the real origin
		
		// When loading a nrrd, we will not try to rotate the data, just
		// use the physical coordinate space aligned with image block space.
		// ie scanner space
		// But if we do that, is the origin really meaningful?
		// so have commented out code below for now
		
//		if(cal.pixelWidth!=0) cal.xOrigin=-spaceOrigin[0]/cal.pixelWidth;
//		if(cal.pixelHeight!=0) cal.yOrigin=-spaceOrigin[1]/cal.pixelHeight;
//		if(cal.pixelDepth!=0) cal.zOrigin=-spaceOrigin[2]/cal.pixelDepth;

		return cal;
	}
	
	// Additional compression modes for fi.compression
	public static final int GZIP = 1001;
	public static final int ZLIB = 1002;
	public static final int BZIP2 = 1003;
	
	// Additional file formats for fi.fileFormat
	public static final int NRRD = 1001;
	public static final int NRRD_TEXT = 1002;
	public static final int NRRD_HEX = 1003;

}
