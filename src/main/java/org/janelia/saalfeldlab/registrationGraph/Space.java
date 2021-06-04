package org.janelia.saalfeldlab.registrationGraph;

/**
 * A space is the source or target of a spatial transformation (registration).
 * 
 * @author John Bogovic
 */
public class Space
{
	private final String name;

	private transient final Unit unitObj;

	private final String unit;

	public Space( final String name )
	{
		this( name, new Unit( "" ));
	}

	public Space( final String name, final String unit )
	{
		this( name, new Unit( unit ));
	}

	public Space( final String name, final Unit unitObj )
	{
		this.name = name;
		this.unitObj = unitObj;
		this.unit = unitObj.getUnit();
	}

	public String getName()
	{
		return name;
	}

	public String getUnit()
	{
		return unit;
	}

	@Override
	public int hashCode()
	{
		return name.hashCode();
	}

	@Override
	public boolean equals( Object other )
	{
		if( other instanceof Space )
		{
			Space s = (Space)other;
			return name.equals(s.getName()); //&& unit.equals( s.getUnit() );
//			return name.equals(s.getName()) && unit.equals( s.getUnit() );
					//unit.equals(s.getUnit().getUnit());
		}

		return false;
	}
	
	public String toString()
	{
		return getName();
	}
	
	public static Space fromString( final String string )
	{
		String[] args = string.split(";");
		return new Space(args[0], args[1]);
	}
	
	public static Space fromJson( final String string )
	{
		return RegistrationGraph.gson.fromJson(string, Space.class);
	}

	public static String toJson( final Space space )
	{
		return RegistrationGraph.gson.toJson( space );
	}

}
