package vis;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.MontageMaker;
import ij.process.ImageProcessor;

public class MakeMontage2D
{

	public static void main( String[] args )
	{
		int j = 0;
		
		boolean addLabel = false;
		while( args[ j ].startsWith("-"))
		{
			if( args[ j ].equals( "-label" ))
				addLabel = true;
			else if( args[ j ].equals( "-label" ))
			
			j++;
		}
		
		String outpath = args[ j ];
		j++;

		ImagePlus[] ipList = new ImagePlus[ args.length - 1 ];
		int maxHeight = 0;
		int maxWidth = 0;
		for( int i = j; i < args.length; i++ )
		{
			ImagePlus ip = IJ.openImage( args[ i ] );
			ipList[ i - 1 ] = ip;
			
			int h = ip.getHeight();
			int w = ip.getWidth();
			if(  h > maxHeight )
				maxHeight = h; 
			
			if(  w > maxWidth)
				maxWidth = w;
		}
		System.out.println( "max h " + maxHeight );
		System.out.println( "max w " + maxWidth );

		IJ.save(
			makeMontage( 
				makeAllTheSameSize( 
						ipList, maxWidth, maxHeight, addLabel ) ),
			outpath );
	}
	
	public static ImagePlus makeMontage( ImagePlus ip )
	{
		double scale = 1.0;
		int first = 1;
		int last = ip.getStackSize();
		
		int rows= (int)Math.ceil( Math.sqrt(last));
		int  columns = (int)Math.ceil( (float)last / (float)rows );
		
		System.out.println( rows + " x " + columns );
		
		MontageMaker mm = new MontageMaker();
		
		ImagePlus montage = mm.makeMontage2( ip, rows, columns, scale, first, last, 1, 5, true );
		return montage;
	}
	
	public static ImagePlus makeAllTheSameSize( ImagePlus[] ipList, int maxWidth, int maxHeight,
			boolean addLabel )
	{

		int wNew = maxWidth;
		int hNew = maxHeight;
		
		ImageStack stack = new ImageStack( maxWidth, maxHeight );
		int n = 1;
		for( ImagePlus imp : ipList )
		{
			int wOld = imp.getWidth();
			int hOld = imp.getHeight();
			
			int xOff = (wNew - wOld)/2; 
			int yOff = (hNew - hOld)/2;

			ImageProcessor newIP = expandImage(imp.getProcessor(), wNew, hNew, xOff, yOff);
			
//			imp.setProcessor(null, newIP);

			stack.addSlice( newIP );
			
			if( addLabel )
				stack.setSliceLabel( imp.getTitle(), n++ );
		}
		ImagePlus ipout = new ImagePlus( "stack", stack );
		return ipout;

	}

	public static ImageProcessor expandImage(ImageProcessor ipOld, int wNew, int hNew, int xOff, int yOff  ) {
		ImageProcessor ipNew = ipOld.createProcessor(wNew, hNew);
		ipNew.setValue(0.0);
		ipNew.fill();
		ipNew.insert(ipOld, xOff, yOff);
		return ipNew;
}
}
