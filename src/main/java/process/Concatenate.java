package process;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import loci.formats.FormatException;
import net.imglib2.exception.ImgLibException;
import process.PerPixelMeanVariance;

public class Concatenate
{
	public static void main( String[] args ) throws FormatException, IOException, ImgLibException
	{
		String outPath = args[ 0 ];

		ImagePlus[] ipList = new ImagePlus[ args.length - 1 ];
		int totalSlices = 0;
		for( int i = 1; i < args.length; i++ )
		{
			ipList[ i - 1 ] = PerPixelMeanVariance.read( new File( args[ i ]) );
			System.out.println( ipList[i-1] );
			totalSlices += ipList[ i - 1 ].getImageStackSize();
		}
		int N = args.length - 1;
		System.out.println( "num inputs: " + N );
		
		ImageStack stack = new ImageStack(
				ipList[0].getWidth(), ipList[0].getHeight() );
//				ipList[0].getWidth(), ipList[0].getHeight(), totalSlices );

		for( int i = 0; i < N; i++ )
		{
			ImagePlus ip = ipList[ i ];
			for( int j = 0; j < ip.getImageStackSize(); j++ )
			{
				System.out.println( i + " " + j );
				ip.setSlice( j );
				stack.addSlice( ip.getProcessor() );
				System.out.println( ip.getProcessor() );
			}
		}
		System.out.println( stack );
		ImagePlus out = new ImagePlus( "concatStack", stack );
		out.setDimensions( 1, totalSlices/N, N );
		
		int nc = out.getNChannels();
		int nf = out.getNFrames();
		int nz = out.getNSlices();
		int ni = out.getImageStackSize();
		System.out.println( "nc " + nc );
		System.out.println( "nz " + nz );
		System.out.println( "nf " + nf );
		System.out.println( "ni " + ni );
		
		IJ.save( out, outPath );
	}
	
	public void WHYDOESTHISNOTWORK()
	{
//		Concatenator concatenator = new Concatenator();
//		
//		ImagePlus out = concatenator.concatenateHyperstacks( ipList, "concatStack", false );
//		int nc = out.getNChannels();
//		int nf = out.getNFrames();
//		int nz = out.getNSlices();
//		int ni = out.getImageStackSize();
//		System.out.println( "nc " + nc );
//		System.out.println( "nz " + nz );
//		System.out.println( "nf " + nf );
//		System.out.println( "ni " + ni );
//
//		out.setDimensions( 1, ni/N, N );
//		
//		nc = out.getNChannels();
//		nf = out.getNFrames();
//		nz = out.getNSlices();
//		ni = out.getImageStackSize();
//		System.out.println( "nc " + nc );
//		System.out.println( "nz " + nz );
//		System.out.println( "nf " + nf );
//		System.out.println( "ni " + ni );
	}
}