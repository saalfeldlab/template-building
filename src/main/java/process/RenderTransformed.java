package process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import org.janelia.utility.parse.ParseUtils;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import io.AffineImglib2IO;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.FinalInterval;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.InvertibleDeformationFieldTransform;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
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

	public static void main( String[] args ) throws FormatException, IOException
	{

		String imF = args[ 0 ];
		String outF = args[ 1 ];
		String outputIntervalF = args[ 2 ];
		
		System.out.println( "imf: " + imF );
		// LOAD THE IMAGE

		ImagePlus ip = null;
		AffineTransform3D resInXfm = null;
		if( imF.endsWith( "nii" ))
		{
			ip = NiftiIo.readNifti( new File( imF ));
		}
		else
		{
			ip = IJ.openImage( imF );
		}

		Img< FloatType > baseImg = ImageJFunctions.convertFloat( IJ.openImage( imF ) );


		double rx = ip.getCalibration().pixelWidth;
		double ry = ip.getCalibration().pixelHeight;
		double rz = ip.getCalibration().pixelDepth;


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
		}

		FinalInterval renderInterval = parseInterval( outputIntervalF );
		
		System.out.println( "writing to: " + outF );
		
		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();

		if( resInXfm != null )
		{
			totalXfm.add( resInXfm );
			System.out.println( "resInXfm: " + resInXfm );
		}

		int nThreads = 8;
		AffineTransform3D resOutXfm = null;

		int i = 3;
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

		if( resOutXfm != null )
			totalXfm.add( resOutXfm.inverse() );


		System.out.println("transforming");
		IntervalView< FloatType > imgHiXfm = Views.interval( 
				Views.raster( 
					RealViews.transform(
							Views.interpolate( Views.extendZero( baseImg ), new NLinearInterpolatorFactory< FloatType >() ),
							totalXfm )),
				renderInterval );

//		Bdv bdv = BdvFunctions.show( imgHiXfm, "img hi xfm" );
//////	Bdv bdv = BdvFunctions.show( Views.stack( template, imgHiXfm ), "img hi xfm" );
//		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 0 ).setRange( 0, 1200 );


		System.out.println("allocating");
		FloatImagePlus< FloatType > out = ImagePlusImgs.floats( 
				renderInterval.dimension( 0 ),
				renderInterval.dimension( 1 ),
				renderInterval.dimension( 2 ));

		IntervalView< FloatType > outTranslated = Views.translate( out,
				renderInterval.min( 0 ),
				renderInterval.min( 1 ),
				renderInterval.min( 2 ));

		System.out.println("copying with " + nThreads + " threads");
		RenderUtil.copyToImageStack( imgHiXfm, outTranslated, nThreads );

		try
		{
			System.out.println("saving to: " + outF );
			IJ.save( out.getImagePlus(), outF );
		}
		catch ( ImgLibException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static InvertibleRealTransform loadTransform( String filePath, boolean invert ) throws IOException
	{
		if( filePath.endsWith( "mat" ))
		{
			try
			{
				AffineTransform3D xfm = ANTSLoadAffine.loadAffine( filePath );
				if( invert )
				{
					System.out.println("inverting");
					System.out.println( "xfm: " + xfm );
					return xfm.inverse().copy();
				}
				System.out.println( "xfm: " + xfm );
				return xfm;
			} catch ( IOException e )
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
				//System.out.println( "cant invert displacement field here");
				InvertibleDeformationFieldTransform<FloatType> invxfm = new InvertibleDeformationFieldTransform<FloatType>(
						dfield.getDefField());

				return new InverseRealTransform( invxfm );
			}
			return dfield;
		}
		return null;
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
