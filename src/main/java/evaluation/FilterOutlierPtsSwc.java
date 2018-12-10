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

import sc.fiji.skeleton.SwcIO;
import sc.fiji.skeleton.SWCPoint;

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

			ArrayList< SWCPoint > swcIn = SwcIO.loadSWC( fileIn );
			Map< Integer, SWCPoint > idPtMap = pointFromId( swcIn );
			Map< Integer, List< SWCPoint > > childrenMap = childrenFromId( swcIn );

			List< SWCPoint > swcOut = filterPoints( swcIn, idPtMap, childrenMap, distanceThreshold );
			
			System.out.println( "Exporting to " + fileOut );
			try
			{
				final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileOut), "UTF-8"));
				TransformSwc.flushSWCPoints( swcOut, pw );
			}
			catch (final IOException ioe)
			{
				System.err.println("Saving to " + fileOut + " failed");
			}
			i++;
		}

	}
	
	public static Map<Integer,SWCPoint> pointFromId( 
			ArrayList<SWCPoint> swc )
	{
		HashMap<Integer,SWCPoint> map = new HashMap<>();
		for( SWCPoint p : swc )
			map.put( p.getId(), p );

		return map;
	}
	
	public static Map<Integer,List<SWCPoint>> childrenFromId( 
			ArrayList<SWCPoint> swc )
	{
		HashMap<Integer,List<SWCPoint>> map = new HashMap<>();
		
		// Initialize
		for( SWCPoint p : swc )
		{
			map.put( p.getId(), new ArrayList<>());
		}
		
		// build the list
		for( SWCPoint p : swc )
		{
			int parentId =  p.getPrevious();
			if( map.containsKey( parentId ))
				map.get( parentId ).add( p );
		}

		return map;
	}
	
	public static List< SWCPoint > filterPoints( 
			final List< SWCPoint > ptList,
			final Map< Integer, SWCPoint > idPtMap,
			final Map< Integer, List< SWCPoint > > childrenMap,
			final double distanceThreshold ) 
	{
		ArrayList<SWCPoint> swcOut = new ArrayList<>();
		ArrayList<SWCPoint> naughtyPoints = new ArrayList<>(); 

//		Iterator< Integer > it = idPtMap.keySet().iterator();
		for( SWCPoint p : ptList )
		{
			SWCPoint parent = idPtMap.get( p.getPrevious());

			// don't bother if the parent has been excluded
			if( parent == null || naughtyPoints.contains( parent ))
			{
				swcOut.add( p );
				continue;
			}

			double distance = distance( p, parent );
//			System.out.println( "distance : " + distance );

			if( distance > distanceThreshold )
			{
				System.out.println( "distance : " + distance );
//				System.out.println( "filtering point: " + p );

				List< SWCPoint > children = childrenMap.get( p.getId() );
				
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
		
		return swcOut;
	}
	
	public static SWCPoint convexCombination(
			final SWCPoint base,
			final SWCPoint parent,
			final List<SWCPoint> children )
	{
		double t = 1.0 / ( 1 + children.size() );
		double x = t * parent.getPointInImage().x;
		double y = t * parent.getPointInImage().y;
		double z = t * parent.getPointInImage().z;
		
		for( SWCPoint c : children )
		{
			x += t * c.getPointInImage().x;
			y += t * c.getPointInImage().y;
			z += t * c.getPointInImage().z;
		}
		
		System.out.println( "parent : " + parent );
		System.out.println( "base   : " + base );
		System.out.println( "c0     : " + children.get( 0 ) );
		System.out.println( "xyz : " + x + " " + y + " " + z );
		System.out.println( " " );
		
		return new SWCPoint(
				base.getId(), base.getType(), 
				x, y, z,
				base.getRadius(), base.getPrevious());
	}

	public static SWCPoint linearInterpolate(
			final SWCPoint prev, 
			final SWCPoint base,
			final SWCPoint next,
			final double t )
	{
		
		double x = ( 1 - t ) * prev.getPointInImage().x + t * next.getPointInImage().x;
		double y = ( 1 - t ) * prev.getPointInImage().y + t * next.getPointInImage().y;
		double z = ( 1 - t ) * prev.getPointInImage().z + t * next.getPointInImage().z;

		System.out.println( "prev : " + prev );
		System.out.println( "base : " + base );
		System.out.println( "xyz : " + x + " " + y + " " + z );
		System.out.println( " " );

		return new SWCPoint(
				base.getId(), base.getType(), 
				x, y, z,
				base.getRadius(), base.getPrevious());
	}
	
	public static double distance( 
			final SWCPoint p, 
			final SWCPoint q )
	{
		double dx = p.getPointInImage().x - q.getPointInImage().x;
		double dy = p.getPointInImage().y - q.getPointInImage().y;
		double dz = p.getPointInImage().z - q.getPointInImage().z;
		return Math.sqrt( dx*dx + dy*dy + dz*dz );
	}

}
