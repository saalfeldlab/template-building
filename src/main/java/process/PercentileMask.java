package process;

import java.io.File;
import java.io.IOException;

import com.google.common.collect.Streams;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.algorithm.stats.WindowedCentileStats;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ByteImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.img.planar.PlanarRandomAccess;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class PercentileMask
{

	public static void main( String[] args ) throws FormatException, IOException, ImgLibException
	{
		String inPath = args[ 0 ];
		String outPath = args[ 1 ];
		double centile = Double.parseDouble( args[ 2 ] );

		int subsampleForCentile = 4;
		if( args.length >= 4 )
		{
			subsampleForCentile = Integer.parseInt( args[ 3 ] );
		}

		ImagePlus ip = null;
		if( inPath.endsWith( "nii" ))
		{
			ip = NiftiIo.readNifti( new File( inPath ));
		}
		else
		{
			ip =  IJ.openImage( inPath );
		}

		ImagePlus result = run( ip, centile, subsampleForCentile );
		result.setDimensions( result.getNSlices(), result.getNChannels(), result.getNFrames() );

		if( outPath.endsWith( "nii" ))
		{
			Nifti_Writer writer = new Nifti_Writer();
			File fout = new File( outPath );
			writer.save( result, fout.getParent(), fout.getName() );
		}
		else
		{
			IJ.save( result, outPath );
		}
	}

	public static ImagePlus run( ImagePlus in, double centile, int subsampleStep ) throws ImgLibException
	{
		Img< FloatType > img = ImageJFunctions.convertFloat( in );
		ByteImagePlus< ByteType > result = ImagePlusImgs.bytes( Intervals.dimensionsAsLongArray( img ) );

		System.out.println( "computing threshold" );
		double[] data = toDoubleArray( Views.iterable( Views.subsample( img, subsampleStep )) );
		System.out.println( "nsamples: " + data.length );

		WindowedCentileStats alg = new WindowedCentileStats( new double[]{ centile } );
		double threshold = alg.centiles( data )[ 0 ];
		System.out.println( "threshold: " + threshold );

		Cursor< FloatType > c = img.cursor();
		PlanarRandomAccess< ByteType > ra = result.randomAccess();
		while( c.hasNext() )
		{
			c.fwd();
			ra.setPosition( c );

			if( c.get().get() < threshold )
				ra.get().setZero();
			else
				ra.get().setOne();
		}

		return result.getImagePlus();
	}

	public static <T extends RealType<T>> double[] toDoubleArray( final IterableInterval<T> in )
	{
		return Streams.stream( in ).mapToDouble( x -> x.getRealDouble() ).toArray();
	}
}
