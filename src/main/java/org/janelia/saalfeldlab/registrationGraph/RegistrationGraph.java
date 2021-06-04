package org.janelia.saalfeldlab.registrationGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;

public class RegistrationGraph
{
	public static final Gson gson = new Gson();

	private final List< Transform > transforms;

	private final HashMap< Space, SpaceNode > spaces;
	
	public RegistrationGraph( List< Transform > transforms )
	{
		this.transforms = transforms;
		
		spaces = new HashMap< Space, SpaceNode >();
		for( Transform t : transforms )
		{
			final Space src = t.getSource();
			if( getSpaces().containsKey( src ))
				getSpaces().get( src ).edges().add( t );
			else
			{
				SpaceNode node = new SpaceNode( src );
				node.edges().add( t );
				getSpaces().put( src, node );
			}
		}
	}

	public HashMap< Space, SpaceNode > getSpaces()
	{
		return spaces;
	}

	public Optional<RegistrationPath> path(final Space from, final Space to ) {

		return allPaths( from ).stream().filter( p -> p.getEnd().equals(to)).findFirst();
	}

	public List<RegistrationPath> allPaths(final Space from) {

		final ArrayList<RegistrationPath> paths = new ArrayList<RegistrationPath>();
		allPathsHelper( paths, from, null );
		return paths;
	}

	private void allPathsHelper(final List<RegistrationPath> paths, final Space start, final RegistrationPath pathToStart) {

		SpaceNode node = spaces.get(start);

		List<Transform> edges = null;
		if( node != null )
			edges = spaces.get(start).edges();

		if( edges == null || edges.size() == 0 )
			return;

		for (Transform t : edges) {
			final Space end = t.getDestination();
			
			if( pathToStart != null && pathToStart.hasSpace( end ))
				continue;

			final RegistrationPath p;
			if (pathToStart == null )
				p = new RegistrationPath( t );
			else
				p = new RegistrationPath( pathToStart, t );	

			paths.add(p);
			allPathsHelper(paths, end, p);
		}
	}
	
}
