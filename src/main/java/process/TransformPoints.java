package process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.janelia.utility.parse.ParseUtils;

import loci.formats.FormatException;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;

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
