package evaluation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.io.output.ByteArrayOutputStream;

import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import process.RenderTransformed;
import tracing.SNT;
import tracing.SWCPoint;



public class TransformSwc
{

	/*
	 * Reads the first argument as a swc file
	 * If the second argument is a directory, will rename the input and write to that directory,
	 * else, will treat the second argument as the path of the output.
	 */
	public static void main( String[] args )
	{
		String ptFlist = args[ 0 ];
		String out = args[ 1 ];

		File testOutDir = new File( out );
		boolean isOutDir = testOutDir.isDirectory();

		System.out.println( "writing to: " + out );
		
		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		
		int i = 2;
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
			
			InvertibleRealTransform xfm = RenderTransformed.loadTransform( args[ i ], invert );
			
			if( xfm == null )
			{
				System.err.println("  failed to load transform ");
				System.exit( 1 );
			}
			
			totalXfm.add( xfm );
			i++;
		}
		
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
		
			ArrayList< SWCPoint > res = transformSWC( totalXfm, loadSWC( fileIn ));
			
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

	public static void flushSWCPoints(final ArrayList<SWCPoint> swcPoints, final PrintWriter pw) {
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
		StringPrintWriter spw = new StringPrintWriter();
		//System.out.println( pt.toString() );
		pt.println( spw );
		
		
		String[] s = spw.toString().split( " " );
		int id = Integer.parseInt( s[ 0 ] );
		int type = Integer.parseInt( s[ 1 ] );
		double radius = Double.parseDouble( s[ 5 ] );
		int previous = Integer.parseInt( s[ 6 ] );
		
		double[] p = new double[] { 
				pt.getPointInImage().x,
				pt.getPointInImage().x,
				pt.getPointInImage().z };
		
		double[] pxfm = new double[ 3 ];
		xfm.apply( p, pxfm );
		
		return new SWCPoint( 
				id, type, 
				pxfm[ 0 ], pxfm[ 1 ], pxfm[ 2 ], 
				radius, previous );
	}
	
	public static ArrayList<SWCPoint> loadSWC( File f )
	{
		ArrayList<SWCPoint> out = new ArrayList<SWCPoint>();
		
		try
		{
			Files.lines( Paths.get( f.getCanonicalPath() )).forEach( 
					l -> line2point( out, l ));
		} 
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		return out;
	}
	
	public static void line2point( ArrayList<SWCPoint> out, String line )
	{
		if( line.startsWith( "#" ))
			return;

		String[] s = line.split( " " );
		int id = Integer.parseInt( s[ 0 ] );
		int type = Integer.parseInt( s[ 1 ] );
		
		double x = Double.parseDouble( s[ 2 ] );
		double y = Double.parseDouble( s[ 3 ] );
		double z = Double.parseDouble( s[ 4 ] );
		
		double radius = Double.parseDouble( s[ 5 ] );
		int previous = Integer.parseInt( s[ 6 ] );
		
		out.add( new SWCPoint( id, type, x, y, z, radius, previous ));
	}
	
	private static class StringPrintWriter extends PrintWriter {

		StringBuffer theString;

		public StringPrintWriter()
		{
			this( new ByteArrayOutputStream());
		}

		public StringPrintWriter( OutputStream stream ) 
		{
			super( stream );
			theString = new StringBuffer();
		}

		@Override
		public void println( String s )
		{
			theString.append( s );
		}

		@Override
		public String toString()
		{
			return theString.toString();
		}
		
	}
}
