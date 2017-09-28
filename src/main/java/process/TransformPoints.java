package process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
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
public class TransformPoints
{

	public static void main( String[] args ) throws FormatException, IOException
	{

		String ptsF = args[ 0 ];
		
		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();

		int nThreads = 1;

		int i = 1;
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
			
			if( invert )
				System.out.println( "loading transform from " + args[ i ] + " AND INVERTING" );
			else
				System.out.println( "loading transform from " + args[ i ]);
			
			InvertibleRealTransform xfm = RenderTransformed.loadTransform( args[ i ], invert );
			
			if( xfm == null )
			{	
				System.err.println("  failed to load transform ");
				System.exit( 1 );
			}

			totalXfm.add( xfm );
			i++;
		}

		List< String > lines = Files.readAllLines( Paths.get( ptsF ) );
		double[] result = new double[ 3 ];
		
		for( String line : lines )
		{
			double[] src = ParseUtils.parseDoubleArray( line, " " );
			totalXfm.apply( src, result );
			System.out.println( Arrays.toString( result ));
		}
		
	}
	

}
