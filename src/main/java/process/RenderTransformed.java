package process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;
import org.janelia.utility.parse.ParseUtils;

import bigwarp.landmarks.LandmarkTableModel;
import ij.IJ;
import ij.ImagePlus;
import io.AffineImglib2IO;
import io.IOHelper;
import io.cmtk.CMTKLoadAffine;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.DeformationFieldTransform;
import net.imglib2.realtransform.RealTransformAsInverseRealTransform;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import sc.fiji.io.Dfield_Nrrd_Reader;
import util.RenderUtil;

/**
 * Renders images transformed/registered.
 * 
 * Currently the final (implied) upsampling transform is hard-coded in : it resmples up by a factor of 4 in x and y and 2 in z.
 * After that, it upsamples z again by a factor of (~ 2.02) to resample the images to a resolution of 0.188268 um isotropic.
 * 
 * 
 * @author John Bogovic
 *
 */
public class RenderTransformed
{
	
	public enum INTERP_OPTIONS { LINEAR, NEAREST };

	public static void main( String[] args ) throws FormatException, Exception
	{

		String imF = args[ 0 ];
		String outF = args[ 1 ];
		String outputIntervalF = args[ 2 ];
		
		System.out.println( "imf: " + imF );
		// LOAD THE IMAGE

		ImagePlus ip = null;
		if( imF.endsWith( "nii" ))
		{
			ip = NiftiIo.readNifti( new File( imF ));
		}
		else
		{
			ip = IJ.openImage( imF );
		}

		ImagePlus baseIp = null;
		if( imF.endsWith( "nii" ))
		{
			try
			{
				baseIp =  NiftiIo.readNifti( new File( imF ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if( imF.endsWith( "nrrd" ))
		{
			Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
			File imFile = new File( imF );

			baseIp = nr.load( imFile.getParent(), imFile.getName());
			System.out.println( "baseIp");
		}
		else
		{
			baseIp = IJ.openImage( imF );
		}

		double rx = ip.getCalibration().pixelWidth;
		double ry = ip.getCalibration().pixelHeight;
		double rz = ip.getCalibration().pixelDepth;

		AffineTransform3D resInXfm = null;
		if( rx == 0 ){
			rx = 1.0;
			System.err.println( "WARNING: rx = 0 setting to 1.0" );
		}
		if( ry == 0 ){
			ry = 1.0;
			System.err.println( "WARNING: ry = 0 setting to 1.0" );
		}
		if( rz == 0 ){
			rz = 1.0;
			System.err.println( "WARNING: rz = 0 setting to 1.0" );
		}

		if( rx != 1.0  || ry != 1.0 || rz != 1.0 )
		{
			resInXfm = new AffineTransform3D();
			resInXfm.set( 	rx, 0.0, 0.0, 0.0, 
					  		0.0, ry, 0.0, 0.0, 
					  		0.0, 0.0, rz, 0.0 );
			System.out.println( "transform for input resolutions : " + resInXfm );
		}

		FinalInterval renderInterval = null;
		if( outputIntervalF.equals("infer"))
		{
			System.out.println("trying to infer output interval");
			renderInterval = inferOutputInterval( args, baseIp, new double[]{ rx, ry, rz });
			System.out.println("Rendering to interval: " + Util.printInterval( renderInterval ));
		}
		else
		{
			renderInterval = parseInterval( outputIntervalF );
		}

		System.out.println( "writing to: " + outF );

		System.out.println("allocating");
		ImagePlus ipout = null;
		if( baseIp.getBitDepth() == 8 )
		{
			ImagePlusImg< UnsignedByteType, ? > out = ImagePlusImgs.unsignedBytes(  
					renderInterval.dimension( 0 ),
					renderInterval.dimension( 1 ),
					renderInterval.dimension( 2 ));

			ipout = doIt( ImageJFunctions.wrapByte( baseIp ), out, resInXfm, args, renderInterval );
		}
		else if( baseIp.getBitDepth() == 16 )
		{
			ImagePlusImg< UnsignedShortType, ? > out = ImagePlusImgs.unsignedShorts( 
					renderInterval.dimension( 0 ),
					renderInterval.dimension( 1 ),
					renderInterval.dimension( 2 ));

			
			ipout = doIt( ImageJFunctions.wrapShort( baseIp ), out, resInXfm, args, renderInterval );
		}
		else if( baseIp.getBitDepth() == 32 )
		{
			ImagePlusImg< FloatType, ? > out = ImagePlusImgs.floats( 
					renderInterval.dimension( 0 ),
					renderInterval.dimension( 1 ),
					renderInterval.dimension( 2 ));

			ipout = doIt( ImageJFunctions.wrapFloat( baseIp ), out, resInXfm, args, renderInterval );
		}
		else{
			return;
		}

		System.out.println("saving to: " + outF );
		IOHelper.write( ipout, outF );
	}
	
	public static final <T extends RealType<T> & NativeType<T>> InvertibleRealTransform openH5Xfm( 
			final N5Reader n5,
			final String dataset,
			final boolean inverse,
			final T defaultType,
			final InterpolatorFactory< T, RandomAccessible<T> > interpolator ) throws Exception
	{

		AffineGet affine = N5DisplacementField.openAffine( n5, dataset );

		DeformationFieldTransform< T > dfield = new DeformationFieldTransform<>(
				N5DisplacementField.openCalibratedField( n5, dataset, interpolator, defaultType ));
		
		if( affine != null )
		{
			InvertibleRealTransformSequence xfmSeq = new InvertibleRealTransformSequence();
			if( inverse )
			{
				xfmSeq.add( new RealTransformAsInverseRealTransform( dfield ));
				xfmSeq.add( affine.inverse() );
			}
			else
			{
				xfmSeq.add( affine.inverse() );
				xfmSeq.add( new RealTransformAsInverseRealTransform( dfield ));
			}
			return xfmSeq;
		}
		else
		{
			return null;
		}
	}

	public static InvertibleRealTransform loadTransform( String filePath, boolean invert ) throws Exception
	{
		if( filePath.endsWith( "h5" ))
		{

			String dataset = invert ? "invdfield" : "dfield";
			N5HDF5Reader n5 = new N5HDF5Reader( filePath, 3, 32, 32, 32 );

			// THIS DOES NOT WORK with multiple threads
//			RealTransform xfm = N5DisplacementField.open( n5, dataset, invert,
//					new FloatType(),
//					new NLinearInterpolatorFactory<FloatType>());
//			RealTransformAsInverseRealTransform ixfm = new RealTransformAsInverseRealTransform( xfm );
//			return ixfm;

			InvertibleRealTransform ixfm = openH5Xfm( n5, dataset, invert,
					new FloatType(),
					new NLinearInterpolatorFactory<FloatType>());
			return ixfm;
	
		}
		else if( filePath.endsWith( "csv" ))
		{
			System.out.println("READING BIGWARP TRANSFORM");
			LandmarkTableModel ltm = new LandmarkTableModel( 3 );
			ltm.load( new File( filePath ));
			return new InverseRealTransform( 
					new WrappedIterativeInvertibleRealTransform< ThinplateSplineTransform >( 
							new ThinplateSplineTransform( ltm.getTransform() )));
		}
		else if( filePath.endsWith( "mat" ))
		{
			try
			{
				AffineTransform3D xfm = ANTSLoadAffine.loadAffine( filePath );
				if( invert )
				{
					return xfm.inverse().copy();
				}

				return xfm;
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if( filePath.endsWith( "xform" ))
		{
			try
			{
				CMTKLoadAffine reader = new CMTKLoadAffine();
				AffineTransform3D xfm = reader.load( new File( filePath ));
				if( invert )
				{
					return xfm.inverse().copy();
				}

				return xfm;
			}
			catch( Exception e )
			{
				e.printStackTrace();
			}
		}
		else if( filePath.endsWith( "txt" ))
		{
			if( Files.readAllLines( Paths.get( filePath ) ).get( 0 ).startsWith( "#Insight Transform File" ))
			{
				System.out.println("Reading itk transform file");
				try
				{
					AffineTransform3D xfm = ANTSLoadAffine.loadAffine( filePath );
					if( invert )
					{
						System.out.println("inverting");
						return xfm.inverse().copy();
					}
					return xfm;
				} catch ( IOException e )
				{
					e.printStackTrace();
				}
			}
			else
			{
				System.out.println("Reading imglib2 transform file");
				try
				{
					AffineTransform xfm = AffineImglib2IO.readXfm( 3, new File( filePath ) );
					System.out.println( Arrays.toString(xfm.getRowPackedCopy() ));
					if( invert )
					{
						System.out.println("inverting");
						return xfm.inverse().copy();
					}
					return xfm;
				} catch ( IOException e )
				{
					e.printStackTrace();
				}
			}
		}
		else
		{
			ImagePlus displacementIp = null;
			if( filePath.endsWith( "nii" ))
			{
				try
				{
					displacementIp =  NiftiIo.readNifti( new File( filePath ) );
				} catch ( FormatException e )
				{
					e.printStackTrace();
				} catch ( IOException e )
				{
					e.printStackTrace();
				}
			}
			else if( filePath.endsWith( "nrrd" ))
			{
				//Nrrd_Reader_4d reader = new Nrrd_Reader_4d();
				Dfield_Nrrd_Reader reader = new Dfield_Nrrd_Reader();
				File tmp = new File( filePath );
				displacementIp = reader.load( tmp.getParent(), tmp.getName() );
			}
			else
			{
				displacementIp = IJ.openImage( filePath );
			}

			if( displacementIp == null )
				return null;

			ANTSDeformationField dfield = new ANTSDeformationField( displacementIp );
			System.out.println( "DISPLACEMENT INTERVAL: " + Util.printInterval( dfield.getDefInterval() ));

			if( invert )
			{
//				System.out.println( "WARNING: ITERATIVE INVERSE OF DISPLACEMENT FIELD IS UNTESTED ");
//				InvertibleDeformationFieldTransform<FloatType> invxfm = new InvertibleDeformationFieldTransform<FloatType>(
//						new DeformationFieldTransform<FloatType>( dfield.getDefField() ));
//
//				return new InverseRealTransform( invxfm );
				System.err.println("Inverse deformation fields are not yet supported.");
				return null;
			}
			return dfield;
		}
		return null;
	}

	public static FinalInterval inferOutputInterval( String[] args, Interval pixelInterval, double[] resIn )
	{
		int i = 0;
		AffineTransform3D resOutXfm = null;
		while( i < args.length )
		{

			if( args[ i ].equals( "-r" ))
			{
				i++;
				double[] outputResolution = ParseUtils.parseDoubleArray( args[ i ] );
				i++;

				resOutXfm = new AffineTransform3D();
				resOutXfm.set( 	outputResolution[ 0 ], 0.0, 0.0, 0.0, 
								0.0, outputResolution[ 1 ], 0.0, 0.0, 
								0.0, 0.0, outputResolution[ 2 ], 0.0 );

				System.out.println( "output Resolution " + Arrays.toString( outputResolution ));
				continue;
			}
			i++;
		}

		return new FinalInterval( 
				(long)Math.round( pixelInterval.dimension( 0 ) * resIn[ 0 ] / resOutXfm.get( 0, 0 )),
				(long)Math.round( pixelInterval.dimension( 1 ) * resIn[ 1 ] / resOutXfm.get( 1, 1 )),
				(long)Math.round( pixelInterval.dimension( 2 ) * resIn[ 2 ] / resOutXfm.get( 2, 2 )));
	}
	
	public static FinalInterval inferOutputInterval( String[] args, ImagePlus ip, double[] resIn )
	{
		int i = 0;
		AffineTransform3D resOutXfm = null;
		while( i < args.length )
		{

			if( args[ i ].equals( "-r" ))
			{
				i++;
				double[] outputResolution = ParseUtils.parseDoubleArray( args[ i ] );
				i++;

				resOutXfm = new AffineTransform3D();
				resOutXfm.set( 	outputResolution[ 0 ], 0.0, 0.0, 0.0, 
								0.0, outputResolution[ 1 ], 0.0, 0.0, 
								0.0, 0.0, outputResolution[ 2 ], 0.0 );

				System.out.println( "output Resolution " + Arrays.toString( outputResolution ));
				continue;
			}
			i++;
		}

		return new FinalInterval( 
				(long)Math.round( ip.getWidth() * resIn[ 0 ] / resOutXfm.get( 0, 0 )),
				(long)Math.round( ip.getHeight() * resIn[ 1 ] / resOutXfm.get( 1, 1 )),
				(long)Math.round( ip.getNSlices() * resIn[ 2 ] / resOutXfm.get( 2, 2 )));
	}
	
	public static <T extends NumericType<T> & NativeType<T> > InterpolatorFactory<T,RandomAccessible<T>> getInterpolator( String option,
			RandomAccessible<T> ra )
	{
		if( INTERP_OPTIONS.valueOf(option) == INTERP_OPTIONS.LINEAR )
		{
			return new NLinearInterpolatorFactory<T>();	
		}
		else if( INTERP_OPTIONS.valueOf(option) == INTERP_OPTIONS.NEAREST )
		{
			return new NearestNeighborInterpolatorFactory<T>();
		}
		else 
			return null;
	}

	public static  <T extends NumericType<T> & NativeType<T> > ImagePlus run(
			Img<T> baseImg,
			ImagePlusImg< T, ? > out,
			AffineTransform3D resInXfm,
			FinalInterval renderInterval,
			InvertibleRealTransform xfm,
			double[] outputResolutions,
			InterpolatorFactory<T,RandomAccessible<T>> interp,
			int nThreads )
	{
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();

		if( resInXfm != null )
			totalXfm.add( resInXfm );

		totalXfm.add( xfm );
		
		AffineTransform3D resOutXfm;
		if( outputResolutions != null )
		{
			resOutXfm = new AffineTransform3D();
			resOutXfm.set( 	outputResolutions[ 0 ], 0.0, 0.0, 0.0, 
					  		0.0, outputResolutions[ 1 ], 0.0, 0.0, 
					  		0.0, 0.0, outputResolutions[ 2 ], 0.0 );
			totalXfm.add( resOutXfm.inverse() );
		}
		
//		double[] testPtPixel = new double[]{ 155, 54, 54 };
//		double[] testPtPixelResult = new double[ 3 ];
//		totalXfm.applyInverse( testPtPixelResult, testPtPixel );
//		System.out.println( "testPtPixel      : " + Arrays.toString( testPtPixel ) );
//		System.out.println( "testPtPixelResult: " + Arrays.toString( testPtPixelResult ) );
//		
//		System.out.println("\n\n\n");
//		double[] p = new double[]{ 465, 162, 162 };
//		double[] q = new double[ 3 ];
//		xfm.applyInverse( q, p );
//		System.out.println( "p : " + Arrays.toString( p ) );
//		System.out.println( "q : " + Arrays.toString( q ) );

		System.out.println("transforming");
		IntervalView< T > imgHiXfm = Views.interval( 
				Views.raster( 
					RealViews.transform(
							Views.interpolate( Views.extendZero( baseImg ), interp ),
							totalXfm )),
				renderInterval );

		IntervalView< T > outTranslated = Views.translate( out,
				renderInterval.min( 0 ),
				renderInterval.min( 1 ),
				renderInterval.min( 2 ));
		
//		RandomAccess<T> testra = imgHiXfm.randomAccess();
//		testra.setPosition( new int[]{ 155, 54, 54 });
//		T t  = testra.get();
//		System.out.println( "interpolated value at: " + t );

		
		System.out.println("copying with " + nThreads + " threads");
		RenderUtil.copyToImageStack( imgHiXfm, outTranslated, nThreads );

		ImagePlus ipout = null;
		try
		{
			ipout = out.getImagePlus();
		}
		catch ( ImgLibException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (outputResolutions != null )
		{
			ipout.getCalibration().pixelWidth  = outputResolutions[ 0 ];
			ipout.getCalibration().pixelHeight = outputResolutions[ 1 ];
			ipout.getCalibration().pixelDepth  = outputResolutions[ 2 ];
		}
		if( ipout.getNSlices() == 1 && ipout.getNChannels() > 1 )
		{
			ipout.setDimensions(  ipout.getNSlices(),  ipout.getNChannels(), ipout.getNFrames() );
		}

		return ipout;
	}
	
	public static InvertibleRealTransformSequence parseAndReadTransforms(
			final String[] args )
	{
//		String arg = " ";
//		arg.split(" ");
//		
//		ImagePlus ip = null;
//		FinalInterval renderInterval = new FinalInterval(
//				ip.getWidth(), ip.getHeight(), ip.getNSlices()
//				);
//		
//		double[] a = new double[]{ ip.getCalibration().pixelWidth, 
//			ip.getCalibration().pixelHeight,
//			ip.getCalibration().pixelDepth
//		}
		
		return null;

	}

	public static  <T extends NumericType<T> & NativeType<T> > ImagePlus doIt(
			Img<T> baseImg,
			ImagePlusImg< T, ? > out,
			AffineTransform3D resInXfm,
			String[] args,
			FinalInterval renderInterval ) throws Exception
	{
		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();

		int nThreads = 8;
		InterpolatorFactory<T,RandomAccessible<T>> interp =  new NLinearInterpolatorFactory<T>();

		int i = 3;
		double[] outputResolution = null;
		while( i < args.length )
		{
			boolean invert = false;
			if( args[ i ].equals( "-i" ))
			{
				invert = true;
				i++;
			}

			if( args[ i ].equals( "-q" ))
			{
				i++;
				nThreads = Integer.parseInt( args[ i ] );
				i++;
				System.out.println( "argument specifies " + nThreads + " threads" );
				continue;
			}

			if( args[ i ].equals( "-t" ))
			{
				i++;
				String interpArg = args[ i ].toLowerCase();
				if( interpArg.equals( "nearest" ) || interpArg.equals( "near"))
					 interp =  new NearestNeighborInterpolatorFactory<T>();

				i++;
				System.out.println( "using" + interp + " interpolation" );
				continue;
			}

			if( args[ i ].equals( "-r" ))
			{
				i++;
				outputResolution = ParseUtils.parseDoubleArray( args[ i ] );
				i++;

				System.out.println( "output Resolution " + Arrays.toString( outputResolution ));
				continue;
			}

			if( invert )
				System.out.println( "loading transform from " + args[ i ] + " AND INVERTING" );
			else
				System.out.println( "loading transform from " + args[ i ]);

			InvertibleRealTransform xfm = loadTransform( args[ i ], invert );

			if( xfm == null )
			{
				System.err.println("  failed to load transform ");
				System.exit( 1 );
			}

			totalXfm.add( xfm );
			i++;
		}

		return run( baseImg, out, resInXfm, renderInterval, totalXfm, outputResolution, interp, nThreads );
	}

	public static FinalInterval parseInterval( String outSz )
	{
		FinalInterval destInterval = null;
		if ( outSz.contains( ":" ) )
		{
			String[] minMax = outSz.split( ":" );
			System.out.println( " " + minMax[ 0 ] );
			System.out.println( " " + minMax[ 1 ] );

			long[] min = ParseUtils.parseLongArray( minMax[ 0 ] );
			long[] max = ParseUtils.parseLongArray( minMax[ 1 ] );
			destInterval = new FinalInterval( min, max );
		} else
		{
			long[] outputSize = ParseUtils.parseLongArray( outSz );
			destInterval = new FinalInterval( outputSize );
		}
		return destInterval;
	}
}
