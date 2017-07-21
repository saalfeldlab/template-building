package process;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.janelia.utility.parse.ParseUtils;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import io.AffineImglib2IO;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.img.imageplus.ShortImagePlus;
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
import net.imglib2.transform.InverseTransform;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.RandomAccessibleOnRealRandomAccessible;
import net.imglib2.view.Views;
import transforms.AffineHelper;
import util.RenderUtil;

/**
 * Renders images transformed/registered at low resolution at high resolution.
 * <p>
 * Applies up to four transformations:
 * <ul>
 * 	<li>(Optional) "Downsampling Affine" : affine from high (original) resolution to low resoultion (at which registration was performed)</li>
 *  <li>"Reg affine" : Affine part of registering transform</li>
 *  <li>"Reg Warp" : Deformable part of registering transform</li>
 *  <li>(Optional) "to canonical affine" : Final transform in final high-res space (usually brining subject to a "canonical" orientation" </li>
 * </ul>
 * <p>
 * Currently the final (implied) upsampling transform is hard-coded in : it resmples up by a factor of 4 in x and y and 2 in z.
 * After that, it upsamples z again by a factor of (~ 2.02) to resample the images to a resolution of 0.188268 um isotropic.
 * 
 * Command line parameters / usage:
 * RenderHires <high res image> <downsample affine> <reg affine> <reg warp> <output interval> <output path> <optional to canonical affine>
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
		Img< FloatType > baseImg = null;
		if( imF.endsWith( "nii" ))
		{
			baseImg = ImageJFunctions.convertFloat( NiftiIo.readNifti( new File( imF )));
		}
		else
		{
			baseImg = ImageJFunctions.convertFloat( IJ.openImage( imF ) );	
		}

		FinalInterval renderInterval = parseInterval( outputIntervalF );
		
		System.out.println( "writing to: " + outF );
		
		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		
		int i = 3;
		while( i < args.length )
		{
			boolean invert = false;
			if( args[ i ].equals( "-i" ))
			{
				invert = true;
				i++;
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

		System.out.println("copying with 8 threads");
		RenderUtil.copyToImageStack( imgHiXfm, outTranslated, 8 );

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
	
	public static InvertibleRealTransform loadTransform( String filePath, boolean invert )
	{
		if( filePath.endsWith( "mat" ))
		{
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
		else if( filePath.endsWith( "txt" ))
		{
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
		else
		{
			
			Img< FloatType > displacement = null;
			if( filePath.endsWith( "nii" ))
			{
				try
				{
					displacement = ImageJFunctions.convertFloat( 
							NiftiIo.readNifti( new File( filePath ) ) );
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
				displacement = ImageJFunctions.convertFloat( IJ.openImage( filePath ));
			}

			if( displacement == null )
				return null;

			System.out.println( "DISPLACEMENT INTERVAL: " + Util.printInterval( displacement ));
			ANTSDeformationField dfield = new ANTSDeformationField( displacement, new double[]{ 1, 1, 1 } );

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
