package transforms;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import io.DfieldIoHelper;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.CompositeView;
import net.imglib2.view.composite.GenericComposite;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import util.RenderUtil;

@Command( version = "0.1.1-SNAPSHOT",
		description="Convert a displacement field from pixel to physical units.")
public class Pixel2PhysicalDisplacementField<T extends RealType<T> & NativeType<T>> implements Callable< Void >
{
	
	@Option( names = { "-i", "--input" }, required = true, description = "Input displacement field path" )
	private String dfieldPath;

	@Option( names = { "-o", "--output" }, required = true, description = "Output displacement field path" )
	private String dfieldOut;

	@Option( names = { "-r", "--resolution" }, required = true, split=",", description = "Resolution of output." )
	private double[] resolution;

	@Option( names = { "-j", "--num-threads" }, required = false, description = "Number of threads" )
	private int nThreads = 1;


	public Pixel2PhysicalDisplacementField() { }
	
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static void main( String[] args )
	{
		CommandLine.call( new Pixel2PhysicalDisplacementField(), args );
		System.exit(0);
	}

	@SuppressWarnings( "unchecked" )
	public Void call()
	{
		DfieldIoHelper dfieldIo = new DfieldIoHelper();
		ANTSDeformationField dfield = null;
		try
		{
			dfield = dfieldIo.readAsDeformationField( dfieldPath );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
		
		dfieldPixelToPhysicalWrite( dfield.getImg(), resolution, nThreads, dfieldOut );
		
		return null;
	}

	public static <T extends RealType<T> & NativeType<T>> void dfieldPixelToPhysicalWrite(
			final RandomAccessibleInterval<T> dfieldPixel,
			final double[] resolution,
			final int numThreads,
			final String outputPath )
	{
		System.out.println( "dfieldPixel : " + dfieldPixel );
	
		ImagePlusImgFactory< T > factory = new ImagePlusImgFactory<>( Util.getTypeFromInterval( dfieldPixel ) );
		ImagePlusImg< T, ? > dfieldPhysical = factory.create( dfieldPixel );
		dfieldPixelToPhysical( dfieldPixel, dfieldPhysical, resolution, numThreads );

		System.out.println( "dfieldPhysical : " + dfieldPhysical );
		
		DfieldIoHelper dfieldIo = new DfieldIoHelper();
		dfieldIo.spacing = resolution;
		try
		{
			dfieldIo.write( dfieldPhysical, outputPath );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}
		
	public static <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> dfieldPixelToPhysical(
			final RandomAccessibleInterval<T> dfieldPixel,
			final double[] resolution,
			final int nThreads )
	{
	
		final ImagePlusImgFactory< T > factory = new ImagePlusImgFactory<>( Util.getTypeFromInterval( dfieldPixel ) );
		final ImagePlusImg< T, ? > dfieldPhysical = factory.create( dfieldPixel );
		dfieldPixelToPhysical( dfieldPixel, dfieldPhysical, resolution, nThreads );
		return dfieldPhysical;
	}

	public static <T extends RealType<T>, V extends Composite<T>> void dfieldPixelToPhysical(
			final RandomAccessibleInterval<T> dfieldPixel,
			final RandomAccessibleInterval<T> dfieldPhysical,
			final double[] resolution,
			final int nThreads )
	{
		assert resolution.length == ( dfieldPixel.numDimensions() - 1 );

		final long nv = dfieldPixel.dimension( dfieldPixel.numDimensions() - 1 );
//		final RandomAccessibleInterval<V> dpix = Views.collapse( dfieldPixel );
//		final CompositeIntervalView< T, ? extends GenericComposite< T > > dpix = Views.collapse( dfieldPixel );
//		final CompositeIntervalView< T, ? extends GenericComposite< T > > dphy = Views.collapse( dfieldPhysical );
		final CompositeIntervalView< T, V > dpix = ( CompositeIntervalView< T, V > ) Views.collapse( dfieldPixel );
		final CompositeIntervalView< T, V > dphy = ( CompositeIntervalView< T, V > ) Views.collapse( dfieldPhysical );
		
//		final Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( dphy ).cursor();
//		final CompositeView< T, ? extends GenericComposite< T > >.CompositeRandomAccess ra = dpix.randomAccess();
//
//		while( c.hasNext() )
//		{
//			c.fwd();
//			ra.setPosition( c );
//
//			GenericComposite< T > vphy = c.get();
//			GenericComposite< T > vpix = ra.get();
//			for( int i = 0; i < nv; i++ )
//			{
//				vphy.get( i ).setReal( resolution[ i ] * vpix.get( i ).getRealDouble() );
//			}
//		}
		
//		BiConsumer<Composite<T>,Composite<T>> fun = new BiConsumer<Composite<T>,Composite<T>>()
		BiConsumer<V,V> fun = new BiConsumer<V,V>()
				{
					@Override
					public void accept( V src, V dst )
					{
						for( int i = 0; i < nv; i++ )
						{
							dst.get( i ).setReal( resolution[ i ] * src.get( i ).getRealDouble() );
						}
					}
				};
		
		try
		{
			RenderUtil.copyToImageStackIterOrder( dpix, dphy, fun, nThreads );
		}
		catch ( InterruptedException e )
		{
			e.printStackTrace();
		}
		catch ( ExecutionException e )
		{
			e.printStackTrace();
		}
	}

}

