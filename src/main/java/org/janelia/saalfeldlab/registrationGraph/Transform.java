package org.janelia.saalfeldlab.registrationGraph;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * A transformation mapping points from Source to Destination space.
 * 
 * @author John Bogovic
 */
public class Transform
{
	
	private final Space src;

	private final Space dst;
	
	private final String name;

	private final double cost;
	
	private final String path;

	public Transform( final String path, final Space src, final Space dst )
	{
		this(path, src, dst, src.getName() + "_" + dst.getName());
	}

	public Transform( final String path, final Space src, final Space dst, final String name )
	{
		this( path, src, dst, name, 1.0 );
	}

	public Transform( final String path, final Space src, final Space dst, final String name, final double cost )
	{
		this.path = path;
		this.src = src;
		this.dst = dst;
		this.name = name;
		this.cost = cost;
	}
	
	public String getName()
	{
		return name;
	}

	public String getPath()
	{
		return path;
	}

	public Space getSource()
	{
		return src;
	}

	public Space getDestination()
	{
		return dst;
	}
	
	public double getCost()
	{
		return cost;
	}

	public String toString()
	{
		return getName();
	}
	
	@Override
	public boolean equals( Object other )
	{
		if( other instanceof Transform )
		{
			Transform t = (Transform)other;
			return name.equals( t.getName() ) && src.equals( t.getSource()) && dst.equals( t.getDestination() );
		}
		else
			return false;
	}

	public static Transform fromString( final String string, final Map<String,Space> spaces )
	{
		String[] args = string.split(">");
		if( args.length == 2 )
			return new Transform("",spaces.get(args[0]), spaces.get(args[1]));
		else if( args.length == 3 )
			return new Transform("",spaces.get(args[0]), spaces.get(args[2]), args[1] );
		else
			return null;
	}

	public static Transform fromJson( final String string )
	{
		return RegistrationGraph.gson.fromJson(string, Transform.class);
	}

	public static String toJson( final Transform transform )
	{
		return RegistrationGraph.gson.toJson( transform );
	}
	
	public static Transform fromH5( final String h5Path, boolean forward )
	{
		final String fname = h5Path.substring(h5Path.lastIndexOf(File.separator) + 1);
		final String name = fname.substring( 0, fname.lastIndexOf('.') );
		String[] srcDest = name.split("_");
		final String src = srcDest[0].trim();
		final String dst = srcDest[1].trim();
		if( forward )
			return new Transform(h5Path, new Space(src), new Space(dst), name);
		else 
			return new Transform(h5Path+"?i", new Space(dst), new Space(src), dst+"_"+src);
	}

}
