package process;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.janelia.utility.parse.ParseUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.histogram.Real1dBinMapper;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;


/**
 * Makes a sorted plot of image intensities in the given interval
 *
 */
public class PlotSubset
{

	protected transient JCommander jCommander;

	@Parameter(names = {"--start", "-s"}, description = "Start (min) of interval", required = true, 
			converter = ParseUtils.LongArrayConverter.class )
	protected long[] start;
	
	@Parameter(names = {"--width", "-w"}, description = "Width of interval", 
			converter = ParseUtils.LongArrayConverter.class )
	protected long[] widthIn = new long[]{ 1 };
	
	@Parameter(names = {"--end", "-e"}, description = "End (max) of interval", 
			converter = ParseUtils.LongArrayConverter.class )
	protected long[] endIn;
	
	@Parameter(names = {"--output", "-o"}, description = "Output png/json file" )
	protected String outputPath;
	
	@Parameter(names = {"--histMin"}, description = "Center of lowest histogram bin" )
	protected double histMin = 0.0;

	@Parameter(names = {"--histMax"}, description = "Center of highest histogram bin" )
	protected double histMax = 255.0;
	
	@Parameter(names = {"--numBins", "-n"}, description = "Number of histogram bins" )
	protected long nBins = 256;
	
	@Parameter(names = "--show", description = "Show the chart")
	protected boolean show = false;

	@Parameter(names = {"--hist", "--histogram", "-h"}, description = "Plot a histogram instead of the raw data")
	protected boolean doHistogram = false;
	
	@Parameter( description="input images")
	protected List<String> imagePathList;
	

	protected long[] end;
	protected long[] width;
	
	public double[] getData()
	{
		System.out.println( imagePathList );
		
//		double[] vals = new double[]{ 1, 2, 4, 8, 16, 12 };
		
		int nd = start.length;
		width = checkAndFillArrays( widthIn, nd, "width" );
		end = checkAndFillArrays( endIn, nd, "end" );
		
		if( end == null )
		{
			end = new long[ nd ];
			for( int d = 0; d < nd;  d++ )
				end[ d ] = start[ d ] + width[ d ] - 1;
		}

		FinalInterval interval = new FinalInterval( start, end );
		System.out.println( Util.printInterval( interval ));
		
		// 
		int nImages = imagePathList.size();
		int nValuesPerImage = (int)Intervals.numElements( interval ); 
		int nValues = nValuesPerImage * nImages; 
		
		double[] vals = new double[ nValues ];
		
		int i = 0;
		for( String imgPath : imagePathList )
		{
			System.out.println( imgPath );
//			Img<FloatType> img = ImageJFunctions.wrapFloat( 
//					IJ.openImage( imgPath ));
			
			ImagePlus ip = IJ.openImage( imgPath );
			System.out.println( ip );
			Img<FloatType> img = ImageJFunctions.convertFloat( ip );

			Cursor< FloatType > c = Views.interval( img, interval ).cursor();
			while( c.hasNext() )
				vals[ i++ ] = c.next().getRealDouble();
		}
		return vals;
	}

	public void process() throws IOException
	{

		double[] vals = getData();
//
//		System.out.println( vals.length );
//		System.out.println( "val 0: " + vals[0] );
//		System.out.println( "val 1: " + vals[1] );

		JFreeChart chart;
		if( doHistogram )
		{
			chart = histPlot( "histogram", "value", "count", vals );
		}
		else {
			Arrays.sort( vals );
			chart = rawDataPlot( "", "index", "value", vals );
		}

		if( show )
		{
			ChartFrame frame = new ChartFrame( "", chart );
			frame.pack();
			frame.setVisible( true );
		}	
		
		if( outputPath != null && !outputPath.isEmpty())
		{
			ChartUtils.saveChartAsPNG( new File( outputPath ), chart, 800, 600 );
		}
	}

	public JFreeChart histPlot( String title, String xAxisLabel, String valueAxisLabel,
			double[] data )
	{
		ArrayImg< DoubleType, DoubleArray > img = ArrayImgs.doubles( data, data.length );
		System.out.println( "histMin: " + histMin );
		System.out.println( "histMax: " + histMax );
		Real1dBinMapper< DoubleType > binMapper = new Real1dBinMapper< DoubleType >(
				histMin, histMax, nBins, false );
		final Histogram1d< DoubleType > hist = new Histogram1d< DoubleType >( binMapper );
		hist.countData( img );
		RandomAccess< LongType > hra = hist.randomAccess();

		final XYSeries series = new XYSeries( "data" );
		DoubleType center = new DoubleType();
		for ( int i = 0; i < hist.getBinCount(); i++ )
		{
			hist.getCenterValue( i, center );
			hra.setPosition( i, 0 );
			series.add( center.get(), hra.get().getRealDouble() );
//			series.add( i, hra.get().getRealDouble() );
			System.out.println( String.format( "bin %d (center %f) count %f", i, center.get(), hra.get().getRealDouble()));
		}
		
//		final HistogramDataset histData = new HistogramDataset();
//		DoubleType tmp = new DoubleType();
//		double[] values = new double[ (int)hist.getBinCount() ];
//		for ( int i = 0; i < hist.getBinCount(); i++ )
//		{
//			hist.getCenterValue( i, tmp );
//			hra.setPosition( i, 0 );
//			values[ i ] = hra.get().getRealDouble();
//			System.out.println( String.format( "bin %d (center %f) count %f", i, tmp.get(), hra.get().getRealDouble()));
//		}
//
//		histData.addSeries( "hist", values, values.length, histMin, histMax );
//		JFreeChart chart = ChartFactory.createHistogram( title, xAxisLabel,
//				valueAxisLabel, histData, PlotOrientation.VERTICAL, false, true, false );

//		XYBarRenderer renderer = (XYBarRenderer)chart.getXYPlot().getRenderer();
//		renderer.
		
		final XYSeriesCollection collection = new XYSeriesCollection();
		collection.addSeries( series );
		JFreeChart chart = ChartFactory.createXYLineChart( title, xAxisLabel,
				valueAxisLabel, collection );
		
		return chart;
	}

	public static JFreeChart rawDataPlot( String title, String timeAxisLabel,
			String valueAxisLabel, double[] data )
	{
		final XYSeries series = new XYSeries( "data" );
		for ( int i = 0; i < data.length; i++ )
		{
			series.add( i, data[ i ] );
		}

		final XYSeriesCollection collection = new XYSeriesCollection();
		collection.addSeries( series );

		JFreeChart chart = ChartFactory.createXYLineChart( title, timeAxisLabel,
				valueAxisLabel, collection );
		
		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaint( Color.lightGray );
//		plot.getDomainAxis().setVisible( false );

		return chart;

	}

	public  long[] checkAndFillArrays( long[] in, int nd, String kind )
	{
		if( in == null )
			return null;

		if( in.length == 1 )
		{
			long[] out = new long[ nd ];
			Arrays.fill( out, in[ 0 ]);
			return out;
		}
		else if( in.length == nd )
			return in;
		else
		{
			System.err.println( "Error interpreting " + kind + " : " + 
					" image has " + nd + " dimensions, and input is of length " + in.length + 
					".  Expected length 1 or " + nd );
		}
		return null;
	}

	protected void initCommander()
	{
		jCommander = new JCommander( this );
		jCommander.setProgramName( "input parser" ); 
	}

	public static PlotSubset parseCommandLineArgs( final String[] args )
	{
		PlotSubset ds = new PlotSubset();
		ds.initCommander();
		try 
		{
			ds.jCommander.parse( args );
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		return ds;
	}
	/**
	 * Makes 
	 * @param args
	 * @throws IOException 
	 */
	public static void main( String[] args ) throws IOException
	{
		PlotSubset plotter = PlotSubset.parseCommandLineArgs( args );
		plotter.process();
	}

}
