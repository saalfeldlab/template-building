package sc.fiji.skeleton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.janelia.saalfeldlab.swc.Swc;
import org.janelia.saalfeldlab.swc.SwcPoint;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.skeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.skeleton.Skeleton2Swc;
import sc.fiji.analyzeSkeleton.Vertex;

public class SwcAsGraph
{

	public static Graph load( File f )
	{
		Swc swcPts = Swc.read( f );
		int N = swcPts.getPoints().size();

		Graph g = new Graph();

		// find branch points

		ArrayList< Integer > branchAndTipIds = new ArrayList< Integer >();
		// first count the number of children each point has
		// only points with #children > 1 are branch points
		int[] numChildren = new int[ N ];
		for ( SwcPoint pt : swcPts.getPoints() )
		{
			int prev = pt.previous;
			if ( prev >= 0 )
				numChildren[ prev ]++;
		}
		for ( int i = 0; i < N; i++ )
		{
			if ( numChildren[ i ] > 1 || numChildren[ i ] == 0
					|| swcPts.getPoints().get( i ).previous < 0 )
				branchAndTipIds.add( swcPts.getPoints().get( i ).id );
		}

//		System.out.println( Arrays.toString( numChildren ) );
//		System.out.println( branchAndTipIds );

		HashMap< Integer, Vertex > vertices = new HashMap< Integer, Vertex >();

		int i = 0;
		boolean first = true;
		Vertex lastVertex = null;
		double length = 0;
		
		HashMap< Integer, ArrayList<Point>> vertex2Slab = new HashMap<Integer, ArrayList<Point>>();
		HashMap< Integer, Integer > slabPt2VertexPt = new HashMap< Integer, Integer >();  
		
		// assumes that all "slabs" are adjacent in the list
		// ... turns out that this is not true.  Time to fix!
		for ( SwcPoint swcPt : swcPts.getPoints() )
		{
			// casting to ints is sad 
			Point p = new Point( (int) swcPt.x, (int) swcPt.y, (int) swcPt.z );
			// System.out.println( "p: " + swcPt );
			// System.out.println( "pid: " + swcPt.getId() );
			// System.out.println( "is id branch or tip : " +
			// branchAndTipIds.contains( swcPt.getId() ));

			if ( branchAndTipIds.contains( swcPt.id ) )
			{
				
				// make the vertex
				Vertex v = new Vertex();
				v.addPoint( p );
				g.addVertex( v );
				vertices.put( swcPt.id, v );

				// Add an edge

				// No Edge if this is the first vertex though
				if ( first )
				{
					g.setRoot( v );
					first = false;
				} else
				{
					// If we don't already know the vertex opposite this edge,
					// find it
//					if ( lastVertex == null )
//						lastVertex = vertices.get( swcPt.getPrevious() );
					
//					// FOR DEBUG
//					if ( lastVertex == null )
//					{
//						// if its still null for some reason
//						System.out.println( "has key " + swcPt.getPrevious() + "? " + vertices.containsKey( swcPt.getPrevious() ));
//					}
					
					if ( vertices.containsKey( swcPt.previous) )
						lastVertex = vertices.get( swcPt.previous );
					else if ( slabPt2VertexPt.containsKey( swcPt.previous) )
						lastVertex = vertices.get( slabPt2VertexPt.get( swcPt.previous));

					ArrayList<Point> slab = vertex2Slab.get( swcPt.previous );
					// create a new, empty slab, if one does not exist
					if( slab == null )
						slab = new ArrayList<Point>();


					// make the edge and add it
					Edge e = new Edge( lastVertex, v, slab, length );
					v.setPredecessor( e );
					g.addEdge( e );

					// reset
					lastVertex = null;
//					slab.clear();
					length = 0;
				}
			} 
			else
			{
//				if ( lastVertex == null )
//				{
//					lastVertex = vertices.get( swcPt.getPrevious() );
//					System.out.println("lastVertex = " + swcPt.getPrevious() + " for pt " + swcPt.getId());
//					if ( lastVertex == null )
//					{
//						System.out.println("  but is still null :( ");
//					}
//				}
			
//				if( swcPt.getId() == 339 )
//				{
//					System.out.println( "adding 339 as slab" );
//				}
				
				// get the correct slab
				ArrayList<Point> slab = null;

				int vertexIdx = -1;
				if( vertices.containsKey( swcPt.previous ))
				{
					vertexIdx = swcPt.previous;
					slabPt2VertexPt.put( swcPt.id, vertexIdx );

					
					// make a new slab since this is the first point
					// of this slab (for this vertex)
					slab = new ArrayList<Point>();
					vertex2Slab.put( swcPt.id, slab );
				}
				else if( slabPt2VertexPt.containsKey( swcPt.previous ))
				{
					vertexIdx = slabPt2VertexPt.get( swcPt.previous );
					slabPt2VertexPt.put( swcPt.id, vertexIdx );

					// get the slab corresponding to the parent of this point
					slab = vertex2Slab.get( swcPt.previous);
					vertex2Slab.put( swcPt.id, slab );
				}
				else
				{
					System.out.println( "uh-oh" );
					continue;
				}
				
				slab.add( p );
			}
			length++;
			i++;
		}
		return g;
	}

	public static void main( String[] args )
	{
		String f = "/groups/saalfeld/home/bogovicj/swc/test.swc";
		String outname = "/groups/saalfeld/home/bogovicj/swc/test_out.swc";

		Graph g = SwcAsGraph.load( new File( f ) );
		System.out.println( g );

		Swc swcPts = Skeleton2Swc.graphToSwc( g );
		System.out.println( "  num points of output : " + swcPts.getPoints().size() );

		System.out.println( "Exporting to " + outname );
		Swc.write( swcPts, new File( outname ) );
	}
}
