package process;

import java.io.File;
import java.io.IOException;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import net.imglib2.RandomAccess;
import net.imglib2.algorithm.stats.RayleighDistribution;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.histogram.Real1dBinMapper;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;


/**
 * Makes a sorted plot of image intensities in the given interval
 *
 */
public class PlotSubsetWithRayleigh extends PlotSubset
{
	
	@Override
	public void process() throws IOException
	{

		double[] vals = getData();

		JFreeChart chart;
		chart = histPlotWithRayleigh( "histogram", "value", "count", vals );

		if( show )
		{
			ChartFrame frame = new ChartFrame( "", chart );
			frame.pack();
			frame.setVisible( true );
		}	
		
		if( outputPath != null && !outputPath.isEmpty())
		{
			ChartUtilities.saveChartAsPNG( new File( outputPath ), chart, 800, 600 );
		}
	}
	
	public JFreeChart histPlotWithRayleigh( String title, String xAxisLabel, String valueAxisLabel,
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
		
		RayleighDistribution rd = new RayleighDistribution();
		rd.fit( img );
		System.out.println( rd.getSigma() );
		
		final XYSeries series = new XYSeries( "data" );
		final XYSeries bestFit = new XYSeries( "rayleigh fit" );
		DoubleType center = new DoubleType();
		double binSpacing = ( histMax - histMin ) / hist.getBinCount();
		System.out.println( "binSpacing: " + binSpacing );
		for ( int i = 0; i < hist.getBinCount(); i++ )
		{
			hist.getCenterValue( i, center );

			series.add( center.get(), hist.frequency( i ) / (binSpacing * hist.totalCount()) );
			bestFit.add( center.get(), rd.pdfAt( center.get()));
//			System.out.println( String.format( "bin %d (center %f) count %d", i, center.get(), hist.frequency( i )));
		}

		final XYSeriesCollection collection = new XYSeriesCollection();
		collection.addSeries( series );
		collection.addSeries( bestFit );

		JFreeChart chart = ChartFactory.createXYLineChart( title, xAxisLabel,
				valueAxisLabel, collection );
		
		return chart;
	}

	public static PlotSubsetWithRayleigh parseCommandLineArgs( final String[] args )
	{
		PlotSubsetWithRayleigh ds = new PlotSubsetWithRayleigh();
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
		PlotSubsetWithRayleigh plotter = PlotSubsetWithRayleigh.parseCommandLineArgs( args );
		plotter.process();
	}
}
