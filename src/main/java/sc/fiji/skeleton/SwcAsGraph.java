package sc.fiji.skeleton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.skeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.skeleton.Skeleton2Swc;
import sc.fiji.skeleton.SwcIO;
import sc.fiji.analyzeSkeleton.Vertex;

public class SwcAsGraph
{

	public static Graph load( File f )
	{
		ArrayList< SWCPoint > swcPts = SwcIO.loadSWC( f );
		int N = swcPts.size();

		Graph g = new Graph();

		// find branch points

		ArrayList< Integer > branchAndTipIds = new ArrayList< Integer >();
		// first count the number of children each point has
		// only points with #children > 1 are branch points
		int[] numChildren = new int[ N ];
		for ( SWCPoint pt : swcPts )
		{
			int prev = pt.getPrevious();
			if ( prev >= 0 )
				numChildren[ prev ]++;
		}
		for ( int i = 0; i < N; i++ )
		{
			if ( numChildren[ i ] > 1 || numChildren[ i ] == 0
					|| swcPts.get( i ).getPrevious() < 0 )
				branchAndTipIds.add( swcPts.get( i ).getId() );
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
		for ( SWCPoint swcPt : swcPts )
		{
			Point p = new Point( (int) swcPt.getX(), (int) swcPt.getY(),
					(int) swcPt.getZ() );
			// System.out.println( "p: " + swcPt );
			// System.out.println( "pid: " + swcPt.getId() );
			// System.out.println( "is id branch or tip : " +
			// branchAndTipIds.contains( swcPt.getId() ));

			if ( branchAndTipIds.contains( swcPt.getId() ) )
			{
				
				// make the vertex
				Vertex v = new Vertex();
				v.addPoint( p );
				g.addVertex( v );
				vertices.put( swcPt.getId(), v );

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
					
					if ( vertices.containsKey( swcPt.getPrevious()) )
						lastVertex = vertices.get( swcPt.getPrevious() );
					else if ( slabPt2VertexPt.containsKey( swcPt.getPrevious()) )
						lastVertex = vertices.get( slabPt2VertexPt.get( swcPt.getPrevious()));

					ArrayList<Point> slab = vertex2Slab.get( swcPt.getPrevious() );
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
				if( vertices.containsKey( swcPt.getPrevious() ))
				{
					vertexIdx = swcPt.getPrevious();
					slabPt2VertexPt.put( swcPt.getId(), vertexIdx );

					
					// make a new slab since this is the first point
					// of this slab (for this vertex)
					slab = new ArrayList<Point>();
					vertex2Slab.put( swcPt.getId(), slab );
				}
				else if( slabPt2VertexPt.containsKey( swcPt.getPrevious() ))
				{
					vertexIdx = slabPt2VertexPt.get( swcPt.getPrevious() );
					slabPt2VertexPt.put( swcPt.getId(), vertexIdx );

					// get the slab corresponding to the parent of this point
					slab = vertex2Slab.get( swcPt.getPrevious());
					vertex2Slab.put( swcPt.getId(), slab );
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

		ArrayList< tracing.SWCPoint > swcPts = Skeleton2Swc.graphToSwc( g );
		System.out.println( "  num points of output : " + swcPts.size() );

		System.out.println( "Exporting to " + outname );
		try
		{
			final PrintWriter pw = new PrintWriter(
					new OutputStreamWriter( new FileOutputStream( outname ), "UTF-8" ) );
			Skeleton2Swc.flushSWCPoints( swcPts, pw );
		} catch ( final IOException ioe )
		{
			System.err.println( "Saving to " + outname + " failed" );
		}
	}
}
