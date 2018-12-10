package sc.fiji.skeleton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageConverter;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.skeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;
import sc.fiji.skeleton.ShapeMeasure.DistancePair;
import sc.fiji.analyzeSkeleton.SkeletonResult;
import sc.fiji.analyzeSkeleton.Vertex;
import tracing.Path;
import tracing.SNT;
import tracing.SWCPoint;

public class Skeleton2Swc {
	
	public static void main(String[] args) {
		
		ImagePlus imp = IJ.openImage( args[ 0 ] );
		boolean doShapeMeasure = true;
		
		if( imp.getBitDepth() != ImagePlus.GRAY8 )
		{
			System.out.println( "converting to 8bit ");
			ImageConverter conv = new ImageConverter( imp );
			conv.convertToGray8();
		}
		
		File f = new File( args[ 0 ]);
		String destdir = args[ 1 ];

		String baseoutputname = destdir + File.separator + f.getName().replaceAll( ".tif", "" );
		System.out.println( baseoutputname );
		
		AnalyzeSkeleton_ skel = new AnalyzeSkeleton_();
		skel.setup( "", imp );

		boolean pruneEnds = false;
		boolean shortPath = false;
		boolean silent = true;
		boolean verbose = true;

		System.out.println("running skeleton");
		SkeletonResult skelResult = skel.run( AnalyzeSkeleton_.NONE, pruneEnds, shortPath, null, silent, verbose );
		System.out.println( skelResult );
		sc.fiji.analyzeSkeleton.Graph[] oldgraphs = skelResult.getGraph();

		Graph[] graphs = new Graph[ oldgraphs.length ];
		for( int i = 0; i < graphs.length; i++ )
			graphs[ i ] = new Graph( oldgraphs[ i ] );
		
		
		System.out.println( "Found " + graphs.length + " graphs." );
		
		//int j = 0;
		int i = 0;
		for( Graph g : graphs )
		{
			if( g.getVertices().size() > 100 )
			{
				System.out.println( "skeleton "  + (i++) + " has " + g.getVertices().size() + " vertices." );
				HashMap< Edge, DistancePair > result = null;
				if( doShapeMeasure )
				{
					ShapeMeasure sm = new ShapeMeasure( g );
					result = sm.compute();

					String shapeoutname = baseoutputname + "_" + i + "_shape.csv";
					// write the shape measures
					try
					{
						final PrintWriter shapepw = new PrintWriter(new OutputStreamWriter(new FileOutputStream( shapeoutname ), "UTF-8"));
						shapepw.print( sm.print() );
						shapepw.flush();
						shapepw.close();
					} catch ( Exception e )
					{
						e.printStackTrace();
					} 

				}

				ArrayList<SWCPoint> swcPts = graphToSwc( g );
				System.out.println( "  num points of output : "  + swcPts.size());
				
				String outname = baseoutputname + "_" + i + ".swc";
				System.out.println( "Exporting to " + outname );
				try
				{
					final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outname), "UTF-8"));
					flushSWCPoints(swcPts, pw);
				}
				catch (final IOException ioe)
				{
					System.err.println("Saving to " + outname + " failed");
					continue;
				}
				i++;
			}
			//j++;
		}
	}
	
	/*
	 * Taken from Simple Neurite Tracer
	 */
	public static void flushSWCPoints(final ArrayList<SWCPoint> swcPoints, final PrintWriter pw) {
		pw.println("# Exported from \"Simple Neurite Tracer\" version " + SNT.VERSION + " on "
				+ LocalDateTime.of(LocalDate.now(), LocalTime.now()));
		pw.println("# https://imagej.net/Simple_Neurite_Tracer");
		pw.println("#");
//		pw.println("# All positions and radii in " + spacing_units);
//		if (usingNonPhysicalUnits())
//			pw.println("# WARNING: Usage of pixel coordinates does not respect the SWC specification");
//		else
//			pw.println("# Voxel separation (x,y,z): " + x_spacing + ", " + y_spacing + ", " + z_spacing);
		pw.println("#");
		
		for (final SWCPoint p : swcPoints)
			p.println(pw);
		pw.close();
	}

	/**
	 * Converts a Graph to an SWCPoint tree.
	 * 
	 * Largely adapted from Graph.depthFirstSearch
	 * 
	 * @param graph the AnalyzeSkeleton graph
	 * @return list of swc points
	 */
	public static ArrayList< SWCPoint > graphToSwc( Graph graph )
	{
		HashMap<Vertex, ArrayList<Edge>> v2e = verticesToEdges( graph );
		HashMap<Point,Integer> ptSet = new HashMap<Point,Integer>();
		ArrayList<Vertex> processUs = new ArrayList<Vertex>();

		final ArrayList< SWCPoint > result = new ArrayList< SWCPoint >();
		
		int i = 0; 
		Vertex root = graph.getRoot();
		if( add( i, root.getPoints().get( 0 ), -1, ptSet, result )) i++;

		addDownstream( root, v2e, ptSet, processUs, result );
		
		while( !processUs.isEmpty() )
		{
			addDownstream( processUs.remove( 0 ), v2e, ptSet, processUs, result );
		}

		return result;
	}
	
	public static HashMap< Vertex, ArrayList<Edge> > verticesToEdges( Graph g )
	{
		HashMap< Vertex, ArrayList<Edge> > v2e = new HashMap< Vertex, ArrayList<Edge> >();
		for( Edge e : g.getEdges())
		{
			if( !v2e.containsKey( e.getV1() ))
				v2e.put( e.getV1(), new ArrayList<Edge>());

			if( !v2e.containsKey( e.getV2() ))
				v2e.put( e.getV2(), new ArrayList<Edge>());

			v2e.get( e.getV1()).add( e );
			v2e.get( e.getV2()).add( e );
		}
		return v2e;
	}

	/*
	 * A depth first addition of nodes into a tree.
	 * 
	 * Assumes the input vertex v is already in ptSet and result
	 * at the time this method is called, and does not check.
	 */
	private static void addDownstream(
			Vertex v,
			HashMap<Vertex,ArrayList<Edge>> v2e,
			HashMap<Point,Integer> ptSet,
			ArrayList<Vertex> processUs,
			ArrayList< SWCPoint > result
			)
	{
		//System.out.println( "addDownstream " + v.getPoints().get(0));
		for( Edge e : v2e.get( v ))
		{
			Vertex ov = e.getOppositeVertex( v );
			if( ptSet.containsKey( ov.getPoints().get( 0 )) )
				continue;

			int newIdx = ptSet.keySet().size();
			int parentIdx = ptSet.get( v.getPoints().get( 0 ) );

			ArrayList<Point> list = e.getSlabs();
			// TODO I think its okay to do this in place, since we only 
			// touch each edge once
			if( v == e.getV2())
				Collections.reverse( list );
			
			for( Point p : list )
			{
				//System.out.println( "p: " + p );
				if( add( newIdx, p, parentIdx, ptSet, result ))
				{
					parentIdx = newIdx;
					newIdx++;
				}
				else
				{
					System.out.println( "PROBLEM" );
				}
			}

			if( add( newIdx, ov.getPoints().get( 0 ), parentIdx, ptSet, result ))
			{
				newIdx++; 
			}
			else
			{
				System.out.println( "PROBLEM" );
			}
		
			// recurse 
			processUs.add( ov );
		}
	}

	/**
	 * Add a point
	 * @param i index of this vertex 
	 * @param v the vertex 
	 * @param previous index of the vertex's parent 
	 * @param result result list to add to 
	 */
	public static boolean add( 
			int i,
			Point p,
			int parentIndex,
			final HashMap< Point, Integer > pt2idx,
			final ArrayList< SWCPoint > result )
	{
		
		if( pt2idx.containsKey( p ))
			return false;

		//System.out.println( "adding " );
		SWCPoint pt = new SWCPoint( i, Path.SWC_UNDEFINED, 
				p.x, p.y, p.z, 1.0, parentIndex );

		result.add( pt );		
		pt2idx.put( p, i );

		return true;
	}

}
