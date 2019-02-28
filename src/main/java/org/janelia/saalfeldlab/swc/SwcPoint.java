package org.janelia.saalfeldlab.swc;

/**
 * An immutable swc point
 */
public class SwcPoint
{
	public final double x, y, z;
	public final double radius;
	public final int id, type, previous;

	public SwcPoint( final int id, final int type, final double x, final double y,
			final double z, final double radius, final int previous )
	{
		this.id = id;
		this.type = type;
		this.x = x;
		this.y = y;
		this.z = z;
		this.radius = radius;
		this.previous = previous;
	}
	
	/**
	 * Returns a new SwcPoint with the given position, and other fields copied
	 * @param newx
	 * @param newy
	 * @param newz
	 * @return
	 */
	public SwcPoint setPosition( final double newx, final double newy, final double newz )
	{
		return new SwcPoint( id, type, newx, newy, newz, radius, previous );
	}

	/**
	 * Returns a new SwcPoint with the given radius, and other fields copied from this point
	 * @param newradius
	 * @return
	 */
	public SwcPoint setRadius( final double newradius )
	{
		return new SwcPoint( id, type, x, y, z, newradius, previous );
	}
	
	/**
	 * Returns an SwcPoint parsed from a string.
	 * @param s
	 * @return
	 */
	public static SwcPoint parse( final String s )
	{
		if( s.startsWith( "#" ))
			return null;

		try{
			String[] parts = s.split( "\\s" );
			return new SwcPoint(
					Integer.parseInt( parts[0] ), 	// id
					Integer.parseInt( parts[1] ), 	// type
					Double.parseDouble( parts[2] ),	// x 
					Double.parseDouble( parts[3] ),	// y
					Double.parseDouble( parts[4] ),	// z
					Double.parseDouble( parts[5] ),	// radius
					Integer.parseInt( parts[6] ));  // previous
		}
		catch( Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}
	
	/**
	 * Used for io
	 */
	public String toString()
	{
		return String.format( "%d %d %f %f %f %f %d", id, type, x, y, z, radius, previous );
	}

}
