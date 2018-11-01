package evaluation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import process.RenderTransformed;
import sc.fiji.analyzeSkeleton.SwcIO;
import sc.fiji.analyzeSkeleton.SWCPoint;
import tracing.SNT;


public class TransformSwc
{

	/*
	 * Reads the first argument as a swc file
	 * If the second argument is a directory, will rename the input and write to that directory,
	 * else, will treat the second argument as the path of the output.
	 */
	public static void main( String[] args )
	{
		System.out.println( "TRANSFORM SWC");
		String ptFlist = args[ 0 ];
		String out = args[ 1 ];
		
		String[] list = null;
		if( ptFlist.indexOf( ',' ) < 0 )
		{
			list = new String[]{ ptFlist };
		}
		else
		{
			list = ptFlist.split( "," );
		}
		System.out.println( Arrays.toString( list ));

		File testOutDir = new File( out );
		boolean isOutDir = testOutDir.isDirectory();

		System.out.println( "writing to: " + out );
		
		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();

		int nThreads = 1;
		
		int i = 2;
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
			
			InvertibleRealTransform xfm = null;
			try
			{
				xfm = RenderTransformed.loadTransform( args[ i ], invert );
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
			
			if( xfm == null )
			{
				System.err.println("  failed to load transform ");
				System.exit( 1 );
			}

			totalXfm.add( xfm );
			i++;
		}
		
		for ( String ptF : list )
		{
			File fileIn = new File( ptF );
			
			File fileOut = null;
			if( isOutDir )
			{
				fileOut = new File( out + File.separator + fileIn.getName().replaceAll( ".swc", "_xfm.swc" ));
			}
			else
			{
				fileOut = new File( out );
			}
		
			ArrayList< SWCPoint > res = transformSWC( totalXfm, SwcIO.loadSWC( fileIn ) );
			System.out.println( "Exporting to " + fileOut );
			try
			{
				final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileOut), "UTF-8"));
				flushSWCPoints( res, pw);
			}
			catch (final IOException ioe)
			{
				System.err.println("Saving to " + fileOut + " failed");
				continue;
			}
		}
	}

	public static void flushSWCPoints(final List<SWCPoint> swcPoints, final PrintWriter pw) {
		pw.println("# Exported from \"Simple Neurite Tracer\" version " + SNT.VERSION + " on "
				+ LocalDateTime.of(LocalDate.now(), LocalTime.now()));
		pw.println("# https://imagej.net/Simple_Neurite_Tracer");
		pw.println("#");
//		pw.println("# All positions and radii in " + spacing_units);
//		if (usingNonPhysicalUnits())
//			pw.println("# WARNING: Usage of pixel coordinates does not respect the SWC specification");
//		else
//			pw.println("# Voxel separation (x,y,z): " + x_spacing + ", " + y_spacing + ", " + z_spacing);
		pw.println("#");
		
		for (final SWCPoint p : swcPoints)
			p.println(pw);

		pw.close();
	}

	public static ArrayList<SWCPoint> transformSWC( InvertibleRealTransform xfm, ArrayList<SWCPoint> pts )
	{
		ArrayList<SWCPoint> out = new ArrayList<SWCPoint>();
		pts.forEach( pt -> out.add( transform( xfm, pt )) );
		return out;
	}
	
	public static SWCPoint transform( InvertibleRealTransform xfm, SWCPoint pt )
	{
		SwcIO.StringPrintWriter spw = new SwcIO.StringPrintWriter();
		//System.out.println( pt.toString() );
		pt.println( spw );
		
		
		String[] s = spw.toString().split( " " );
		int id = Integer.parseInt( s[ 0 ] );
		int type = Integer.parseInt( s[ 1 ] );
		double radius = Double.parseDouble( s[ 5 ] );
		int previous = Integer.parseInt( s[ 6 ] );
		
		double[] p = new double[] { 
				pt.getPointInImage().x,
				pt.getPointInImage().y,
				pt.getPointInImage().z };

		double[] pxfm = new double[ 3 ];
		xfm.apply( p, pxfm );

		return new SWCPoint( 
				id, type, 
				pxfm[ 0 ], pxfm[ 1 ], pxfm[ 2 ], 
				radius, previous );
	}
	

}
