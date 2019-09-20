package vis;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import ij.IJ;
import ij.ImagePlus;
import io.IOHelper;
import loci.formats.FormatException;
import mpicbg.ij.clahe.Flat;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
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
		double max = Double.parseDouble( args[ 3 ] );
		
		// the remaining args are slice codes
		Stream<String> codeArgs = Arrays.stream( args ).skip( 4 );

		System.out.println( "min max " + min + " " + max );

		IOHelper io = new IOHelper();
		ImagePlus ip = io.readIp( fin );

		if( ip.getBitDepth() == 8 )
		{
			System.out.println("byte");
			run( codeArgs, ImageJFunctions.wrapByte( ip ), min, max, foutBase );
		}
		else if( ip.getBitDepth() == 16 )
		{
			System.out.println("short");
			run( codeArgs, ImageJFunctions.wrapShort( ip ), min, max, foutBase );
		}
		else if( ip.getBitDepth() == 32 )
		{
			System.out.println("float");
			run( codeArgs, ImageJFunctions.wrapFloat( ip ), min, max, foutBase );
		}
	}
	
	public static < T extends RealType< T > & NativeType< T >>  void run( 
			final Stream<String> args, final Img<T> img ,
			final double min, final double max,
			final String foutBase )
	{
		ImagePlusImgFactory<UnsignedByteType> factory = new ImagePlusImgFactory<UnsignedByteType>( new UnsignedByteType() );
		
		Stream<String> sliceCodes =  args.flatMap( x -> expand( x, img ));
		
//		System.out.println("TOTAL RANGE: ");
//		printMinMax( img );
		
		sliceCodes.forEach( x -> {
			System.out.println( x );
			IntervalView<T> slcView = hyperslice(x, img );
			System.out.println( Util.printInterval( slcView ));
//			printMinMax( slcView );
			
			// make a copy so we can process the output
			ImagePlusImg<UnsignedByteType, ?> res = factory.create( slcView );

			copyIntoByte( slcView, res, min, max );
			ImagePlus ipOut = null;
			try {
				ipOut = res.getImagePlus();
				
				// process the output
				Flat.getFastInstance().run( ipOut, 72, 128, 2.5f, null, false );

				String base = foutBase.replaceAll( "#", "" );
				String suffix = x.substring( 0, 1 ) + String.format( "%04d", Integer.parseInt( x.substring( 1 )));

				IJ.save( ipOut, base + "_" + suffix + ".png" );
				
			} catch (ImgLibException e) {
				e.printStackTrace();
			}
		});
	}

	public static Stream<String> expand( String arg, Interval img )
	{
		if( arg.startsWith("e"))
		{
			Builder<String> out = Stream.builder();
			
			int d = 2;
			String pre = "z";
			if( arg.substring(1).startsWith("x"))
			{
				d = 0;
				pre="x";
			}
			else if( arg.substring(1).startsWith("y"))
			{
				d = 1;
				pre="y";
			}
			int N = Integer.parseInt( arg.substring(2));
			
			//int center = (int) Math.round( img.dimension( d ));
			int w = (int)Math.round( img.dimension( d ) / ( N + 1 ));
			
			int x = w;
			for( int i = 0; i < N; i++ )
			{
				out.add( String.format("%s%d", pre, x ));
				x += w;
			}
			
			return out.build();
		}
		else
		{
			return Stream.of( arg );
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
	
	public static  < T extends RealType< T > > void printMinMax( RandomAccessibleInterval<T> src )
	{
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		
		Cursor<T> c = Views.flatIterable( src ).cursor();
		while( c.hasNext() )
		{
			double v = c.next().getRealDouble();
			if( v < min )
				min = v;
			
			if( v > max )
				max = v;
		}
		
		System.out.println( "min/max : " + min + " " + max );
	}
	
	public static < T extends RealType< T > > 
		void copyIntoByte( RandomAccessibleInterval<T> src, RandomAccessibleInterval<UnsignedByteType> dst, double min, double max )
	{
		double w = ( max - min );

		Cursor<T> c = Views.flatIterable( src ).cursor();
		RandomAccess<UnsignedByteType> ra = dst.randomAccess();
		while( c.hasNext() )
		{
			c.fwd();
			ra.setPosition( c );
			double val = c.get().getRealDouble();
			if( val <= min )
				ra.get().setZero();
			else if( val >= max )
				ra.get().setInteger( 255 );
			else
				ra.get().setReal( (( val - min ) / w ) * 255.0  );
		}
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
