package org.janelia.saalfeldlab.registrationGraph;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class RegistrationPath {
	
	private final Space start;
	
	private final RegistrationPath parentPath;
	
	private final Transform transform;

	private final Space end;

	public RegistrationPath( final Transform transform ) {

		this.start = transform.getSource();
		this.transform = transform;
		this.end = transform.getDestination();
		this.parentPath = null;
	}
	
	public RegistrationPath( final RegistrationPath parentPath, final Transform transform ) {

		this.start = parentPath.getStart();
		this.parentPath = parentPath;
		this.transform = transform;
		this.end = transform.getDestination();
	}

//	public RegistrationPath( final Space start,
//			final RegistrationPath parentPath,
//			final Transform transform,
//			final Space end ) {
//
//		this.start = start;
//		this.parentPath = parentPath;
//		this.transform = transform;
//		this.end = end;
//	}
	
	public Space getStart()
	{
		return start;
	}
	
	public Space getEnd()
	{
		return end;
	}

	/**
	 * Does this path run through the given space.
	 * 
	 * @param space the space
	 * @return true if this path contains space
	 */
	public boolean hasSpace( final Space space )
	{
		if ( start.equals( space ) || end.equals( space ))
			return true;

		if( parentPath != null )
			return parentPath.hasSpace( space );

		return false;
	}
	
	public List<Transform> flatTransforms()
	{
		LinkedList<Transform> flatTransforms = new LinkedList<>();
		flatTransforms( flatTransforms );
		return flatTransforms;
	}

	private void flatTransforms( LinkedList<Transform> queue )
	{
		if( transform != null )
			queue.addFirst( transform );

		if( parentPath != null )
			parentPath.flatTransforms( queue );
	}

	public List<Space> flatSpace()
	{
		LinkedList<Space> flatSpace = new LinkedList<>();
		flatSpace( flatSpace );
//		if( parentPath == null )
		flatSpace.addFirst( start );
		return flatSpace;
	}

	private void flatSpace( LinkedList<Space> queue )
	{
		if( end != null )
			queue.addFirst( end );

		if( parentPath != null )
			parentPath.flatSpace( queue );
	}
	
	public String toString()
	{
		List<Space> spaceList = flatSpace();
		List<Transform> transformList = flatTransforms();

		if( transformList.size() < 1 )
			return "(" + spaceList.get(0) + ")";

		StringBuffer out = new StringBuffer();
		for( int i = 0; i < transformList.size(); i++ )
		{
			out.append( "("+spaceList.get(i));
			out.append( ") --" );
			out.append( transformList.get(i));
			out.append( "-> " );
		}
		out.append( "(" + end  + ")");

		return out.toString();
	}

//	public void add(Space s, Transform t) {
//		spaces.add( s );
//		transforms.add( t );
//	}

}
