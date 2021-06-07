package org.janelia.saalfeldlab.registrationGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;


public class RegistrationGraphTest {

	@Test
	public void testPaths()
	{
		final Space a = new Space("a");
		final Space b = new Space("b");
		final Space c = new Space("c");

		final RegistrationPath ab = new RegistrationPath( new Transform("",a,b) );
		final RegistrationPath abc = new RegistrationPath( ab, new Transform("",b,c) );
		
		Assert.assertEquals( "path a->b start", a, ab.getStart());
		Assert.assertEquals( "path a->b end", b, ab.getEnd());
		Assert.assertEquals( "path a->b length", 1, ab.flatTransforms().size());

		Assert.assertEquals( "path a->c start", a, abc.getStart());
		Assert.assertEquals( "path a->c end", c, abc.getEnd());
		Assert.assertEquals( "path a->c length", 2, abc.flatTransforms().size());
		
	}
	
	@Test
	public void testPathFinding()
	{
		final Space a = new Space("a");
		final Space b = new Space("b");
		final Space c = new Space("c");
		final Space d = new Space("d"); // an unreachable space
		
		ArrayList<Transform> transforms = new ArrayList<>();
		transforms.add( new Transform("a-to-b", a, b ));
		transforms.add( new Transform("b-to-a", b, a ));
		transforms.add( new Transform("b-to-c", b, c ));
		transforms.add( new Transform("c-to-b", c, b ));

		transforms.add( new Transform("a-to-b-2", a, b, "a-to-b-2", 2  ));

		final RegistrationGraph graph = new RegistrationGraph(transforms);
		Optional<RegistrationPath> ab = graph.path(a, b);
		Optional<RegistrationPath> ac = graph.path(a, c);

		Assert.assertTrue("ab exists", ab.isPresent());
		final RegistrationPath abPath = ab.get();
		Assert.assertEquals("ab start", a, abPath.getStart());
		Assert.assertEquals("ab end", b, abPath.getEnd());
		Assert.assertEquals("ab cost", 1.0, abPath.getCost(), 1e-9);
		Assert.assertEquals("ab name", "a-to-b", abPath.flatTransforms().get( 0 ).getPath());

		List<RegistrationPath> a2bPaths = graph.paths(a,b);
		Assert.assertEquals("num a2b", 2, a2bPaths.size());

		Assert.assertTrue("ac exists", ac.isPresent());
		Assert.assertEquals("ac start", a, ac.get().getStart());
		Assert.assertEquals("ac end", c, ac.get().getEnd());

	}
}
