package net.imglib2.algorithm.stats;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.histogram.BinMapper1d;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.histogram.HistogramNd;
import net.imglib2.histogram.Real1dBinMapper;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.view.Views;

public class MutualInformation
{

	/**
	 * Returns the normalized mutual information of the inputs
	 * @param rai the RandomAccessibleInterval
	 * @param ra the RandomAccessible
	 * @return the normalized mutual information
	 */
	public static <T extends RealType< T >> double nmi(
			RandomAccessibleInterval< T > rai,
			RandomAccessible< T > ra,
			float histmin, float histmax, int numBins )
	{
		HistogramNd< T > jointHist = jointHistogram( rai, ra, histmin, histmax, numBins );

		double HA = marginalEntropy( jointHist, 0 );
		double HB = marginalEntropy( jointHist, 1 );
		double HAB = entropy( jointHist );
		
		double HA2 = entropy( rai, histmin, histmax, numBins );
		double HB2 = entropy( Views.interval( ra, rai ), histmin, histmax, numBins );


		System.out.println(" ");
		System.out.println("HA  " + HA);
		System.out.println("HA2 " + HA2);
		System.out.println("HB  " + HB);
		System.out.println("HB2 " + HB2);
		
		System.out.println("HAB " + HAB);

		return ( HA2 + HB2 ) / HAB; 
	}
	
	/**
	 * Returns the normalized mutual information of the inputs
	 * @param rai the RandomAccessibleInterval
	 * @param ra the RandomAccessible
	 * @return the normalized mutual information
	 */
	public static <T extends RealType< T >> double mi(
			RandomAccessibleInterval< T > rai,
			RandomAccessible< T > ra,
			float histmin, float histmax, int numBins )
	{
		HistogramNd< T > jointHist = jointHistogram( rai, ra, histmin, histmax, numBins );

		double HA = marginalEntropy( jointHist, 0 );
		double HB = marginalEntropy( jointHist, 1 );
		double HAB = entropy( jointHist );
//		System.out.println("HA " + HA);
//		System.out.println("HB " + HB);
//		System.out.println("HAB " + HAB);

		return HA + HB - HAB;
	}
	
	public static <T extends RealType< T >> HistogramNd<T> jointHistogram(
			RandomAccessibleInterval< T > rai,
			RandomAccessible< T > ra,
			float histmin, float histmax, int numBins )
	{
		Real1dBinMapper<T> binMapper = new Real1dBinMapper<T>(
				histmin, histmax, numBins, false );
		ArrayList<BinMapper1d<T>> binMappers = new ArrayList<BinMapper1d<T>>( 2 );
		binMappers.add( binMapper );
		binMappers.add( binMapper );
		
		List<Iterable<T>> data = new ArrayList<Iterable<T>>( 2 );
		data.add( Views.flatIterable( rai ));
		data.add( Views.interval( ra, rai ));
		return new HistogramNd<T>( data, binMappers );
	}
	
	/**
	 * Returns the joint entropy of the inputs
	 * @param rai the RandomAccessibleInterval
	 * @param ra the RandomAccessible
	 * @return the joint entropy 
	 */
	public static <T extends RealType< T >> double jointEntropy(
			RandomAccessibleInterval< T > rai,
			RandomAccessible< T > ra,
			float histmin, float histmax, int numBins )
	{
		return entropy( jointHistogram( rai, ra, histmin, histmax, numBins ));
	}

	/**
	 * Returns the entropy of the input
	 * @param rai the RandomAccessibleInterval
	 * @return the entropy 
	 */
	public static <T extends RealType< T >> double entropy(
			RandomAccessibleInterval< T > rai,
			float histmin, float histmax, int numBins )
	{
		
		Real1dBinMapper<T> binMapper = new Real1dBinMapper<T>(
				histmin, histmax, numBins, false );
		final Histogram1d<T> hist = new Histogram1d<T>( binMapper );
		hist.countData( Views.flatIterable( rai ));

		return entropy( hist );
	}

	/**
	 * Computes the entropy of the input 1d histogram.
	 * @param hist the histogram
	 * @return the entropy
	 */ 
	public static <T extends RealType< T >> double entropy( Histogram1d<T> hist )
	{
		double entropy = 0.0;
		for( int i = 0; i < hist.getBinCount(); i++ )
		{
			double p = hist.relativeFrequency( i, false );
			if( p > 0 )
				entropy -= p * Math.log( p );
		}
		return entropy;
	}

	/**
	 * Computes the entropy of the input nd histogram.
	 * @param hist the histogram
	 * @return the entropy
	 */ 
	public static <T extends RealType< T >> double entropy( HistogramNd<T> hist )
	{
		double entropy = 0.0;
		Cursor< LongType > hc = hist.cursor();
		long[] pos = new long[ hc.numDimensions() ];

		while( hc.hasNext() )
		{
			hc.fwd();
			hc.localize( pos );
			double p = hist.relativeFrequency( pos, false );
			System.out.println( "    ep : " + p );
			if( p > 0 )
				entropy -= p * Math.log( p );

		}
		return entropy;
	}
	
	public static <T extends RealType< T >> double marginalEntropy( HistogramNd<T> hist, int dim )
	{
		final long ni = hist.dimension( dim );
		final long total = hist.totalCount();
		long count = 0;
		double entropy = 0.0;
		for( int i = 0; i < ni; i++ )
		{
			count = subHistCount( hist, dim, i );
			double p = 1.0 * count / total;

			System.out.println( "   mep : " + p );
			if( p > 0 )
				entropy -= p * Math.log( p );
		}
//		System.out.println("  me total: " + total);
//		System.out.println("  me count: " + count);
		return entropy;
	}
	
	private static <T extends RealType< T >> long subHistCount( HistogramNd<T> hist, int dim, int pos )
	{
		long count = 0;
		Cursor< LongType > c = Views.hyperSlice( hist, dim, pos ).cursor();
		while( c.hasNext() )
		{
			count += c.next().get();
		}
		return count;
	}
}
