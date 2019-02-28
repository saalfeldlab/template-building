package org.janelia.saalfeldlab.swc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Swc
{
	
	public static final int UNDEFINED_TYPE = 0;
	public static final int SOMA_TYPE = 1;
	public static final int AXON_TYPE = 2;
	public static final int DENDRITE_TYPE = 3;
	public static final int APICAL_DENDRITE_TYPE = 4;
	public static final int FORK_POINT_TYPE = 5;
	public static final int END_POINT_TYPE = 6;
	public static final int CUSTOM_TYPE = 7;

	private List<SwcPoint> points;
	
	public Swc()
	{
		points = new ArrayList<>();
	}

	public Swc( final List<SwcPoint> points )
	{
		this.points = points;
	}
	
	public void add( SwcPoint pt )
	{
		if( pt != null )
			points.add( pt );
	}
	
	public List< SwcPoint > getPoints()
	{
		return points;
	}

	public static Swc read( final File f )
	{
		Swc out = new Swc();
		try
		{
			List< String > lines = Files.readAllLines( Paths.get( f.toURI() ));
			lines.stream().forEach( x -> out.add( SwcPoint.parse( x )));
			return out;
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		return null;
	}

	public static void write( final Swc swc, final File f )
	{
		// TODO include header
		ArrayList<String> lines = new ArrayList<>();
		swc.points.stream().forEach( x -> lines.add( x.toString()) );
		try
		{
			Files.write( Paths.get( f.toURI()), lines );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
	}
}
