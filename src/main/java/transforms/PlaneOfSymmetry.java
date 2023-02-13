package transforms;

import java.io.File;
import java.io.IOException;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.DecompositionFactory;
import org.ejml.ops.CommonOps;
import org.janelia.utility.parse.ParseUtils;

import io.AffineImglib2IO;
import jitk.spline.XfmUtils;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import transforms.AffineHelper;

public class PlaneOfSymmetry
{

//	public static void main( String[] args ) throws IOException, FormatException
//	{
//		String Ffile = args[0];
//		String regMtx = args[1];
//		
//		String intervalArg = args[ 2 ];
//		FinalInterval interval= null; 
//		if( intervalArg.contains( "[" ))
//		{
//			interval = parseNiiInterval( intervalArg );
//		}
//		else
//		{
////			long[] dims = ParseUtils.parseLongArray( args[2] );
//			interval = new FinalInterval(  ParseUtils.parseLongArray( intervalArg ));
//		}
//
//		
//		String canXfmOutF = args[3];
//		run( Ffile, regMtx, canXfmOutF, interval );
//		System.out.println( "done" );
//
////		String bboxString = "[0 0 0], [765 766 303]";
////		System.out.println( Util.printInterval( parseNiiInterval( bboxString ) ));
//
//	}
//
//	/**
//	 * Parses strings of the form "[minx miny minz],[maxx maxy maxz]"
//	 * 
//	 * @param boundingBoxString
//	 * @return the interval
//	 */
//	public static FinalInterval parseNiiInterval( String boundingBoxString )
//	{
//		String[] minmax = boundingBoxString.split( "," );
//		final String captureBracketsRegexp = "[\\[\\]]";
//		final long[] min = ParseUtils.parseLongArray( minmax[ 0 ].replaceAll( captureBracketsRegexp, "" ).trim(), " " );
//		final long[] max = ParseUtils.parseLongArray( minmax[ 1 ].replaceAll( captureBracketsRegexp, "" ).trim(), " " );
//		return new FinalInterval( min, max );
//	}	
//
//	public static void toMatrix( AffineTransform xfm, double[][] mtx )
//	{
//		for( int i = 0; i < xfm.numTargetDimensions(); i++ )
//		{
//			for( int j = 0; j < xfm.numSourceDimensions(); j++ )
//			{
//				mtx[ i ][ j ] = xfm.get( i, j );
//			}
//		}
//	}
//
//	public static void toAffine( AffineTransform3D xfm, AffineTransform out )
//	{ 
//		for( int i = 0; i < xfm.numTargetDimensions(); i++ )
//			for( int j = 0; j < xfm.numSourceDimensions(); j++ )
//				out.set( xfm.get( i, j ), i, j );
//		
//		int nd = xfm.numSourceDimensions();
//		// translation
//		for( int i = 0; i < xfm.numTargetDimensions(); i++ )
//			out.set( xfm.get( i, nd ), i, nd );
//	}	
//
//	public static AffineTransform3D toAffine3d( AffineTransform xfm )
//	{ 
//		AffineTransform3D out = new AffineTransform3D();
//		for( int i = 0; i < out.numTargetDimensions(); i++ )
//			for( int j = 0; j < out.numSourceDimensions(); j++ )
//				out.set( xfm.get( i, j ), i, j );
//		
//		int nd = xfm.numSourceDimensions();
//		// translation
//		for( int i = 0; i < out.numTargetDimensions(); i++ )
//			out.set( xfm.get( i, nd ), i, nd );
//
//		return out;
//	}
//	
//	public static void run( String Ffile, String regMtx, String canXfmOutF, Interval interval ) throws IOException, FormatException
//	{
//		AffineTransform3D F = toAffine3d( AffineImglib2IO.readXfm( 3, new File( Ffile ) ));
//		AffineTransform3D Flip2Orig = ANTSLoadAffine.loadAffine( regMtx );
//
//		System.out.println( F );
//
//		// A = TF
////		AffineTransform3D A = T.copy().concatenate( F );
//		AffineTransform3D A = F.copy();
//		double[][] Adata = new double[ 4 ][ 4 ];
//		Adata[ 3 ][ 3 ] = 1.0;
//		A.toMatrix( Adata );
//		
//		// after the flip apply Flip2Orig inverse
//		// Now A is the transform that flips, 
//		A.preConcatenate( Flip2Orig.inverse() );
//
//		System.out.println( "\nAmtx: " );
//		System.out.println( XfmUtils.printArray( Adata ) );
//		System.out.println( " " );
//
//		/*
//		 * If Ax = -x, then it means that the point x goes to -x so axis of
//		 * symmetry is the eigenvector with eigenvalue = -1
//		 */
//		DenseMatrix64F Amtx = new DenseMatrix64F( Adata );
//		EigenDecomposition< DenseMatrix64F > eig = DecompositionFactory.eig( 
//				4, true, false );
//		eig.decompose( Amtx );
//
//
//		System.out.println( "evalues: " );
//		System.out.println( eig.getEigenvalue( 0 ) );
//		System.out.println( eig.getEigenvalue( 1 ) );
//		System.out.println( eig.getEigenvalue( 2 ) );
//		System.out.println( eig.getEigenvalue( 3 ) );
//		System.out.println( " " );
//
//
//		DenseMatrix64F evec_0 = eig.getEigenVector( 0 );
//		System.out.println( evec_0 );
//
//		// null - why?
//		DenseMatrix64F evec_1 = eig.getEigenVector( 1 );
//		System.out.println( evec_1 );
//		
//		// null - why?
//		DenseMatrix64F evec_2 = eig.getEigenVector( 2 );
//		System.out.println( evec_2 );
//		
//		DenseMatrix64F evec_3 = eig.getEigenVector( 3 );
//		System.out.println( evec_3 );
//		
//		
//		double[] res = new double[ 3 ];
//		
//		double[] pt = new double[]{ 80, 210, 64 };
//		DenseMatrix64F ptv = new DenseMatrix64F( 4, 1 );
//		ptv.set( 0, 0, pt[0] );
//		ptv.set( 1, 0, pt[1] );
//		ptv.set( 2, 0, pt[2] );
//		ptv.set( 3, 0, 1.0 );
//		
//		A.apply( pt, res );
//		
//		System.out.println( "pt : " + XfmUtils.printArray( pt ));
//		System.out.println( "res: " + XfmUtils.printArray( res ));
//		System.out.println( " " );
//
//		// find QR decomposition of A
//		QRDecomposition< DenseMatrix64F > qr = DecompositionFactory.qr( 4, 4 );
//		DenseMatrix64F Q = new DenseMatrix64F( 4, 4 );
//		DenseMatrix64F R = new DenseMatrix64F( 4, 4 );
//		qr.decompose( Amtx );
//		qr.getQ( Q, false );
//		qr.getR( R, false );
//		
//		System.out.println( "Q: "  );
//		System.out.println( Q );
//		System.out.println( "\nR: "  );
//		System.out.println( R );
//		
//		double[] destAxis = new double[]{ -1.0, 0.0, 0.0 };
//		AffineTransform3D ToCanonical = alignSymmetryAxis3d( evec_3.data, destAxis );
//		System.out.println( ToCanonical );
//		
//		AffineTransform3D RcanCenter = AffineHelper.centeredRotation( ToCanonical, interval );
//		System.out.println( "RcanCenter 1" );
//		System.out.println( RcanCenter );
//		AffineTransform3D flipYZ = new AffineTransform3D();
//		flipYZ.set( -1, 1, 1 );
////		flipYZ.set( -1, 2, 2 );
//		RcanCenter.preConcatenate( flipYZ );
//
//		
//		
//		FinalInterval intervalXfm = AffineHelper.transformInterval( interval, RcanCenter );
//		System.out.println( "\ntmp interval: " );
//		System.out.println( Util.printInterval( intervalXfm ));
//		System.out.println( " " );
//		
//		AffineTransform3D translate = new AffineTransform3D();
//		translate.set( intervalXfm.min( 0 ), 0, 3 );
//		translate.set( intervalXfm.min( 1 ), 1, 3 );
//		translate.set( intervalXfm.min( 2 ), 2, 3 );
//
//		AffineTransform3D total2CanXfm = RcanCenter.copy().preConcatenate( translate.inverse() );
//		System.out.println( "total2Can xfm:\n" + total2CanXfm +"\n");
//		total2CanXfm.set( 0.0, 0, 3 );
//		total2CanXfm.set( 0.0, 1, 3 );
//		total2CanXfm.set( 0.0, 2, 3 );
//		
////		FinalInterval canonicalInterval = AffineHelper.transformInterval( img, total2CanXfm );
////		System.out.println( "\nfinal interval: " );
////		System.out.println( Util.printInterval( canonicalInterval ) );
////		System.out.println( " " );
//		
//		System.out.println( "writing xfm 2 canonical" );
//		AffineImglib2IO.writeXfm( new File( canXfmOutF ), total2CanXfm );
//
//	}
//	
//	/**
//	 * Returns a transformation that moves the srcAxis to the destAxis 
//	 * 
//	 * @param srcAxis 
//	 * @param destAxis the symmetry axis after transformation
//	 * @return the rigid transform
//	 */
//	public static AffineTransform3D alignSymmetryAxis3d( double[] srcAxis, double[] destAxis )
//	{
//		DenseMatrix64F u = new DenseMatrix64F( 3, 1 );
//		u.setData( srcAxis );
//		
//		DenseMatrix64F v = new DenseMatrix64F( 3, 1 );
//		v.setData( destAxis );
//		
//		/*
//		 * From:
//		 * http://math.stackexchange.com/questions/23197/finding-a-rotation-transformation-from-two-coordinate-frames-in-3-space
//		 * 
//		 * with correction:
//		 * On that site the matrix B is listed as \sum u v^T
//		 * where it should be \sum v u^t
//		 */
//		
//		// gen B matrix
//		DenseMatrix64F B = new DenseMatrix64F( 3, 3 );
//		CommonOps.multTransB( v, u, B );
//		System.out.println( B );
//		
//		DenseMatrix64F U = new DenseMatrix64F( 3, 3 );
//		DenseMatrix64F V = new DenseMatrix64F( 3, 3 );
//		DenseMatrix64F W = new DenseMatrix64F( 3, 3 );
//		
//		SingularValueDecomposition< DenseMatrix64F > svd = DecompositionFactory.svd( 3, 3, true, true, false );
//		svd.decompose( B );
//		svd.getU( U, false );
//		svd.getV( V, false );
//		svd.getW( W );
//		
//		double d = CommonOps.det( U ) * CommonOps.det( V );
//		System.out.println( "d: " + d) ;	
//		System.out.println( U );
//		System.out.println( V );
//		System.out.println( W );
//		
//		DenseMatrix64F M = new DenseMatrix64F( 3, 3 );
//		DenseMatrix64F R = new DenseMatrix64F( 3, 3 );
//		DenseMatrix64F tmp = new DenseMatrix64F( 3, 3 );
//		M.set( 0, 0, 1.0 );
//		M.set( 1, 1, 1.0 );
//		M.set( 2, 2, d );
//		
//		CommonOps.mult( U, M, tmp );
//		CommonOps.multTransB( tmp, V, R );
//
//
//		// TEST
////		System.out.println( "\nR:" );
////		System.out.println( R );
////		
////		DenseMatrix64F res = new DenseMatrix64F( 3, 1 );
////		CommonOps.mult( R, u, res );
////		
////		System.out.println( "\nres:" );
////		System.out.println( res );
////		
//		
//		AffineTransform3D rotXfm = new AffineTransform3D();
//		for( int i = 0; i < 3; i++ )
//			for( int j = 0; j < 3; j++ )
//				rotXfm.set( R.get( i, j ), i, j );
//		
//		return rotXfm;
//	}
//	
//	/**
//	 * Average 
//	 */
//	public static <T extends RealType<T>> void symmetrize(
//			RandomAccessibleInterval<T> rai,
//			AffineGet flipXfm )
//	{
//		IntervalView< T > flipped = Views.interval(
//						Views.raster( RealViews.transform(
//								Views.interpolate( Views.extendZero( rai ),
//										new NLinearInterpolatorFactory< T >() ),
//								flipXfm ) ),
//						rai );
//		
//		RandomAccessible< Pair< T, T >> pairra = Views.pair( rai, flipped );
//		Cursor< Pair< T, T >> c = Views.interval( pairra, flipped ).cursor();
//		while( c.hasNext() )
//		{
//			Pair< T, T > p = c.next();
//			double av = p.getA().getRealDouble();
//			double bv = p.getB().getRealDouble();
//			p.getA().setReal( ( av + bv ) / 2  );
//			p.getB().setReal( ( av + bv ) / 2  );
//		}
//	}
//	
//	public static <T extends RealType<T>> void average(
//			RandomAccessibleInterval<T> dest,
//			RandomAccessibleInterval<T> im1,
//			RandomAccessibleInterval<T> im2 )
//	{
//		RandomAccess< Pair< T, T >> pairra = Views.pair( im1, im2 ).randomAccess();
//		Cursor< T > c = Views.flatIterable( dest ).cursor();
//		while( c.hasNext() )
//		{
//			T t = c.next();
//			pairra.setPosition( c );
//			Pair< T, T > p = pairra.get();
//			double av = p.getA().getRealDouble();
//			double bv = p.getB().getRealDouble();
//			t.setReal( ( av + bv ) / 2 );
//		}
//	}
//	
//	public static <T extends RealType<T>> void average(
//			RandomAccessibleInterval<T> rai,
//			AffineGet flipXfm )
//	{
//		IntervalView< T > flipped = Views.interval(
//						Views.raster( RealViews.transform(
//								Views.interpolate( Views.extendZero( rai ),
//										new NLinearInterpolatorFactory< T >() ),
//								flipXfm ) ),
//						rai );
//		
//		RandomAccessible< Pair< T, T >> pairra = Views.pair( rai, flipped );
//		Cursor< Pair< T, T >> c = Views.interval( pairra, flipped ).cursor();
//		while( c.hasNext() )
//		{
//			Pair< T, T > p = c.next();
//			double av = p.getA().getRealDouble();
//			double bv = p.getB().getRealDouble();
//			p.getA().setReal( ( av + bv ) / 2  );
//			p.getB().setReal( ( av + bv ) / 2  );
//		}
//	}
//
//	public static <T extends RealType<T>> double measureSSDsymmetry( 
//			RandomAccessibleInterval<T> rai,
//			AffineGet flipXfm )
//	{
//
//		IntervalView< T > flipped =
//		Views.interval( Views.raster( 
//				RealViews.transform(
//						Views.interpolate( Views.extendZero( rai ), new NLinearInterpolatorFactory<T>() ),
//						flipXfm )),
//				rai );
//
//		RandomAccessible< Pair< T, T >> pairra = Views.pair( rai, flipped );
//
//		// Compute the sum of squared differences across adjacent even and odd lines 
//		double ssd = 0.0;
//		Cursor< Pair< T, T >> c = Views.interval( pairra, flipped ).cursor();
//		while( c.hasNext() )
//		{
//			Pair< T, T > p = c.next();
//			double diff = p.getA().getRealDouble() - p.getB().getRealDouble();
//			ssd += ( diff * diff );
//		}
//
//		return ssd;
//	}
	
	
}
