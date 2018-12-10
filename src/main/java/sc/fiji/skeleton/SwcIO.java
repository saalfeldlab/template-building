package sc.fiji.skeleton;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.apache.commons.io.output.ByteArrayOutputStream;

import sc.fiji.skeleton.SWCPoint;

public class SwcIO
{
	public static class StringPrintWriter extends PrintWriter
	{

		StringBuffer theString;

		public StringPrintWriter()
		{
			this( new ByteArrayOutputStream() );
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

	public static ArrayList< SWCPoint > loadSWC( File f )
	{
		ArrayList< SWCPoint > out = new ArrayList< SWCPoint >();

		try
		{
			Files.lines( Paths.get( f.getCanonicalPath() ) )
					.forEach( l -> line2point( out, l ) );
		} catch ( IOException e )
		{
			e.printStackTrace();
		}
		return out;
	}

	public static void line2point( ArrayList< SWCPoint > out, String line )
	{
		if ( line.startsWith( "#" ) )
			return;

		String[] s = line.split( " " );
		int id = Integer.parseInt( s[ 0 ] );
		int type = Integer.parseInt( s[ 1 ] );

		double x = Double.parseDouble( s[ 2 ] );
		double y = Double.parseDouble( s[ 3 ] );
		double z = Double.parseDouble( s[ 4 ] );

		double radius = Double.parseDouble( s[ 5 ] );
		int previous = Integer.parseInt( s[ 6 ] );

		out.add( new SWCPoint( id, type, x, y, z, radius, previous ) );
	}

}
