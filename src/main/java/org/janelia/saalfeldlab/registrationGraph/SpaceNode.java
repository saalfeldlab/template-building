package org.janelia.saalfeldlab.registrationGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * A node in a {@link RegistrationGraph}.
 * 
 * Edges are directed with this node as their base.
 * 
 * @author John Bogovic
 */
public class SpaceNode
{
	private final Space space;

	private final List< Transform > edges;

	public SpaceNode( final Space space )
	{
		this( space, new ArrayList< Transform >() );
	}

	public SpaceNode( final Space space, List< Transform > edges )
	{
		this.space = space;
		this.edges = edges;
	}

	public Space space()
	{
		return space;
	}
	
	public List<Transform> edges()
	{
		return edges;
	}
	
	@Override
	public boolean equals( Object other )
	{
		return space.equals(other);
	}

}
