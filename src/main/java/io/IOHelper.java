package io;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Exception;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import io.nii.NiftiIo;
import io.nii.Nifti_Writer;
import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.plugins.BF;
import net.imglib2.Dimensions;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.bspline.BSplineLazyCoefficientsInterpolatorFactory;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converters.ClippingConverters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.outofbounds.OutOfBoundsBorderFactory;
import net.imglib2.outofbounds.OutOfBoundsConstantValueFactory;
import net.imglib2.outofbounds.OutOfBoundsFactory;
import net.imglib2.outofbounds.OutOfBoundsMirrorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.realtransform.Scale2D;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.transform.integer.MixedTransform;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.IntervalView;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import process.RenderTransformed;
import sc.fiji.io.Dfield_Nrrd_Reader;
import sc.fiji.io.Dfield_Nrrd_Writer;

@Command( version = "0.2.0-SNAPSHOT" )
public class IOHelper implements Callable<Void>
{

	ImagePlus ip;
	RandomAccessibleInterval< ? > rai;
	double[] resolution;

	@Option(names = {"--input", "-i"}, description = "Input image file" )
	private String inputFilePath;

	@Option(names = {"--output", "-o"}, description = "Output image file" )
	private String outputFilePath;

	@Option(names = {"--resolution", "-r"}, description = "Force output resolution. "
			+ "Does not resample the image, or change image data in any way", split=",")
	private double[] resIn;

	@Option(names = {"--unit", "-u"}, description = "Unit" )
	private String unit = null;

	@Option(names = {"--do-not-permute-h5" }, description = "This flag indicates that input h5 files should not have their dimensions permuted. "
			+ "By default, the order of dimensions for h5 files are read in reverse order." )
	private boolean permute = true;

	private String offsetAttribute;

	public static ResolutionGet[] resolutionGetters = new ResolutionGet[]{
			new TransformResolution(),
			new Resolution(),
			new PixelResolution(),
			new ElemSizeUmResolution()
	};

	final Logger logger = LoggerFactory.getLogger( IOHelper.class );

	public static void main( String[] args )
	{
		CommandLine.call( new IOHelper(), args );
	}

//	public void setResolutionAttribute( final String resolutionAttribute )
//	{
//		this.resolutionAttribute = resolutionAttribute;
//	}
//
//	public void setOffsetAttribute( final String offsetAttribute )
//	{
//		this.offsetAttribute = offsetAttribute;
//	}

	@Override
	public Void call()
	{
		// read
		final ImagePlus ip = readIp( inputFilePath );

		// resolution
		if( resIn != null )
			setResolution( ip, resIn );

		// units
		if( unit != null)
			ip.getCalibration().setUnit( unit );

		// write
		IOHelper.write( ip, outputFilePath );

		return null;
	}

	public static <T extends NumericType<T>> OutOfBoundsFactory<T, RandomAccessibleInterval< T > > outOfBounds( final String extendOption, T type )
	{
		switch( extendOption )
		{
		case "mirror":
			return new OutOfBoundsMirrorFactory<T, RandomAccessibleInterval< T > >( OutOfBoundsMirrorFactory.Boundary.SINGLE );
		case "mirror2":
			return new OutOfBoundsMirrorFactory<T, RandomAccessibleInterval< T > >( OutOfBoundsMirrorFactory.Boundary.DOUBLE );
		case "border":
			return new OutOfBoundsBorderFactory<>();
		}

		final T value = type.copy();
		if (value instanceof IntegerType)
		{
			final int v = Integer.parseInt(extendOption);
			((IntegerType) value).setInteger(v);
		} else if (value instanceof RealType)
		{
			final double v = Double.parseDouble(extendOption);
			((RealType) value).setReal(v);
		}
		return new OutOfBoundsConstantValueFactory<>( value );
	}

	public static <T extends NumericType<T>> ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T > > extend( RandomAccessibleInterval<T> img, String option )
	{
		switch( option )
		{
		case "mirror":
			return Views.extendMirrorSingle( img );
		case "mirror2":
			return Views.extendMirrorDouble( img );
		case "border":
			return Views.extendBorder( img );
		}

		T type = Util.getTypeFromInterval( img );

		if( type instanceof IntegerType )
		{
			type = Util.getTypeFromInterval( img );
			final int v = Integer.parseInt( option );
			((IntegerType)type).setInteger( v );
		}
		else if( type instanceof RealType )
		{
			type = Util.getTypeFromInterval( img );
			final double v = Double.parseDouble( option );
			((RealType)type).setReal( v );
		}

		return Views.extendValue( img, type );
	}

	public ValuePair< long[], double[] > readSizeAndResolution( File file )
	{
		try
		{
			return readSizeAndResolution( file.getCanonicalPath() );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		return null;
	}

	public double[] getResolution()
	{
		return resolution;
	}

	public static double[] getResolution( final N5Reader n5, final String dataset )
	{
        System.out.println("IOHelper.getResolution");
		Set<String> attrKeys;
		try {
			attrKeys = n5.listAttributes( dataset ).keySet();
			double[] resolution = null;
			for( final ResolutionGet rg : resolutionGetters )
			{
				if( attrKeys.contains( rg.getKey()))
				{
					ResolutionGet rgInstance;
					if( rg.isSimple() )
						 rgInstance = rg.create( n5.getAttribute( dataset, rg.getKey(), double[].class ) );
					else
						 rgInstance = (ResolutionGet)n5.getAttribute( dataset, rg.getKey(), rg.getClass() );

					resolution = rgInstance.getResolution();
					System.out.println( "  resolution: " + Arrays.toString( resolution ));
					return resolution;
				}
			}
		} catch (final Exception e) {
			//e.printStackTrace();
		}
		return null;
	}

	public ValuePair< long[], double[] > readSizeAndResolution( String filePath )
	{
		if ( filePath.endsWith( "nii" ) )
		{
			try
			{
				return NiftiIo.readSizeAndResolution( new File( filePath ) );
			}
			catch ( final FormatException e )
			{
				e.printStackTrace();
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
		else if ( filePath.endsWith( "nrrd" ) )
		{
			final Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
			try
			{
				return nr.readSizeAndResolution( new File( filePath ) );
			}
			catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
		else if ( filePath.contains( "n5?" ) ||
					filePath.contains( "h5?" ) || filePath.contains("hdf?") || filePath.contains("hdf5?") )
		{
			final String[] parts = filePath.split( "\\?" );
			final String file = parts[ 0 ];
			final String dataset = parts[ 1 ];

			try {

				N5Reader n5;
				if( filePath.contains( "n5?" ))
				{
					n5 = new N5FSReader( file );
					permute = false;
				}
				else
				{
					n5 = new N5HDF5Reader( file, 32, 32, 32 );
				}

				if( n5.datasetExists( dataset ))
				{
					final long[] size = (long[])n5.getAttribute( dataset, "dimensions", long[].class );

					double[] resolution = getResolution( n5, dataset );
					if( resolution == null )
					{
						resolution = new double[ size.length ];
						Arrays.fill( resolution, 1.0 );
					}

					// TODO implement this
					//double[] offset = (double[])n5.getAttribute( filePath, offsetAttribute, double[].class );

					return new ValuePair< long[], double[] >( size, resolution );
				}
				else
					return null;

			} catch (final N5Exception e)
			{
				e.printStackTrace();
				return null;
			}
		}
		else
		{
			try
			{
				final ImageReader reader = new ImageReader();

				reader.setId( filePath );

				final int nz = reader.getSizeZ();
				long[] size;
				if( nz <= 1 )
				{
					size = new long[] { reader.getSizeX(), reader.getSizeY() };
				}
				else
				{
					size = new long[] { reader.getSizeX(), reader.getSizeY(), reader.getSizeZ() };
				}

				final Hashtable< String, Object > globalMeta = reader.getGlobalMetadata();

				final double[] resolutions = new double[ size.length ];
				Arrays.fill( resolutions, 1.0 );

				if( globalMeta.containsKey( "XResolution" ) && resolutions.length > 0 )
					resolutions[ 0 ] = 1 / ((double)globalMeta.get( "XResolution" ));

				if( globalMeta.containsKey( "YResolution" ) && resolutions.length > 1 )
					resolutions[ 1 ] = 1 / ((double)globalMeta.get( "YResolution" ));

				if( globalMeta.containsKey( "ZResolution" ) && resolutions.length > 2 )
					resolutions[ 2 ] = 1 / ((double)globalMeta.get( "ZResolution" ));

				reader.close();
				return new ValuePair< long[], double[] >( size, resolutions );

			}
			catch ( final Exception e )
			{
				e.printStackTrace();
			}
		}
		return null;
	}

	public ImagePlus readIp( File file )
	{
		try
		{
			return readIp( file.getCanonicalPath() );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		return null;
	}

	public ImagePlus readIp( String filePathAndDataset )
	{
		if( filePathAndDataset.endsWith( "nii" ))
		{
			try
			{
				ip =  NiftiIo.readNifti( new File( filePathAndDataset ) );
			} catch ( final FormatException e )
			{
				e.printStackTrace();
			} catch ( final IOException e )
			{
				e.printStackTrace();
			}
		}
		else if( filePathAndDataset.endsWith( "nrrd" ))
		{
			final Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
			final File imFile = new File( filePathAndDataset );

			ip = nr.load( imFile.getParent(), imFile.getName());
		}
		else if( filePathAndDataset.contains( ".h5" )  ||
				 filePathAndDataset.contains( ".n5" ) )
		{
			ip = toImagePlus( readRai( filePathAndDataset ));
		}
		else if (new File(filePathAndDataset).isDirectory()) {
			ip = FolderOpener.open(filePathAndDataset);
		}
		else
		{
			try
			{
				ip = IJ.openImage( filePathAndDataset );
			}
			catch( final Exception e )
			{
				e.printStackTrace();
			}

			if ( ip == null )
			{
				try
				{
					ip = BF.openImagePlus( filePathAndDataset )[0];
				}
				catch( final Exception e )
				{
					e.printStackTrace();
				}
			}
		}

		final int nd = ip.getNSlices() > 1 ? 3 : 2;
		resolution = new double[ nd ];
		resolution[ 0 ] = ip.getCalibration().pixelWidth;
		resolution[ 1 ] = ip.getCalibration().pixelHeight;

		if( nd > 2)
			resolution[ 2 ] = ip.getCalibration().pixelDepth;

		return ip;
	}

	public static void setResolution( ImagePlus ip, double[] res )
	{
		ip.getCalibration().pixelWidth  = res[ 0 ];
		ip.getCalibration().pixelHeight = res[ 1 ];
		ip.getCalibration().pixelDepth  = res[ 2 ];
	}

	@SuppressWarnings( "unchecked" )
	public <T extends RealType<T> & NativeType<T>> T getType()
	{
		if( rai != null )
			return ( T ) Util.getTypeFromInterval( rai );

		else if( ip != null )
		if( ip.getBitDepth() == 8 )
		{
			return ( T ) new UnsignedByteType();
		}
		else if( ip.getBitDepth() == 16 )
		{
			return ( T ) new UnsignedShortType();
		}
		else if( ip.getBitDepth() == 32 )
		{
			return ( T ) new FloatType();
		}
		return null;
	}

	public ImagePlus getIp()
	{
		return ip;
	}

	@SuppressWarnings( "unchecked" )
	public <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval< T > getRai()
	{
		return ( RandomAccessibleInterval< T > ) rai;
	}

	//public <T extends RealType<T> & NativeType<T>> ImagePlus toImagePlus( RandomAccessibleInterval<T> img )
	public ImagePlus toImagePlus( RandomAccessibleInterval<?> img )
	{
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final
		ImagePlus ipout = ImageJFunctions.wrap( (RandomAccessibleInterval<NumericType>) img, "img", null );
		setResolution( ipout, resolution );
		return ipout;
	}

	public <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> readRai( File file )
	{
		try
		{
			return readRai( file.getCanonicalPath() );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * If the file
	 * The input string should contain both the absolute file path and dataset into the h5 file separated by a colon (":"),
	 * E.g. "/tmp/myfile.h5:mydataset"
	 *
	 * @param filePathAndDataset the file and dataset string
	 * @return the image
	 */
	public <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> readRai( String filePathAndDataset )
	{
		if ( filePathAndDataset.contains( "n5?" ) ||
					filePathAndDataset.contains( "h5?" ) ||
					filePathAndDataset.contains("hdf?") ||
					filePathAndDataset.contains("hdf5?") )
		{
			final String[] partList = filePathAndDataset.split( "\\?" );
			final String fpath = partList[ 0 ];
			final String dset = partList[ 1 ];

			logger.debug( "fpath: " + fpath );
			logger.debug( "dset: " + dset );

			RandomAccessibleInterval<T> img = null;
			try
			{
				N5Reader n5;
				if( filePathAndDataset.contains( "n5?" ))
				{
					n5 = new N5FSReader( fpath );
					permute = false;
				}
				else
				{
					n5 = new N5HDF5Reader( fpath, 32, 32, 32 );
				}

				final RandomAccessibleInterval<T> tmp = N5Utils.open( n5, dset );
				resolution = getResolution( n5, dset );

				if( permute )
				{
					img = reverseDims( tmp, resolution );
				}
				else
					img = tmp;

//				float[] rtmp = n5.getAttribute( dset, "element_size_um", float[].class );
//				if( rtmp != null )
//				{
//					resolution = new double[ 3 ];
//					// h5 attributes are usually listed zyx not xyz
//					resolution[ 0 ] = rtmp[ 2 ];
//					resolution[ 1 ] = rtmp[ 1 ];
//					resolution[ 2 ] = rtmp[ 0 ];
//				}
//				else
//					resolution = new double[]{ 1, 1, 1 };
			}
			catch ( final N5Exception e )
			{
				e.printStackTrace();
			}

			rai = img;
			return img;
		}
		else
		{
			rai = ImageJFunctions.wrap( readIp( filePathAndDataset ) );
			return ( RandomAccessibleInterval< T > ) rai;
		}
	}

    /**
     * Permutes the dimensions of a {@link RandomAccessibleInterval}
     * using the given permutation vector, where the ith value in p
     * gives destination of the ith input dimension in the output.
     *
     * @param source the source data
     * @param p the permutation
     * @return the permuted source
     */
	public static final < T > IntervalView< T > permute( RandomAccessibleInterval< T > source, int[] p )
	{
		final int n = source.numDimensions();

		final long[] min = new long[ n ];
		final long[] max = new long[ n ];
		for ( int i = 0; i < n; ++i )
		{
			min[ p[ i ] ] = source.min( i );
			max[ p[ i ] ] = source.max( i );
		}

		final MixedTransform t = new MixedTransform( n, n );
		t.setComponentMapping( p );

		return Views.interval( new MixedTransformView< T >( source, t ), min, max );
	}

    /**
     * Permutes the dimensions of a {@link RandomAccessibleInterval}
     * using the given permutation vector, where the ith value in p
     * gives destination of the ith input dimension in the output.
     *
     * @param source the source data
     * @param p the permutation
     * @return the permuted source
     */
	public static final < T > IntervalView< T > reverseDims( RandomAccessibleInterval< T > source, double[] res )
	{
		assert source.numDimensions() == res.length;

		final int n = source.numDimensions();
		final double[] tmp  = new double[ n ];

		final int[] p = new int[ n ];
		for ( int i = 0; i < n; ++i )
		{
			p[ i ] = n - 1 - i;
			tmp[ i ] = res[ n - i - 1 ];
		}
		System.arraycopy( tmp, 0, res, 0, n );

		return permute( source, p );
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > interpolate(
			final RandomAccessible<T> img,
			final String interp )
	{
		return Views.interpolate( img,
				RenderTransformed.getInterpolator( interp, img ));
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > interpolateExtend(
			final RandomAccessibleInterval<T> img,
			final String interp,
			final String extendOption )
	{
		if( RenderTransformed.INTERP_OPTIONS.valueOf(interp) == RenderTransformed.INTERP_OPTIONS.BSPLINE )
		{
			final int nd = img.numDimensions();
			final int[] blockSize = new int[ nd ];
			if( nd == 1 )
				Arrays.fill( blockSize, 256);
			if( nd == 2 )
				Arrays.fill( blockSize, 128);
			else if( nd == 3 )
				Arrays.fill( blockSize, 64);
			else
				Arrays.fill( blockSize, 32);

			final OutOfBoundsFactory<T, RandomAccessibleInterval<T>> oobFactory = outOfBounds( extendOption, Util.getTypeFromInterval(img));

			final DoubleType doubleType = new DoubleType();
			final T outType = Util.getTypeFromInterval( img );

			final BSplineLazyCoefficientsInterpolatorFactory<T, DoubleType> interpFactory = new BSplineLazyCoefficientsInterpolatorFactory<T,DoubleType>(
					img, img, 3, true, doubleType,
					blockSize, oobFactory );

			final RealRandomAccessible<DoubleType> imgInterp = Views.interpolate(img, interpFactory );
			final Converter<DoubleType,T> conv = ClippingConverters.getConverter( doubleType, outType );
			return Converters.convert(imgInterp, conv, outType);
		}
		else
		{
			final ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval<T>> imgExt = extend( img, extendOption );
			return Views.interpolate(imgExt, RenderTransformed.getInterpolator(interp, img));
		}
	}

	public static AffineGet pixelToPhysicalN5( final N5Reader n5, final String dataset )
	{
		try {
			if( n5.datasetExists( dataset ))
			{
				final long[] size = (long[])n5.getAttribute( dataset, "dimensions", long[].class );
				final int nd = size.length;

//				double[] resolutions = (double[])n5.getAttribute( dataset, resolutionAttribute, double[].class );
//				if( resolutions == null )
//				{
//					resolutions = new double[ size.length ];
//					Arrays.fill( resolutions, 1.0 );
//				}

				final Set<String> attrKeys = n5.listAttributes( dataset ).keySet();
				double[] resolution = null;
                try
                {
                    for( final ResolutionGet rg : resolutionGetters )
                    {
                        if( attrKeys.contains( rg.getKey()))
                        {
                            ResolutionGet rgInstance;
                            if( rg.isSimple())
                                rgInstance = rg.create( n5.getAttribute( dataset, rg.getKey(), double[].class ));
                            else
                                rgInstance = (ResolutionGet)n5.getAttribute( dataset, rg.getKey(), rg.getClass() );

                            resolution = rgInstance.getResolution();
                            System.out.println( "PARSED RESOLUTION: " + Arrays.toString( resolution ));
                            break;
                        }
                    }
                } catch( final Exception e ){}

				if( resolution == null )
				{
					resolution = new double[ size.length ];
					Arrays.fill( resolution, 1.0 );
				}

				// TODO implement
//				double[] offset = (double[])n5.getAttribute( dataset, offsetAttribute, double[].class );
				final double[] offset = null;

				if ( nd == 1 )
				{
					final AffineTransform affine = new AffineTransform( 1 );
					affine.set( resolution[ 0] , 0, 0 );
					//affine.set( offset[ 0 ], 0, 1 ); // TODO
					return affine;
				}
				else if ( nd == 2 && offset == null )
				{
					return new Scale2D( resolution );
				}
				else if ( nd == 3 && offset == null )
				{
					return new Scale3D( resolution );
				}
//				else if ( nd == 2 && offset != null )
//				{
//					AffineTransform2D affine = new AffineTransform2D();
//				}
////				else if ( nd == 3 && offset != null )
//				{
//					AffineTransform3D affine = new AffineTransform3D();
//				}

				return null;

			}
		}
		catch( final N5Exception e )
		{
			e.printStackTrace();
		}
		return null;
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > readPhysical(
			final File file,
			final String interp )
	{
		return readPhysical( file, interp, "0" );
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > readPhysical(
			final File file,
			final String interp,
			final String extendOption )
	{
		try
		{
			return readPhysical( file.getCanonicalPath(), interp, extendOption );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		return null;
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > readPhysical(
			final String filePathAndDataset,
			final String interp )
	{
		return readPhysical( filePathAndDataset, interp, "0" );
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > readPhysical(
			final String filePathAndDataset,
			final String interp,
			final String extendOption )
	{
		final RandomAccessibleInterval< T > rai = readRai( filePathAndDataset );
//		RealRandomAccessible< T > realimgpixel = interpolate( extend( rai, extendOption ), interp );
		final RealRandomAccessible<T> realimgpixel = interpolateExtend( rai, interp, extendOption );
		if( resolution == null )
			return realimgpixel;

		final Scale pixelToPhysical = new Scale( resolution );
		return RealViews.affine( realimgpixel, pixelToPhysical );
	}

	public < T extends RealType< T > & NativeType< T > > RealRandomAccessible< T > readPhysical(
			final String filePathAndDataset,
			final InterpolatorFactory< T, RandomAccessible<T> > interp )
	{
		rai = readRai( filePathAndDataset );
		final RealRandomAccessible< T > realimgpixel = Views.interpolate( getRai(), interp );
		if( resolution == null )
			return realimgpixel;

		final Scale pixelToPhysical = new Scale( resolution );
		return RealViews.affine( realimgpixel, pixelToPhysical );
	}

	public static void write( ImagePlus ip, File outputFile )
	{

		write(ip, outputFile, 1);
	}

	public static void write(ImagePlus ip, File outputFile, int nThreads) {
		try
		{
			write(ip, outputFile.getCanonicalPath(), nThreads);
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
	}

	public static void write(ImagePlus ip, String outputFilePath) {

		write(ip, outputFilePath, 1);
	}

	public static void write(ImagePlus ip, String outputFilePath, int nThreads)
	{
		if( outputFilePath.endsWith( "nii" ))
		{
			final File f = new File( outputFilePath );

			boolean is_displacement = false;
			if( ip.getDimensions()[ 2 ] == 3 )
				is_displacement = true;

			final Nifti_Writer writer = new Nifti_Writer( is_displacement );
			writer.save( ip, f.getParent(), f.getName() );
		}
		else if( outputFilePath.endsWith( "nrrd" ))
		{
			final File f = new File( outputFilePath );
			final Dfield_Nrrd_Writer writer = new Dfield_Nrrd_Writer();
			writer.save( ip, f.getParent(), f.getName() );
		}
		else if( outputFilePath.endsWith( "hdf5" ) || outputFilePath.endsWith("h5") || outputFilePath.endsWith( "n5" ) ||
				outputFilePath.contains("hdf5:") || outputFilePath.contains("h5:") || outputFilePath.contains("n5:") ||
				 outputFilePath.contains( "zarr:" ) || outputFilePath.endsWith("zarr"))
		{

			String dataset = "/volume/raw";
			String path = outputFilePath;
			if( outputFilePath.contains( ":" ))
			{
				final String[] split = outputFilePath.split( ":" );
				path = split[ 0 ];
				dataset = split[ 1 ];
			}

			try
			{
				final N5Writer n5Writer = new N5Factory().openWriter(path);

				if (n5Writer == null) {
					System.err.println("Could not create an n5 writer from path: " + path );
					return;
				}

				// TODO add more options
				final int[] blockSize = new int[]{64, 64, 64};
				final GzipCompression compression = new GzipCompression();

				final RandomAccessibleInterval img = ImageJFunctions.wrap( ip );
				N5Utils.save(img, n5Writer, dataset, blockSize, compression, Executors.newFixedThreadPool(nThreads));
			}
			catch( final Exception e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			IJ.save( ip, outputFilePath );
		}
	}

	public static interface ResolutionGet
	{
		public String getKey();
		public double[] getResolution();
		public boolean isSimple();
		public ResolutionGet create( double[] in );
	}

	public static class PixelResolution implements ResolutionGet
	{
		public static final String key = "pixelResolution";
		public double[] dimensions;
		public String unit;
		@Override
		public double[] getResolution(){ return dimensions; }
		@Override
		public String getKey(){ return key; };
		@Override
		public boolean isSimple(){ return false; }
		@Override
		public ResolutionGet create( double[] in ){ return null; }
	}

	public static class Resolution implements ResolutionGet
	{
		public static final String key = "resolution";
		public static final boolean simple = true;
		public Resolution(){}
		public Resolution( double[] in ) { resolution = in; }
		public double[] resolution;
		@Override
		public double[] getResolution(){ return resolution; }
		@Override
		public String getKey(){ return key; };
		@Override
		public boolean isSimple(){ return true; }
		@Override
		public ResolutionGet create( double[] in ){ return new Resolution( in ); }
	}

	public static class ElemSizeUmResolution implements ResolutionGet
	{
		public static final String key = "element_size_um";
		public static final boolean simple = true;
		public ElemSizeUmResolution(){}
		public ElemSizeUmResolution( double[] in ) { element_size_um = in; }
		public double[] element_size_um;
		@Override
		public double[] getResolution(){ return element_size_um; }
		@Override
		public String getKey(){ return key; };
		@Override
		public boolean isSimple(){ return true; }
		@Override
		public ResolutionGet create( double[] in ){ return new ElemSizeUmResolution( in ); }
	}

    public static class TransformResolution implements ResolutionGet
    {
        public static final String key = "transform";
        public static final boolean simple = false;
        public double[] scale;
        public double[] translation;
        public String[] units;
        public String[] axes;
        public TransformResolution(){}
        public TransformResolution( double[] s, double[] t, String[] u, String[] a )
        {
            scale = s;
            translation = t;
            units = u;
            axes = a;
        }
        @Override
		public double[] getResolution(){ return reverse(scale); }
        @Override
		public String getKey(){ return key; };
        @Override
		public boolean isSimple(){ return simple; }

		@Override
		public ResolutionGet create(double[] in) {
			final int N = in.length;
			final String[] units = new String[N];
			Arrays.fill(units, "pixel");
			return new TransformResolution(reverse(in), new double[in.length], units, new String[] { "z", "y", "x" });
		}

		public static double[] reverse(double[] in) {
			final int N = in.length;
			final double[] out = new double[N];
			int j = N - 1;
			for (int i = 0; i < N; i++) {
				out[j--] = in[i];
			}
			return out;
		}
    }

	public static RealInterval toRealInterval(Dimensions dims, double[] spacing) {
		final int nd = dims.numDimensions();
		final double[] min = new double[nd];
		final double[] max = new double[nd];
		for (int i = 0; i < nd; i++) {
			max[i] = dims.dimension(i) * spacing[i];
		}

		return new FinalRealInterval(min, max);
	}

	public static RealInterval toRealInterval(long[] dim, double[] spacing) {
		final int nd = dim.length;
		final double[] min = new double[nd];
		final double[] max = new double[nd];
		for (int i = 0; i < nd; i++) {
			max[i] = dim[i] * spacing[i];
		}

		return new FinalRealInterval(min, max);
	}

}
