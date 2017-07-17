package transforms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import ij.IJ;
import io.AffineImglib2IO;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import mpicbg.models.AffineModel3D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.SubsampleIntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.GenericComposite;

public class AffineFromDisplacement
{
	public static final String FLAG_SKIP_WARP  = "--skip-warp";

	public static void main( String[] args ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		int argIdx = 0;

		boolean doWarp = true;
		if( args[ argIdx ].equals( FLAG_SKIP_WARP ))
		{
			doWarp = false;
			argIdx++;
		}

		String outPath = args[ argIdx++ ];
		String filePath = args[ argIdx++ ];

		int step = Integer.parseInt( args[ argIdx++ ] );

		Img< FloatType > displacement = null;
		if( filePath.endsWith( "nii" ))
		{
			try
			{
				displacement = ImageJFunctions.convertFloat( 
						NiftiIo.readNifti( new File( filePath ) ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else
		{
			displacement = ImageJFunctions.convertFloat( IJ.openImage( filePath ));
		}

		if ( displacement == null )
		{
			System.err.println( "Failed to load displacement field" );
			return;
		}

		System.out.println(
				"DISPLACEMENT INTERVAL: " + Util.printInterval( displacement ) );

		AffineTransform3D affine = estimateAffine( displacement, step, true );
		System.out.println( affine );

		try
		{
			AffineImglib2IO.writeXfm( new File( outPath + "_affine.txt" ), affine );
		} catch ( IOException e )
		{
			e.printStackTrace();
		}

		if ( doWarp )
		{
			System.out.println( "removing affine part from warp" );
			removeAffineComponent( displacement, affine, true );
			System.out.println( "saving warp" );
			IJ.save( ImageJFunctions.wrap( displacement, "warp" ),
					outPath + "_warp.tif" );
		}

	}

	public static <T extends RealType< T >> void removeAffineComponent(
			RandomAccessibleInterval< T > displacementField, AffineTransform3D affine,
			boolean filter )
	{

		CompositeIntervalView< T, ? extends GenericComposite< T > > vectorField = Views
				.collapse( displacementField );
		Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( vectorField )
				.cursor();

		RealPoint affineResult = new RealPoint( 3 );
		while ( c.hasNext() )
		{
			GenericComposite< T > vector = c.next();

			if ( filter && 
					vector.get( 0 ).getRealDouble() == 0 && 
					vector.get( 1 ).getRealDouble() == 0 && 
					vector.get( 2 ).getRealDouble() == 0 )
			{
				continue;
			}

			affine.apply( c, affineResult );

			for ( int i = 0; i < 2; i++ )
				vector.get( i ).setReal(
						c.getDoublePosition( i ) + vector.get( i ).getRealDouble()
								- affineResult.getDoublePosition( i ) );
		}
	}

	public static <T extends RealType< T >> AffineTransform3D estimateAffine(
			RandomAccessibleInterval< T > displacementField,
			int step, boolean filter )
					throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		SubsampleIntervalView< T > dFieldSub = Views.subsample( displacementField, step );
		System.out.println( "sub INTERVAL: " + Util.printInterval( dFieldSub ));

		// IntervalView< T > physicalSpace = Views.hyperSlice( displacementField, 3, 0 ); 
		CompositeIntervalView< T, ? extends GenericComposite< T > > vectorField = Views.collapse( dFieldSub );

		Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( vectorField ).cursor();

		int i = 0;
		ArrayList<PointMatch> matches = new ArrayList<PointMatch>( (int)Intervals.numElements( vectorField ));
		while( c.hasNext())
		{
			GenericComposite< T > vector = c.next();

			if( filter &&
					vector.get( 0 ).getRealDouble() == 0  && 
					vector.get( 1 ).getRealDouble() == 0  && 
					vector.get( 2 ).getRealDouble() == 0 )
			{
				continue;
			}

			matches.add( new PointMatch( 
					new Point( new double[]{
							step * c.getDoublePosition( 0 ),
							step * c.getDoublePosition( 1 ),
							step * c.getDoublePosition( 2 ),
					}),
					new Point( new double[]{
							step * c.getDoublePosition( 0 ) + vector.get( 0 ).getRealDouble(),
							step * c.getDoublePosition( 1 ) + vector.get( 1 ).getRealDouble(),
							step * c.getDoublePosition( 2 ) + vector.get( 2 ).getRealDouble()
					})
				));
		}

		System.out.println( "fitting affine with " + matches.size() + " point matches.");

		AffineModel3D estimator = new AffineModel3D();
		estimator.fit( matches );

		AffineTransform3D affine = new AffineTransform3D();
		affine.set( estimator.getMatrix( new double[ 12 ] ));
		return affine;

	}

	public static <T extends RealType< T >> AffineTransform3D estimateAffineRaw(
			RandomAccessibleInterval< T > displacementField )
					throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		// IntervalView< T > physicalSpace = Views.hyperSlice( displacementField, 3, 0 ); 
		CompositeIntervalView< T, ? extends GenericComposite< T > > vectorField = Views.collapse( displacementField );

		int N = 0;
		Cursor< ? extends GenericComposite< T > > c = Views.flatIterable( vectorField ).cursor();
		while( c.hasNext())
		{
			c.fwd();
			N++;
		}
		c.reset();

		int i = 0;
		double[][] p = new double[ 3 ][ N ];
		double[][] q = new double[ 3 ][ N ];
		double[] w = new double[ N ];
		while( c.hasNext())
		{
			GenericComposite< T > vector = c.next();

			p[ 0 ][ i ] = c.getDoublePosition( 0 );
			p[ 1 ][ i ] = c.getDoublePosition( 1 );
			p[ 2 ][ i ] = c.getDoublePosition( 2 );

			q[ 0 ][ i ] = c.getDoublePosition( 0 ) + vector.get( 0 ).getRealDouble();
			q[ 1 ][ i ] = c.getDoublePosition( 1 ) + vector.get( 1 ).getRealDouble();
			q[ 2 ][ i ] = c.getDoublePosition( 2 ) + vector.get( 2 ).getRealDouble();
		}

		Arrays.fill( w, 1.0 );
		AffineModel3D estimator = new AffineModel3D();
		estimator.fit( p, q, w );

		AffineTransform3D affine = new AffineTransform3D();
		affine.set( estimator.getMatrix( new double[12] ));
		return affine;
	}	

}
