package org.janelia.saalfeldlab.registrationGraph;

/**
 * Spatial units
 * 
 * @author John Bogovic
 */
public class Unit
{
//	public static enum UNIT {
//		nm, um, cm, m, km
//	};

	public static final String nm = "nm";
	public static final String um = "um";
	public static final String cm = "cm";
	public static final String m = "m";
	public static final String km = "km";
	
//	public static final String[] validUnits = new String[]{
//		"nm", "um", "cm", "m", "km"
//	};

	private final String unit;

	public Unit( final String unit )
	{
		final String unitlower = unit.toLowerCase();
		if ( unitlower.equals( nm ) ||
				unitlower.equals( um ) ||
				unitlower.equals( cm ) ||
				unitlower.equals( m ) ||
				unitlower.equals( km ) )
		{
			this.unit = unitlower;
		}
		else
		{
			this.unit = "";
		}
	}

	public String getUnit()
	{
		return unit;
	}
	
	public static double convert( final String src, final String to )
	{
		return convertToMeters( to ) / convertToMeters( src );
	}
	
	public static double convertToMeters( final String unit )
	{
		switch( unit )
		{
		case m:
			return 1.0;
		case nm:
			return 1e-9;
		case um:
			return 1e-6;
		case cm:
			return 0.01;
		case km:
			return 1000;
		default:
			return Double.NaN;
		}
	}

}

