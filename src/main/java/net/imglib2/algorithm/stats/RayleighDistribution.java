package net.imglib2.algorithm.stats;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

public class RayleighDistribution
{
	private static final double SQRT_PI_OVER_2 = Math.sqrt( Math.PI / 2 );

	private double sigmaSquared;
	private long count;
	
	public RayleighDistribution()
	{
		sigmaSquared = 0;
		count = 0;
	}

	public double getSigma()
	{
		return Math.sqrt( sigmaSquared );
	}

	public double getSigmaSquared()
	{
		return sigmaSquared;
	}
	
	public double getMean()
	{
		return getSigma() * SQRT_PI_OVER_2;
	}
	
	public double getMode()
	{
		return getSigma();
	}

	public double getVariance()
	{
		return getSigmaSquared() * (( 4 - Math.PI ) / 2);
	}
	
	/**
	 * Only valid for x >= 0 but performs no checks
	 * @param x the value
	 * @return the value of the Rayleigh pdf at x
	 */
	public double pdfAt( double x )
	{
		double sigma2 = getSigmaSquared();		
		return (x / sigma2) * Math.exp( -(x * x) / (2 * sigma2) );
	}
	
	/**
	 * Computes the max-likelihood estimate of the scale parameter sigma
	 * 
	 * @param rai
	 * @return sigma
	 */
	public <T extends RealType< T >> void fit( RandomAccessibleInterval< T > rai )
	{
		sigmaSquared = 0.0;
		count = 0;

		Cursor< T > c = Views.flatIterable( rai ).cursor();
		while ( c.hasNext() )
		{
			T t = c.next();
			sigmaSquared += (t.getRealDouble() * t.getRealDouble());
			count++;
		}
		sigmaSquared = sigmaSquared / (2 * count);
	}
	
	public void reset()
	{
		count = 0;
		sigmaSquared = 0.0;
	}

	public <T extends RealType< T >> double onlineUpdateFit( RandomAccessibleInterval< T > rai )
	{
		long newCount = 0;
		double subsetSigmaSquared = 0.0;
		
		Cursor< T > c = Views.flatIterable( rai ).cursor();
		while ( c.hasNext() )
		{
			T t = c.next();
			subsetSigmaSquared += (t.getRealDouble() * t.getRealDouble());
			newCount++;
		}
		
		if( count == 0)
		{
			count = newCount;
			sigmaSquared = subsetSigmaSquared / (2 * newCount);
			return sigmaSquared;
		}
		else
		{
			double totalsumSquares = sigmaSquared * 2 * count;
			count += newCount;
			sigmaSquared = ( totalsumSquares + subsetSigmaSquared ) / ( 2 * count );
		}

		return sigmaSquared;
	}

	public static void main( String[] args )
	{
		double[] v1 = new double[]{ 2, 2, 1, 4 };
		double[] v2 = new double[]{ 3, 2, 2, 1 };

		double[] v  = new double[]{ 3, 2, 2, 1, 2, 2, 1, 4 };
		
		ArrayImg< DoubleType, DoubleArray > img1 = ArrayImgs.doubles( v1, v1.length );
		ArrayImg< DoubleType, DoubleArray > img2 = ArrayImgs.doubles( v2, v2.length );
		
		ArrayImg< DoubleType, DoubleArray > img = ArrayImgs.doubles( v, v.length );
		
		RayleighDistribution rd = new RayleighDistribution();
		rd.fit( img );
		System.out.println( rd.getSigma() );
		
		
		RayleighDistribution rdo = new RayleighDistribution();
		rdo.onlineUpdateFit( img1 );
//		rd.fit( img1 );
//		System.out.println( rd.getSigma() );
//		System.out.println( rdo.getSigma() );
		
		rdo.onlineUpdateFit( img2 );
		System.out.println( rdo.getSigma() );
	}

}
