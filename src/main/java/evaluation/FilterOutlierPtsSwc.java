package evaluation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.janelia.saalfeldlab.swc.Swc;
import org.janelia.saalfeldlab.swc.SwcPoint;


public class FilterOutlierPtsSwc
{

	public static void main( String[] args )
	{
		double distanceThreshold = Double.parseDouble( args[ 0 ] );
		
		int i = 1;
		while( i < args.length )
		{
			if( !args[ i ].endsWith( ".swc" ))
			{
				System.out.println( "Must give swc files as input, skipping " + args[ i ]);
				i++;
				continue;
			}
			File fileIn = new File( args[ i ] );
			File fileOut = new File( fileIn.getAbsolutePath()
					.replaceAll( ".swc", "_filt.swc" ));

			Swc swcIn = Swc.read( fileIn );
			Map< Integer, SwcPoint > idPtMap = pointFromId( swcIn );
			Map< Integer, List< SwcPoint > > childrenMap = childrenFromId( swcIn );

			Swc swcOut = filterPoints( swcIn, idPtMap, childrenMap, distanceThreshold );
			
			System.out.println( "Exporting to " + fileOut );
			Swc.write( swcOut, fileOut );
			i++;
		}

	}
	
	public static Map<Integer,SwcPoint> pointFromId( Swc swc )
	{
		HashMap<Integer,SwcPoint> map = new HashMap<>();
		for( SwcPoint p : swc.getPoints() )
			map.put( p.id, p );

		return map;
	}
	
	public static Map<Integer,List<SwcPoint>> childrenFromId( 
			Swc swc )
	{
		HashMap<Integer,List<SwcPoint>> map = new HashMap<>();
		
		// Initialize
		for( SwcPoint p : swc.getPoints() )
		{
			map.put( p.id, new ArrayList<>());
		}
		
		// build the list
		for( SwcPoint p : swc.getPoints() )
		{
			int parentId =  p.previous;
			if( map.containsKey( parentId ))
				map.get( parentId ).add( p );
		}

		return map;
	}
	
	public static Swc filterPoints( 
			Swc swc,
			final Map< Integer, SwcPoint > idPtMap,
			final Map< Integer, List< SwcPoint > > childrenMap,
			final double distanceThreshold ) 
	{
		ArrayList<SwcPoint> swcOut = new ArrayList<>();
		ArrayList<SwcPoint> naughtyPoints = new ArrayList<>(); 

		for( SwcPoint p : swc.getPoints() )
		{
			SwcPoint parent = idPtMap.get( p.previous );

			// don't bother if the parent has been excluded
			if( parent == null || naughtyPoints.contains( parent ))
			{
				swcOut.add( p );
				continue;
			}

			double distance = distance( p, parent );

			if( distance > distanceThreshold )
			{

				List< SwcPoint > children = childrenMap.get( p.id );
				
				// this point (p) is bad if all p's children are closer
				// to p's parent than p
				boolean isNaughty = 
					children.stream().map( x -> distance( x, parent ))
					.allMatch( d -> (d < distance) );


				if( isNaughty )
					naughtyPoints.add( p );
				
				if( children.size() > 0 )
					swcOut.add( convexCombination( p, parent, children ) );
				else
					swcOut.add( p );
			}
			else
			{
				swcOut.add( p );
			}
		}
		
		return new Swc( swcOut );
	}
	
	public static SwcPoint convexCombination(
			final SwcPoint base,
			final SwcPoint parent,
			final List<SwcPoint> children )
	{
		double t = 1.0 / ( 1 + children.size() );
		double x = t * parent.x;
		double y = t * parent.y;
		double z = t * parent.z;
	
		for( SwcPoint c : children )
		{
			x += t * c.x;
			y += t * c.y;
			z += t * c.z;
		}
	
		return base.setPosition( x, y, z );
	}

	public static SwcPoint linearInterpolate(
			final SwcPoint prev, 
			final SwcPoint base,
			final SwcPoint next,
			final double t )
	{
		
		double x = ( 1 - t ) * prev.x + t * next.x;
		double y = ( 1 - t ) * prev.y + t * next.y;
		double z = ( 1 - t ) * prev.z + t * next.z;

		return base.setPosition( x, y, z );
	}
	
	public static double distance( 
			final SwcPoint p, 
			final SwcPoint q )
	{
		double dx = p.x - q.x;
		double dy = p.y - q.y;
		double dz = p.z - q.z;
		return Math.sqrt( dx*dx + dy*dy + dz*dz );
	}

}
