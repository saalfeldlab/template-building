package org.janelia.saalfeldlab.registrationGraph.persistence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.janelia.saalfeldlab.registrationGraph.Space;
import org.janelia.saalfeldlab.registrationGraph.Transform;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import picocli.CommandLine;
import picocli.CommandLine.Option;

public class TransformDatabase implements Callable<Void> {

	@Option( names = { "-i", "--database" }, required = true,
			description = "Path to database (json)." )
	private String databasePath;

	@Option( names = { "-o", "--out-database" }, required = false,
			description = "Path to output database (json), defaults to the input." )
	private String outdatabasePath;

	@Option( names = { "-a", "--add" }, required = false,
			description = "Add transform" )
	private List<String> transformsToAdd;

	private List<Transform> transforms;

	private Set<Transform> transformSet;

	private Map<String,Space> spaceMap;

	private final static Gson gson = new Gson();

	public static void main(String... args) {

		new CommandLine(new TransformDatabase()).execute(args);
		System.exit(0);
	}

	public TransformDatabase() {

		// create an empty database
		transforms = new ArrayList<>();
		transformSet = new HashSet<>();
		spaceMap = new HashMap<>();
	}

	public TransformDatabase(final List<Transform> transforms) {

		this.transforms = transforms;
		transformSet = new HashSet<>();
		spaceMap = new HashMap<>();

		this.transforms.forEach( transformSet::add );
		this.transforms.forEach( t -> {
			spaceMap.put( t.getSource().getName(), t.getSource());
			spaceMap.put( t.getDestination().getName(), t.getDestination());
		});
	}

	public void add( Transform transform )
	{
		System.out.println( "add: " + transform );
		if( transformSet.add( transform ))
			transforms.add( transform );
	}

	public List<Transform> getTransforms()
	{
		return transforms;
	}
	
	public void save( File f ) throws IOException
	{
		try (Writer writer = new FileWriter( f.getAbsoluteFile() )) {
		    gson.toJson(transforms, writer);
		}
	}

	public static TransformDatabase load( File f ) 
	{
		Transform[] transforms;
		try {
			transforms = gson.fromJson(new FileReader( f ), Transform[].class );
			return new TransformDatabase( Arrays.asList( transforms ));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Void call() throws Exception {
		
		File dbF = new File( databasePath );

		Transform[] transforms = null;
		if( dbF.exists() )
			transforms = gson.fromJson(new FileReader( new File( databasePath )), Transform[].class );
		else
			System.out.println( "creating new file" );
		
		System.out.println( transforms );
		System.out.println( transforms.length );

		TransformDatabase db;
		if( transforms != null )
			db = new TransformDatabase( Arrays.asList(transforms));
		else
			db = new TransformDatabase();

		if( transformsToAdd != null )
		{
			for( String tString : transformsToAdd )
			{
				Transform t = Transform.fromString(tString, spaceMap);
				db.add( t );
			}
		}

		return null;
	}
	

}
