package net.imglib2.algorithm.stats;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bdv.util.ConstantRandomAccessible;
import io.IOHelper;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.2.0-SNAPSHOT" )
public class LabelwiseStatistics< L extends IntegerType< L >, T extends RealType< T > > implements Callable< Void >
{

	public static long NO_ALL_LABEL = Long.MIN_VALUE;

	@Option( names = { "-i", "--input" }, required = true, description = "Image to compute the statistics for." )
	private List< String > inputs;

	@Option( names = { "-o", "--output" }, required = false, description = "Output file." )
	private String output;

	@Option( names = { "-l", "--labelVolume" }, required = false, description = "File containing the discrete labels." )
	private List< String > labelVolumes;

	@Option( names = { "-a", "--all-label" }, required = false, 
			description = "If this value is set, then another label with this value is added to the table and "
					+ "gives the statistics for all values over all non-background labels. Long.MIN_VALUE (-9223372036854775808) is disallowed." )
	private long allLabel = NO_ALL_LABEL;

	@Option( names = { "-b", "--background" }, required = false, description = "The background label (default = ${DEFAULT-VALUE})." )
	private long backgroundLabel = 0;

	@Option( names = { "-d", "--delimeter" }, required = false, description = "The delimeter. (default = ${DEFAULT-VALUE})." )
	private String delimeter = ",";

	@Option( names = { "-f", "--format" }, required = false, description = "The float format string. (default = ${DEFAULT-VALUE})." )
	private String format = "%f";

	@Option( names = { "-v", "--version" }, required = false, versionHelp = true, description = "Prints version information and exits." )
	private boolean version;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Print this help message" )
	private boolean help;

	HashMap< Long, Double > means;

	HashMap< Long, Double > vars;

	HashMap< Long, Long > counts;

	// need to keep track of this for online computation of variance
	HashMap< Long, Double > m2s;

	final Logger logger = LoggerFactory.getLogger( LabelwiseStatistics.class );

	public LabelwiseStatistics()
	{
		means = new HashMap<>();
		counts = new HashMap<>();
		vars = new HashMap<>();
		m2s = new HashMap<>();
	}

	public static void main( String[] args )
	{
		CommandLine.call( new LabelwiseStatistics<>(), args );
	}

	@Override
	public Void call() throws Exception
	{
		run();
		return null;
	}

	@SuppressWarnings( "unchecked" )
	public void run()
	{
		IOHelper io = new IOHelper();
		RandomAccessibleInterval< ? > fixedLabels = null;

//		if ( labelVolumes.size() < 1 )
//		{
//			System.err.println( "Must supply at least one label volume." );
//			return;
//		}
//		else if ( labelVolumes.size() == 1 )
//		{
//			logger.info( "reading " + labelVolumes.get( 0 ));
//			fixedLabels = io.readRai( new File( labelVolumes.get( 0 ) ) );
//		}

		if ( labelVolumes != null && labelVolumes.size() == 1 )
		{
			logger.info( "reading " + labelVolumes.get( 0 ));
			fixedLabels = io.readRai( new File( labelVolumes.get( 0 ) ) );
		}

		RandomAccessibleInterval< L > labels = null;
		RandomAccessibleInterval< T > img = null;
		for ( int i = 0; i < inputs.size(); i++ )
		{
			logger.info( "reading " + inputs.get( i ));
			img = ( RandomAccessibleInterval< T > ) io.readRai( new File( inputs.get( i ) ) );

			if ( fixedLabels != null )
			{
				labels = ( RandomAccessibleInterval< L > ) fixedLabels;
			}
			else
			{
				if( labelVolumes == null || labelVolumes.isEmpty()  )
				{
					labels = Views.interval(
							new ConstantRandomAccessible( new IntType(1), img.numDimensions()),
							img);
				}
				else
				{
					logger.info( "reading " + labelVolumes.get( i ));
					labels = ( RandomAccessibleInterval< L > ) io.readRai( new File( labelVolumes.get( i ) ) );
				}
			}

			process( img, labels );
			img = null;
		}

		PrintWriter out = null;
		if ( output == null || output.isEmpty() )
		{
			logger.info("output empty");
			out = System.console().writer();
		}
		else
		{
			logger.info("output empty");
			try
			{
				out = new PrintWriter( new File( output ) );
			}
			catch ( FileNotFoundException e )
			{
				e.printStackTrace();
				return;
			}
		}

		out.println( "label,count,mean,variance,stddev" );
		ArrayList< String > strings = new ArrayList<>();

		for ( Long l : means.keySet() )
		{
			strings.add( l.toString() );
			strings.add( counts.get( l ).toString() );
			strings.add( String.format( format, means.get( l ) ) );
			strings.add( String.format( format, vars.get( l ) ) );
			strings.add( String.format( format, Math.sqrt( vars.get( l ) ) ) );

			out.println( strings.stream().collect( Collectors.joining( delimeter ) ) );
			strings.clear();
		}
		out.flush();
	}

	public void process( RandomAccessibleInterval< T > img, RandomAccessibleInterval< L > labels )
	{
		logger.info("process");

		if( allLabel != NO_ALL_LABEL && !counts.containsKey( allLabel ))
		{
			logger.info( "merged statistics go to label: " + allLabel );
			counts.put( allLabel, new Long( 0 ) );
			means.put( allLabel, new Double( 0 ) );
			m2s.put( allLabel, new Double( 0 ) );
		}

		Cursor< T > c = Views.flatIterable( img ).cursor();
		RandomAccess< L > ra = labels.randomAccess();

		while ( c.hasNext() )
		{
			c.fwd();
			ra.setPosition( c );

			T val = c.get();
			double x = val.getRealDouble();
			L labeltype = ra.get();
			long label = labeltype.getIntegerLong();

			if ( label == backgroundLabel )
				continue;

			if ( !counts.containsKey( label ) )
			{
				// add values table if we have not yet encountered 'label'
				counts.put( label, new Long( 0 ) );
				means.put( label, new Double( 0 ) );
				m2s.put( label, new Double( 0 ) );
			}

			update( label, x );

			if( allLabel != NO_ALL_LABEL )
				update( allLabel, x );
		}

		for ( Long label : means.keySet() )
		{
			vars.put( label, m2s.get( label ) / counts.get( label ) );
		}
	}

	protected void update( long label, double x )
	{
		// update the value for 'label'
		counts.put( label, counts.get( label ) + 1 );

		long count = counts.get( label );

		double mn = means.get( label );

		double del = x - mn;
		mn += del / count;
		double del2 = x - mn;

		means.put( label, mn );

		double m2 = m2s.get( label );
		m2s.put( label, m2 + ( del * del2 ) );
	}

	public HashMap< Long, Double > getMeans()
	{
		return means;
	}

	public HashMap< Long, Double > getVars()
	{
		return vars;
	}

	public HashMap< Long, Long > getCounts()
	{
		return counts;
	}

	public HashMap< Long, Double > getM2()
	{
		return m2s;
	}

}
