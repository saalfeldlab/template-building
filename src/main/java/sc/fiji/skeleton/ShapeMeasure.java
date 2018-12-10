package sc.fiji.skeleton;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import sc.fiji.analyzeSkeleton.Edge;
import sc.fiji.skeleton.Graph;
import sc.fiji.analyzeSkeleton.Point;

import sc.fiji.skeleton.SwcAsGraph;

public class ShapeMeasure
{

	private final Graph g;

	private final HashMap< Edge, DistancePair > distancePerBranch;

	public ShapeMeasure( Graph g )
	{ 
		this.g = g;
		distancePerBranch = new HashMap< Edge, DistancePair >();
	}

	public HashMap<Edge,DistancePair> compute()
	{
		distancePerBranch.clear();
		for( Edge e : g.getEdges() )
		{
			distancePerBranch.put( e, edgeDistance( e ));
		}
		return distancePerBranch;
	}
	
	protected DistancePair edgeDistance( Edge e )
	{
	
		double totalDist = 0.0;

		Point v = e.getV1().getPoints().get( 0 );
		Point w = e.getV2().getPoints().get( 0 );

		ArrayList< Point > slabs = e.getSlabs();

		Point basePt = v;
		for( Point slabPt : slabs )
		{
			totalDist += distance( basePt, slabPt );
			basePt = slabPt;
		}
		totalDist += distance( basePt, w );
		
		return new DistancePair( totalDist, distance( v, w ));
	}

	public static double distance( Point p, Point q )
	{
		double xd = p.x - q.x;
		double yd = p.y - q.y;
		double zd = p.z - q.z;
		return Math.sqrt( xd*xd + yd*yd + zd*zd );
	}
	
	public String print()
	{
		StringBuffer bufOut = new StringBuffer();
		bufOut.append( "P1,P2,LOWER,TOTAL\n" );
		for( Edge e : distancePerBranch.keySet())
		{
			Point p1 = e.getV1().getPoints().get( 0 );
			Point p2 = e.getV2().getPoints().get( 0 );
			DistancePair dp = distancePerBranch.get( e );
			bufOut.append(String.format("(%d;%d;%d),", p1.x, p1.y, p1.z ));
			bufOut.append(String.format("(%d;%d;%d),", p2.x, p2.y, p2.z ));
			bufOut.append(String.format("%f,%f", dp.LOWER, dp.TOTAL ));
			bufOut.append("\n");
		}
		return bufOut.toString();
	}
	
	public static class DistancePair {
		public final double TOTAL;
		public final double LOWER;
		public DistancePair( double TOTAL, double LOWER )
		{
			this.TOTAL = TOTAL;
			this.LOWER = LOWER;
			
			if( LOWER > TOTAL )
				System.err.println("LOWER < TOTAL");
		}
	}

	public static void main( String[] args )
	{
		String skelOrg = "/nrs/saalfeld/john/projects/flyChemStainAtlas/eval/ants_ag/nblasteval/20161102_32_C1_Scope_#1_C2_Skeleton_1_xfm.swc";
		String skelXfm = "/nrs/saalfeld/john/projects/flyChemStainAtlas/eval/ants_ag/nblasteval/20161102_32_C1_Scope_#1_C2_Skeleton_1_xfm.swc";
		
		Graph gOrig = SwcAsGraph.load( new File( skelOrg )); 
		//System.out.println( "num edges " + gOrig.getEdges().size());
		//Graph gXfmd = SwcAsGraph.load( new File( skelXfm )); 

		ShapeMeasure sm = new ShapeMeasure( gOrig );
		//System.out.println( sm );
		sm.compute();
		System.out.println( sm.print());

	}
}
