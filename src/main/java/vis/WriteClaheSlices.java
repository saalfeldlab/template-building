package vis;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import mpicbg.ij.clahe.Flat;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class WriteClaheSlices
{

	public static void main( String[] args ) throws FormatException, IOException, ImgLibException
	{
		String fin =  args[0];
		String foutBase = args[ 1 ];
		System.out.println( foutBase );

		double min = Double.parseDouble( args[ 2 ] );
		double max = Double.parseDouble( args[ 3] );

		System.out.println( "min max " + min + " " + max );
		
		ImagePlus ip = null;
		if( fin.endsWith( "nii" ))
		{
			ip = NiftiIo.readNifti( new File( fin ));
		}
		else
		{
			ip = IJ.openImage( fin );
		}
		
		NumericType<?> t;
		
		if( ip.getBitDepth() == 8 )
		{
			run( args, ImageJFunctions.wrapByte( ip ), min, max, foutBase );
		}
		else if( ip.getBitDepth() == 16 )
		{
			run( args, ImageJFunctions.wrapShort( ip ), min, max, foutBase );
		}
		else if( ip.getBitDepth() == 32 )
		{
			run( args, ImageJFunctions.wrapFloat( ip ), min, max, foutBase );
		}
		
//		ip.show();
//		ip.setSlice( idx );
//		String suffix = String.format("-z%d", idx );
//
//		ImagePlus ipOut = new ImagePlus( "", ip.getProcessor() );
//		Flat.getFastInstance().run( ipOut, 72, 128, 2.5f, null, false );
//		
//		
//		System.out.println( "min: " + min );
//		System.out.println( "max: " + max );
//		ipOut.setDisplayRange(min, max);
	}
	
	public static < T extends NumericType< T > & NativeType< T >>  void run( String[] args, Img<T> img ,
			double min, double max,
			String foutBase ) throws ImgLibException
	{
		ImagePlusImgFactory<T> factory = new ImagePlusImgFactory<T>();
		T t = img.firstElement().copy();
		
		int i = 4;
		while( i < args.length )
		{
			IntervalView<T> slcView = hyperslice( args[ i ], img );
			System.out.println( Util.printInterval( slcView ));
			
			// make a copy so we can process the output
			ImagePlusImg<T, ?> res = factory.create(slcView, t);
			copyInto( slcView, res );
			ImagePlus ipOut = res.getImagePlus();
			
			// process the output
			Flat.getFastInstance().run( ipOut, 72, 128, 2.5f, null, false );

			System.out.println( "min: " + min );
			System.out.println( "max: " + max );
			ipOut.setDisplayRange(min, max);

			IJ.save( ipOut, foutBase + "_" + args[ i ] + ".png" );
			i++;
		}
	}

	public static < T extends NumericType< T > & NativeType< T > >  IntervalView<T> hyperslice( String code, Img<T> img )
	{
		int d = 2;
		if( code.startsWith("x") )
			d = 0;
		else if ( code.startsWith("y") )
			d = 1;

		int slc = Integer.parseInt( code.substring( 1 ));
		return Views.hyperSlice( img, d, slc );
	}
	
	public static < T extends NumericType< T > & NativeType< T > > void copyInto( RandomAccessibleInterval<T> src, RandomAccessibleInterval<T> dst )
	{
		Cursor<T> c = Views.flatIterable( src ).cursor();
		RandomAccess<T> ra = dst.randomAccess();
		while( c.hasNext() )
		{
			c.fwd();
			ra.setPosition(c);
			ra.get().set( c.get() );
		}
	}
}
