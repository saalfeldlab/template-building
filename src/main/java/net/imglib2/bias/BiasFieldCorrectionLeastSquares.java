package net.imglib2.bias;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import ij.IJ;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

/**
 * Estimates a global linear bias correction that transforms the intensities
 * of the input image to those of the output image.  Estimated using least squares.
 * 
 * @author John Bogovic
 *
 */
public class BiasFieldCorrectionLeastSquares
{

	private transient JCommander jCommander;

	@Parameter(names = {"--input", "-i"}, description = "Input image file" )
	private String inputFilePath;

	@Parameter(names = {"--template", "-t"}, description = "Template image file" )
	private String templateFilePath;
	
	@Parameter(names = {"--output", "-o"}, description = "Output prefix" )
	private String outputPrefixPath;
	
	@Parameter(names = {"--sampling", "-s"}, description = "Type of sampling" )
	private String samplerType = "RANDOM";
	
	@Parameter(names = {"--samplerParams", "-p"}, description = "Sampler parameters" )
	private String samplerParams = "0.05";
	
	private void initCommander()
	{
		jCommander = new JCommander( this );
		jCommander.setProgramName( "input parser" ); 
	}
	
	public static BiasFieldCorrectionLeastSquares parseCommandLineArgs( final String[] args )
	{
		BiasFieldCorrectionLeastSquares ds = new BiasFieldCorrectionLeastSquares();
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
	
	@SuppressWarnings("unchecked")
	public static <T extends RealType<T>> RandomAccessibleInterval<T> loadImage( String path, T t ) throws FormatException, IOException
	{
		if( path.endsWith( "nii" ) || path.endsWith( "nii.gz" ))
		{
			return ( RandomAccessibleInterval< T > )ImageJFunctions.wrap(NiftiIo.readNifti( new File( path ) ));
		}
		else
		{
			return ( RandomAccessibleInterval< T > )ImageJFunctions.wrap( IJ.openImage( path ));
		}
		
	}
	
	public <T extends RealType<T>> double[] process( 
			RandomAccessibleInterval< T > in,
			RandomAccessibleInterval< T > template,
			String samplerType,
			String samplerParams )
	{
		PairSampler sampler = new RandomPairSampler( 0.0 );
		sampler.parse( samplerParams );
		
		double[][] samples = sampler.getPairs( in, template );
		int N = samples[0].length;
		
		DMatrixRMaj A = new DMatrixRMaj( N, 2 );
		for( int i = 0; i < N; i++ )
		{
			A.set( i, 0, samples[ 0 ][ i ] );
			A.set( i, 1, 1.0 );
		}
		DMatrixRMaj b = new DMatrixRMaj( N, 1, true, samples[1] );
		DMatrixRMaj x = new DMatrixRMaj( 2, 1 );
		CommonOps_DDRM.solve( A, b, x );
		return new double[]{ x.get( 0 ), x.get( 1 ) };
	}
	
	public void run()
	{
		RandomAccessibleInterval< FloatType > in = null;
		RandomAccessibleInterval< FloatType > template = null;
		try
		{
			in = loadImage( inputFilePath, new FloatType() );
			template = loadImage( templateFilePath, new FloatType() );
		}
		catch ( FormatException e )
		{
			e.printStackTrace();
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
		
		System.out.println( in );
		
		double[] result = process( in, template, samplerType, samplerParams );
		System.out.println( "result: " + result[0] + " " + result[1]);
		
		ArrayList<String> lines = new ArrayList<String>();
		lines.add( String.format("%f %f", result[0], result[1]));
		
		try
		{
			Files.write( 
					Paths.get( outputPrefixPath + "_params.txt" ), lines );
			
			writeBiasCorrected( in, result, new File( outputPrefixPath + "_corrected.tif" ));
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
	}

	public <T extends RealType<T>> void writeBiasCorrected(
			RandomAccessibleInterval< T > in,
			final double[] param,
			File out )
	{
		Converter<T,T> conv = new Converter<T,T>()
		{
			@Override
			public void convert( T input, T output )
			{
				output.setReal( param[ 0 ] * input.getRealDouble() + param[ 1 ] );
			}
		};
		T t = Views.flatIterable( in ).firstElement().copy();
		RandomAccessibleInterval< T > raiCorrected = Converters.convert( in, conv, t );
		IJ.save( ImageJFunctions.wrap( raiCorrected, "corrected" ), out.getAbsolutePath() );
	}
	
	public static void main( String[] args )
	{
		
		BiasFieldCorrectionLeastSquares bfg = BiasFieldCorrectionLeastSquares.parseCommandLineArgs( args );
		bfg.run();
		
		System.out.println( "done");
	}
	
	public static interface PairSampler 
	{
		public void parse( String params );
		
		public <T extends RealType<T>> double[][] getPairs( 
				RandomAccessibleInterval< T > in, 
				RandomAccessibleInterval< T > template );

	}

	/**
	 * Picks random samples where both images have intensity greater than zero.
	 */
	public static class RandomPairSampler implements PairSampler
	{
		private double fraction;
		private Point point;

		public RandomPairSampler( double fraction )
		{
			this.fraction = fraction;
		}
		
		@Override
		public void parse( String params )
		{
			this.fraction = Double.parseDouble( params );
		}
		
		public static void randomPoint( Positionable p, Interval itvl )
		{
			Random rand = new Random();
			for( int d = 0; d < itvl.numDimensions(); d++ )
			{
				p.setPosition( 
						itvl.min( d ) + rand.nextInt( (int)itvl.max( d )),
						d );
			}
		}

		@Override
		public <T extends RealType< T >> double[][] getPairs(
				RandomAccessibleInterval< T > in, RandomAccessibleInterval< T > template )
		{
			assert( in.numDimensions() == template.numDimensions() );
			assert( Arrays.equals( 
						Intervals.dimensionsAsLongArray( in ), 
						Intervals.dimensionsAsLongArray( template )));

			point = new Point( in.numDimensions() );

			RandomAccess< T > inra = in.randomAccess();
			RandomAccess< T > tra = template.randomAccess();
			
			int N = (int)Math.round( fraction * Intervals.numElements( in ));
			double[][] out = new double[ 2 ][ N ];
			int ntries = 100;
			
			for( int i = 0; i < N; i++ )
			{

				double inv = -1;
				double tv = -1;
				
				int j = 0;
				while( inv <= 0 && tv <= 0 && j < ntries )
				{
					randomPoint( point, in );

					inra.setPosition( point );
					tra.setPosition( point );

					inv = inra.get().getRealDouble();
					tv = tra.get().getRealDouble();
					j++;
				}
				
				out[ 0 ][ i ] = inv;
				out[ 1 ][ i ] = tv;
			}
			
			return out;
		}
	}
}
