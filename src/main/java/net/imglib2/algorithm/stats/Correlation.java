package net.imglib2.algorithm.stats;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class Correlation
{

	public static <T extends RealType< T >, S extends RealType< S >> double correlation(
			RandomAccessibleInterval< T > rai,
			RandomAccessible< S > ra )
	{
//		MeanStd meanStd1 = meanAndStd( Views.flatIterable( rai ) );
//		MeanStd meanStd2 = meanAndStd( Views.interval( ra, rai ) );
//
//		System.out.println( "ms1: " + meanStd1 );
//		System.out.println( "ms2: " + meanStd2 );

		double sumA = 0;
		double sumSqrA = 0;
		double sumB = 0;
		double sumSqrB = 0;
		double sumAB = 0;
		int n = 0;

//		IterableInterval< T > it1 = Views.flatIterable( rai );
//		IntervalView< S > it2 = Views.interval( ra, rai );

		IntervalView< Pair< T, S > > pairIt = Views.interval( Views.pair( rai, ra ), rai);
		
		for (final Pair<T,S> pair : pairIt )
		{
			final double u = pair.getA().getRealDouble();
			final double v = pair.getB().getRealDouble();

			sumA += u;
			sumSqrA += u * u;
			sumB += v;
			sumSqrB += v * v;

			sumAB += u * v;

			++n;
		}

		double numerator = sumAB - ( sumA * sumB / n );
		double denominator = Math.sqrt( sumSqrA - sumA * sumA / n ) * 
				 			 Math.sqrt( sumSqrB - sumB * sumB / n );

		return numerator / denominator;
	}

	public static <T extends RealType< T >> double mean( Iterable< T > in )
	{
		// Taken from imagejops but dont want it as a dep
		double sum = 0;
		double size = 0;

		for ( final T v : in )
		{
			sum += v.getRealDouble();
			size++;
		}
		return sum / size;
	}

	public static <T extends RealType< T >> double std( Iterable< T > in )
	{
		double sum = 0;
		double sumSqr = 0;
		int n = 0;

		for (final T v : in ) {
			final double px = v.getRealDouble();
			++n;
			sum += px;
			sumSqr += px * px;
		}
		return Math.sqrt((sumSqr - (sum * sum / n)) / (n - 1));
	}

	public static <T extends RealType< T >> MeanStd meanAndStd( Iterable< T > in )
	{
		double sum = 0;
		double sumSqr = 0;
		int n = 0;

		for (final T v : in ) {
			final double px = v.getRealDouble();
			++n;
			sum += px;
			sumSqr += px * px;
		}
		double mean = sum / n;
		double std = Math.sqrt((sumSqr - (sum * sum / n)) / (n - 1));
		return new MeanStd( mean, std );
	}

	public static class MeanStd
	{
		public final double mean;
		public final double std;
		public MeanStd( double mean, double std )
		{
			this.mean = mean;
			this.std = std;
		}
		public String toString()
		{
			return mean + " " + std;
		}
	}
}
