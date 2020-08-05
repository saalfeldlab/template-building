package net.imglib2.realtransform;

import java.util.Arrays;
import java.util.concurrent.Callable;

import ij.ImagePlus;
import io.DfieldIoHelper;
import io.IOHelper;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImg;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.1.1-SNAPSHOT" )
public class DisplacementFieldSmoothnessMeasures implements Callable<Void>
{
	
	public enum MatrixNorms{
		FROBENIUS
	}
	
	// store values of an asymmetric matrix up to 3d
	// can also be used to store a symmetric matrix if only the lower triangular part is set
	double a11, a12, a13;
	double a21, a22, a23;
	double a31, a32, a33;

	@Option(names = {"--input", "-i"}, description = "Input transform file" )
	private String inputTransformPath;

	@Option(names = {"--field-of-view", "-f"}, description = "Field of view over which to compute", split="," )
	private long[] intervalSpec;

	@Option(names = {"--resolution"}, description = "Resolution of output.", split="," )
	private double[] resolutionSpec;

	@Option(names = {"--unit"}, description = "Units of output image physical coordinates.")
	private String unit = "pixel";

	@Option(names = {"--outputHessian"}, description = "Output file for hessian.", required = false )
	private String outputHessianPath;

	@Option(names = {"--outputHessianNorm"}, description = "Output file for matrix norm of hessian.", required = false )
	private String outputHessianNormPath;

	@Option(names = {"--outputJacobianDeterminant"}, description = "Output file for jacobian determinant.", required = false )
	private String outputJacDetPath;

	@Option(names = {"--outputJacobian"}, description = "Output file for jacobian.", required = false )
	private String outputJacPath;

	@Option(names = {"--norm", "-n"}, description = "Matrix norm to compute (default none)" )
	private String mtxNorm = MatrixNorms.FROBENIUS.name();
	
	
	protected long[] interval;

	public static void main( String[] args )
	{
		CommandLine.call( new DisplacementFieldSmoothnessMeasures(), args );
	}
	
	public Void call()
	{
		//RealTransform transform = TransformReader.read( inputTransformPath );
		//ANTSDeformationField transform = (ANTSDeformationField)TransformReader.read( inputTransformPath );

		DfieldIoHelper dfieldIo = new DfieldIoHelper();
		ANTSDeformationField transform = null;
		double[] resolution = new double[]{ 1, 1, 1 };
		try
		{
			transform = dfieldIo.readAsAntsField( inputTransformPath );
			resolution = transform.getResolution();
			unit = transform.getUnit();
			System.out.println( "resolution : " + Arrays.toString( resolution ));
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
		
		// make sure we have the interval
		if( intervalSpec != null )
			interval = intervalSpec;
		else
			interval = Intervals.dimensionsAsLongArray( transform.getImg() );

		if( resolutionSpec != null )
			resolution = resolutionSpec;


		ImagePlusImgFactory< FloatType > factory = new ImagePlusImgFactory<>( new FloatType() );

		boolean doHessian = exists( outputHessianPath ) ;
		boolean doHessianNorm = exists( outputHessianNormPath );

		boolean doJacobian = exists( outputJacPath ) ;
		boolean doJacobianDet = exists( outputJacDetPath );

		if( doHessian || doHessianNorm )
		{
			System.out.println( "allocating" );
			FinalInterval hessianResultInterval = new FinalInterval( 
					interval[0], interval[1], interval[2], 9 );
			ImagePlusImg< FloatType, ? > hessianImg = factory.create( hessianResultInterval );
			
			//run( resultImg, transform );
			System.out.println( "hessian working" );
			hessian( hessianImg, transform.getImg(), transform.getResolution());
			
			if( doHessian )
			{
				System.out.println( "Saving to : " + outputHessianPath );

				ImagePlus hessip = hessianImg.getImagePlus();
				hessip.getCalibration().pixelWidth = resolution[ 0 ]; 
				hessip.getCalibration().pixelHeight = resolution[ 1 ]; 
				hessip.getCalibration().pixelDepth = resolution[ 2 ]; 
				hessip.setDimensions( 9, (int)interval[ 2 ], 1 );
				hessip.getCalibration().setUnit( unit );

				IOHelper.write( hessip, outputHessianPath );
			}
	
			if( mtxNorm != null && !mtxNorm.isEmpty() )
			{
				System.out.println( "computing " + mtxNorm +  " norm.");
				// TODO flesh out with more options after I implement them
				
				// don't pass interval directly in case it is of length > 3
				// and permute, to deal with default imagej axis order
				ImagePlusImg< FloatType, ? > normImg = factory.create( 
						new long[]{ interval[0], interval[1], 1, interval[2] } );
				
				IntervalView< FloatType > normImgPermuted = 
						Views.hyperSlice( 
								Views.permute( normImg, 2, 3 ), 
								3, 0 );
				
				System.out.println( "Setting hessian norm resolutions to: " + Arrays.toString( resolution ) );
				ImagePlus hessNormIp = normImg.getImagePlus();
				hessNormIp.getCalibration().pixelWidth = resolution[ 0 ]; 
				hessNormIp.getCalibration().pixelHeight = resolution[ 1 ]; 
				hessNormIp.getCalibration().pixelDepth = resolution[ 2 ]; 
				hessNormIp.setDimensions( 1, (int)interval[ 2 ], 1 );
				hessNormIp.getCalibration().setUnit( unit );

				pixelwiseMatrixNorms( normImgPermuted, hessianImg, transform.getResolution() );
				System.out.println( "Saving to : " + outputHessianNormPath );
				IOHelper.write( normImg.getImagePlus(), outputHessianNormPath );
			}
		}
		
		if( doJacobian )
		{
			FinalInterval jacobianResultInterval = new FinalInterval( interval[0], interval[1], interval[2], 9 );
			ImagePlusImg< FloatType, ? > jacImg = factory.create( jacobianResultInterval );

			System.out.println( "Computing jacobian central." );
			jacobianCentral( jacImg, transform.getImg(), transform.getResolution() );

			System.out.println( "Saving to : " + outputJacPath );
			ImagePlus jacip = jacImg.getImagePlus();

			jacip.getCalibration().pixelWidth = resolution[ 0 ]; 
			jacip.getCalibration().pixelHeight = resolution[ 1 ]; 
			jacip.getCalibration().pixelDepth = resolution[ 2 ]; 
			jacip.setDimensions( 9, (int)interval[ 2 ], 1 );
			jacip.getCalibration().setUnit( unit );

			IOHelper.write( jacip, outputJacPath );
		}

		if( doJacobianDet )
		{
			FinalInterval jacobianDetResultInterval = new FinalInterval( interval[0], interval[1], interval[2] );
			ImagePlusImg< FloatType, ? > jacDetImg = factory.create( jacobianDetResultInterval );
			
			System.out.println( "Computing jacobian determinant central." );
			jacobianDeterminantCentral( jacDetImg, transform.getImg(), transform.getResolution() );

//			System.out.println( "Computing jacobian determinant forward." );
//			jacobianDeterminantForward( jacDetImg, transform.getImg(), transform.getResolution() );

			ImagePlus jacDetIp = jacDetImg.getImagePlus();
			jacDetIp.getCalibration().pixelWidth = resolution[ 0 ]; 
			jacDetIp.getCalibration().pixelHeight = resolution[ 1 ]; 
			jacDetIp.getCalibration().pixelDepth = resolution[ 2 ]; 
			jacDetIp.setDimensions( 1, (int)interval[ 2 ], 1 );
			jacDetIp.getCalibration().setUnit( unit );

			System.out.println( "Saving to : " + outputJacDetPath );
			IOHelper.write( jacDetImg.getImagePlus(), outputJacDetPath );
		}
	
		return null;
	}
	
	public static boolean exists( String path )
	{
		return path != null && !path.isEmpty();
	}
	
	public static <T extends RealType<T>, S extends RealType<S>> void pixelwiseMatrixNorms( 
			final RandomAccessibleInterval<T> result,
			final RandomAccessibleInterval<S> hessian,
			final double[] resolutions )
	{
		Cursor<T> c = Views.flatIterable( result ).cursor();
		RandomAccess< S > ra = hessian.randomAccess();

		
		double[] flatMtx = new double[ (int)hessian.dimension( 3 ) ];
		while( c.hasNext() )
		{
			c.fwd();

			for( int d = 0; d < c.numDimensions(); d++)
				ra.setPosition( c.getIntPosition( d ), d );
			
			ra.setPosition( 0, c.numDimensions() );
			for( int i = 0; i < c.numDimensions(); i++)
			{
				flatMtx[ i ] = ra.get().getRealDouble();
				ra.fwd( 3 );
				i++;
			}
	
			c.get().setReal( frobeniusNorm( flatMtx ));
			
		}
	}
	
	public static double frobeniusNorm(
			final double[] flatMtx )
	{
		double l2norm = 0;
		for( int i = 0; i < flatMtx.length; i++ )
			l2norm += (flatMtx[ i ] * flatMtx[ i ]);
			
		return Math.sqrt( l2norm );
	}

	public static double l1MatrixNorm(
			final double[] flatMtx )
	{
		double l2norm = 0;
		for( int i = 0; i < flatMtx.length; i++ )
			l2norm += (flatMtx[ i ] * flatMtx[ i ]);
			
		return Math.sqrt( l2norm );
	}

	public static <T extends RealType<T>, S extends RealType<S>> void jacobianDeterminantForward(
			final RandomAccessibleInterval<T> result,
			final RandomAccessibleInterval<S> deformationFieldIn,
			final double[] resolutions )
	{
		
		RandomAccessibleInterval< S > deformationField = 
				Views.interval(
					Views.extendBorder( deformationFieldIn ),
				deformationFieldIn );
		
		RandomAccess< S > dra = deformationField.randomAccess();
		int[] pos = new int[]{ 1274, 9, 0, 0};
		dra.setPosition( pos );

		System.out.println( "p: " + Util.printCoordinates( dra ));
		System.out.println( "v: " + dra.get().getRealDouble());

		pos = new int[]{ 1274, 9, -1, 0 };
		dra.setPosition( pos );

		System.out.println( "p: " + Util.printCoordinates( dra ));
		System.out.println( "v: " + dra.get().getRealDouble());
	
		//RandomAccess< T > resultRa = result.randomAccess();

		Cursor< T > resultC = Views.flatIterable( result ).cursor();

		IntervalView< S > dx = Views.hyperSlice( deformationField, 3, 0 );
		IntervalView< S > dy = Views.hyperSlice( deformationField, 3, 1 );
		IntervalView< S > dz = Views.hyperSlice( deformationField, 3, 2 );

//		MixedTransformView< S > dx = Views.hyperSlice( deformationField, 3, 0 );
//		MixedTransformView< S > dy = Views.hyperSlice( deformationField, 3, 1 );
//		MixedTransformView< S > dz = Views.hyperSlice( deformationField, 3, 2 );
	

		// dx
		final Cursor< S > dxc = Views.flatIterable( dx ).cursor();
		final Cursor< S > dxbx1 = translated( dx, -1, 0 ).cursor();
		final Cursor< S > dxby1 = translated( dx, -1, 1 ).cursor();
		final Cursor< S > dxbz1 = translated( dx, -1, 2 ).cursor();

		// dy
		final Cursor< S > dyc = Views.flatIterable( dy ).cursor();
		final Cursor< S > dybx1 = translated( dy, -1, 0 ).cursor();
		final Cursor< S > dyby1 = translated( dy, -1, 1 ).cursor();
		final Cursor< S > dybz1 = translated( dy, -1, 2 ).cursor();

		// dz
		final Cursor< S > dzc = Views.flatIterable( dz ).cursor();
		final Cursor< S > dzbx1 = translated( dz, -1, 0 ).cursor();
		final Cursor< S > dzby1 = translated( dz, -1, 1 ).cursor();
		final Cursor< S > dzbz1 = translated( dz, -1, 2 ).cursor();


		boolean debug = false;
		System.out.println( "resolutions: " + Arrays.toString( resolutions ));

		while( resultC.hasNext() )
		{
			resultC.fwd();
			
			// 150, 250, 180
//			if( resultC.getIntPosition( 0 ) == 150 && 
//				resultC.getIntPosition( 1 ) == 250 && 
//				resultC.getIntPosition( 2 ) == 180 )
//			{
//				debug = true;
//			}

			dxc.fwd(); dxbx1.fwd(); dxby1.fwd(); dxbz1.fwd();
			double dxdx = derivForward( dxbx1, dxc, resolutions[ 0 ], debug );
			double dxdy = derivForward( dxby1, dxc, resolutions[ 1 ], debug );
			double dxdz = derivForward( dxbz1, dxc, resolutions[ 2 ], debug );
			
			dyc.fwd(); dybx1.fwd(); dyby1.fwd(); dybz1.fwd();
			double dydx = derivForward( dybx1, dyc, resolutions[ 0 ], debug );
			double dydy = derivForward( dyby1, dyc, resolutions[ 1 ], debug );
			double dydz = derivForward( dybz1, dyc, resolutions[ 2 ], debug );

			dzc.fwd(); dzbx1.fwd(); dzby1.fwd(); dzbz1.fwd();
			double dzdx = derivForward( dzbx1, dzc, resolutions[ 0 ], debug );
			double dzdy = derivForward( dzby1, dzc, resolutions[ 1 ], debug );
			double dzdz = derivForward( dzbz1, dzc, resolutions[ 2 ], debug );
			
//			System.out.println( "dxc  " + dxc.get().getRealDouble() );
//			System.out.println( "dxbx " + dxbx1.get().getRealDouble() );
//			System.out.println( "dxby " + dxby1.get().getRealDouble() );
//			System.out.println( "dxbz " + dxbz1.get().getRealDouble() );
			
			double jacobianDeterminant = jacobianDeterminant( 
					1 + dxdx, dxdy, dxdz, 
					dydx, 1 + dydy, dydz, 
					dzdx, dzdy,  1 + dzdz );

//			double jacobianDeterminant = jacobianDeterminant( 
//					1 + dxdx, dydx, dzdx, 
//					dxdy, 1 + dydy, dzdy, 
//					dxdz, dydz,  1 + dzdz );

	
//			if( debug || jacobianDeterminant < -10 || jacobianDeterminant > 20 )
			if( debug )
			{
				
				
				double vxc = dxc.get().getRealDouble();
				double vxxb = dxbx1.get().getRealDouble();
				double vxyb = dxby1.get().getRealDouble();
				double vxzb = dxbz1.get().getRealDouble();
				System.out.println( "vxc  " + vxc );
				System.out.println( "vxxb " + vxxb );
				System.out.println( "vxyb " + vxyb );
				System.out.println( "vxzb " + vxzb );

//				double vyxb = dybx1.get().getRealDouble();
//				double vyyb = dyby1.get().getRealDouble();
//				double vyzb = dybz1.get().getRealDouble();
//				System.out.println( "vyxb " + vyxb );
//				System.out.println( "vyyb " + vyyb );
//				System.out.println( "vyzb " + vyzb );
//
//				double vzxb = dzbx1.get().getRealDouble();
//				double vzyb = dzby1.get().getRealDouble();
//				double vzzb = dzbz1.get().getRealDouble();
//				System.out.println( "vzxb " + vzxb );
//				System.out.println( "vzyb " + vzyb );
//				System.out.println( "vzzb " + vzzb );
				
				System.out.println( "dxdx : " + dxdx );
				System.out.println( "dxdy : " + dxdy );
				System.out.println( "dxdz : " + dxdz );

				System.out.println( "dydx : " + dydx );
				System.out.println( "dydy : " + dydy );
				System.out.println( "dydz : " + dydz );

				System.out.println( "dzdx : " + dzdx );
				System.out.println( "dzdy : " + dzdy );
				System.out.println( "dzdz : " + dzdz );

				System.out.println( "dxbx1 pos: " + Util.printCoordinates( dxbx1 ));
				System.out.println( "result pos: " + Util.printCoordinates( resultC ));
				System.out.println( "jac: " + jacobianDeterminant );
				System.out.println( " " );
			}

			resultC.get().setReal( jacobianDeterminant );
		}

	}

	public static <T extends RealType<T>, S extends RealType<S>> void jacobianCentral(
			final RandomAccessibleInterval<T> result,
			final RandomAccessibleInterval<S> deformationFieldIn,
			final double[] resolutions )
	{
		
		RandomAccessibleInterval< S > deformationField = Views.interval( Views.extendMirrorDouble( deformationFieldIn ),
				deformationFieldIn );
	
		//RandomAccess< T > resultRa = result.randomAccess();

		RandomAccess< T > resultRa = result.randomAccess();

		IntervalView< S > dx = Views.hyperSlice( deformationField, 3, 0 );
		IntervalView< S > dy = Views.hyperSlice( deformationField, 3, 1 );
		IntervalView< S > dz = Views.hyperSlice( deformationField, 3, 2 );

	

		final Cursor< S > center = dx.cursor();
		// dx
		final Cursor< S > dxfx1 = translated( dx,  1, 0 ).cursor();
		final Cursor< S > dxbx1 = translated( dx, -1, 0 ).cursor();
		final Cursor< S > dxfy1 = translated( dx,  1, 1 ).cursor();
		final Cursor< S > dxby1 = translated( dx, -1, 1 ).cursor();
		final Cursor< S > dxfz1 = translated( dx,  1, 2 ).cursor();
		final Cursor< S > dxbz1 = translated( dx, -1, 2 ).cursor();

		// dy
		final Cursor< S > dyfx1 = translated( dy,  1, 0 ).cursor();
		final Cursor< S > dybx1 = translated( dy, -1, 0 ).cursor();
		final Cursor< S > dyfy1 = translated( dy,  1, 1 ).cursor();
		final Cursor< S > dyby1 = translated( dy, -1, 1 ).cursor();
		final Cursor< S > dyfz1 = translated( dy,  1, 2 ).cursor();
		final Cursor< S > dybz1 = translated( dy, -1, 2 ).cursor();

		// dz
		final Cursor< S > dzfx1 = translated( dz,  1, 0 ).cursor();
		final Cursor< S > dzbx1 = translated( dz, -1, 0 ).cursor();
		final Cursor< S > dzfy1 = translated( dz,  1, 1 ).cursor();
		final Cursor< S > dzby1 = translated( dz, -1, 1 ).cursor();
		final Cursor< S > dzfz1 = translated( dz,  1, 2 ).cursor();
		final Cursor< S > dzbz1 = translated( dz, -1, 2 ).cursor();


		boolean debug = false;
		System.out.println( "resolutions: " + Arrays.toString( resolutions ));

		while( center.hasNext() )
		{
			center.fwd();
	
			// 150, 250, 180
//			if( resultC.getIntPosition( 0 ) == 150 && 
//				resultC.getIntPosition( 1 ) == 250 && 
//				resultC.getIntPosition( 2 ) == 180 )
//			{
//				debug = true;
//			}

			dxbx1.fwd(); dxfx1.fwd();
			dxby1.fwd(); dxfy1.fwd();
			dxbz1.fwd(); dxfz1.fwd();
			double dxdx = derivCentral( dxbx1, dxfx1, resolutions[ 0 ], debug );
			double dxdy = derivCentral( dxby1, dxfy1, resolutions[ 1 ], debug );
			double dxdz = derivCentral( dxbz1, dxfz1, resolutions[ 2 ], debug );
			
			dybx1.fwd(); dyfx1.fwd();
			dyby1.fwd(); dyfy1.fwd();
			dybz1.fwd(); dyfz1.fwd();
			double dydx = derivCentral( dybx1, dyfx1, resolutions[ 0 ], debug );
			double dydy = derivCentral( dyby1, dyfy1, resolutions[ 1 ], debug );
			double dydz = derivCentral( dybz1, dyfz1, resolutions[ 2 ], debug );

			dzbx1.fwd(); dzfx1.fwd();
			dzby1.fwd(); dzfy1.fwd();
			dzbz1.fwd(); dzfz1.fwd();
			double dzdx = derivCentral( dzbx1, dzfx1, resolutions[ 0 ], debug );
			double dzdy = derivCentral( dzby1, dzfy1, resolutions[ 1 ], debug );
			double dzdz = derivCentral( dzbz1, dzfz1, resolutions[ 2 ], debug );
			
			resultRa.setPosition( center.getIntPosition( 0 ), 0 );
			resultRa.setPosition( center.getIntPosition( 1 ), 1 );
			resultRa.setPosition( center.getIntPosition( 2 ), 2 );

			int i = 0;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( dxdx ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( dxdy ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( dxdz ); i++;

			resultRa.setPosition( i, 3 ); resultRa.get().setReal( dydx ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( dydy ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( dydz ); i++;

			resultRa.setPosition( i, 3 ); resultRa.get().setReal( dzdx ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( dzdy ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( dzdz ); i++;

		}
	}

	public static <T extends RealType<T>, S extends RealType<S>> void jacobianDeterminantCentral(
			final RandomAccessibleInterval<T> result,
			final RandomAccessibleInterval<S> deformationFieldIn,
			final double[] resolutions )
	{
		
		RandomAccessibleInterval< S > deformationField = Views.interval( Views.extendMirrorDouble( deformationFieldIn ),
				deformationFieldIn );
	
		//RandomAccess< T > resultRa = result.randomAccess();

		Cursor< T > resultC = Views.flatIterable( result ).cursor();

		IntervalView< S > dx = Views.hyperSlice( deformationField, 3, 0 );
		IntervalView< S > dy = Views.hyperSlice( deformationField, 3, 1 );
		IntervalView< S > dz = Views.hyperSlice( deformationField, 3, 2 );
	

		// dx
		final Cursor< S > dxfx1 = translated( dx,  1, 0 ).cursor();
		final Cursor< S > dxbx1 = translated( dx, -1, 0 ).cursor();
		final Cursor< S > dxfy1 = translated( dx,  1, 1 ).cursor();
		final Cursor< S > dxby1 = translated( dx, -1, 1 ).cursor();
		final Cursor< S > dxfz1 = translated( dx,  1, 2 ).cursor();
		final Cursor< S > dxbz1 = translated( dx, -1, 2 ).cursor();

		// dy
		final Cursor< S > dyfx1 = translated( dy,  1, 0 ).cursor();
		final Cursor< S > dybx1 = translated( dy, -1, 0 ).cursor();
		final Cursor< S > dyfy1 = translated( dy,  1, 1 ).cursor();
		final Cursor< S > dyby1 = translated( dy, -1, 1 ).cursor();
		final Cursor< S > dyfz1 = translated( dy,  1, 2 ).cursor();
		final Cursor< S > dybz1 = translated( dy, -1, 2 ).cursor();

		// dz
		final Cursor< S > dzfx1 = translated( dz,  1, 0 ).cursor();
		final Cursor< S > dzbx1 = translated( dz, -1, 0 ).cursor();
		final Cursor< S > dzfy1 = translated( dz,  1, 1 ).cursor();
		final Cursor< S > dzby1 = translated( dz, -1, 1 ).cursor();
		final Cursor< S > dzfz1 = translated( dz,  1, 2 ).cursor();
		final Cursor< S > dzbz1 = translated( dz, -1, 2 ).cursor();


		boolean debug = false;
		System.out.println( "resolutions: " + Arrays.toString( resolutions ));

		while( resultC.hasNext() )
		{
			resultC.fwd();
			
			// 150, 250, 180
//			if( resultC.getIntPosition( 0 ) == 150 && 
//				resultC.getIntPosition( 1 ) == 250 && 
//				resultC.getIntPosition( 2 ) == 180 )
//			{
//				debug = true;
//			}

			dxbx1.fwd(); dxfx1.fwd();
			dxby1.fwd(); dxfy1.fwd();
			dxbz1.fwd(); dxfz1.fwd();
			double dxdx = derivCentral( dxbx1, dxfx1, resolutions[ 0 ], debug );
			double dxdy = derivCentral( dxby1, dxfy1, resolutions[ 1 ], debug );
			double dxdz = derivCentral( dxbz1, dxfz1, resolutions[ 2 ], debug );
			
			dybx1.fwd(); dyfx1.fwd();
			dyby1.fwd(); dyfy1.fwd();
			dybz1.fwd(); dyfz1.fwd();
			double dydx = derivCentral( dybx1, dyfx1, resolutions[ 0 ], debug );
			double dydy = derivCentral( dyby1, dyfy1, resolutions[ 1 ], debug );
			double dydz = derivCentral( dybz1, dyfz1, resolutions[ 2 ], debug );

			dzbx1.fwd(); dzfx1.fwd();
			dzby1.fwd(); dzfy1.fwd();
			dzbz1.fwd(); dzfz1.fwd();
			double dzdx = derivCentral( dzbx1, dzfx1, resolutions[ 0 ], debug );
			double dzdy = derivCentral( dzby1, dzfy1, resolutions[ 1 ], debug );
			double dzdz = derivCentral( dzbz1, dzfz1, resolutions[ 2 ], debug );
			
//			double jacobianDeterminant = jacobianDeterminant( 
//					1 + dxdx, dxdy, dxdz, 
//					dydx, 1 + dydy, dydz, 
//					dzdx, dzdy,  1 + dzdz );

			double jacobianDeterminant = jacobianDeterminant( 
					1 + dxdx, dydx, dzdx, 
					dxdy, 1 + dydy, dzdy, 
					dxdz, dydz,  1 + dzdz );

			
			if( debug )
			{
				System.out.println( "dxdx : " + dxdx );
				System.out.println( "dxdy : " + dxdy );
				System.out.println( "dxdz : " + dxdz );

				System.out.println( "dydx : " + dydx );
				System.out.println( "dydy : " + dydy );
				System.out.println( "dydz : " + dydz );

				System.out.println( "dzdx : " + dzdx );
				System.out.println( "dzdy : " + dzdy );
				System.out.println( "dzdz : " + dzdz );

				System.out.println( "dxbx1 pos: " + Util.printCoordinates( dxbx1 ));
				System.out.println( "result pos: " + Util.printCoordinates( resultC ));
				System.out.println( "dxfx1 pos: " + Util.printCoordinates( dxfx1 ));
				System.out.println( "jac: " + jacobianDeterminant );
				System.out.println( " " );

			}

			

			resultC.get().setReal( jacobianDeterminant );
		}

	}

	public static <T extends RealType<T>, S extends RealType<S>> void hessian( 
			final RandomAccessibleInterval<T> result,
			final RandomAccessibleInterval<S> deformationFieldIn,
			final double[] resolutions )
	{
		RandomAccess< T > resultRa = result.randomAccess();
		
		IntervalView< S > deformationField = Views.interval( Views.extendBorder( deformationFieldIn ), deformationFieldIn );
		IntervalView< S > dx = Views.hyperSlice( deformationField, 3, 0 );
		IntervalView< S > dy = Views.hyperSlice( deformationField, 3, 1 );
		IntervalView< S > dz = Views.hyperSlice( deformationField, 3, 2 );
		
		Cursor<S> dxCenter = Views.flatIterable( dx ).cursor();
		Cursor<S> dyCenter = Views.flatIterable( dy ).cursor();
		Cursor<S> dzCenter = Views.flatIterable( dz ).cursor();
		
		// dx
		final Cursor< S > dxfx1 = translated( dx,  1, 0 ).cursor();
		final Cursor< S > dxbx1 = translated( dx, -1, 0 ).cursor();
		final Cursor< S > dxfy1 = translated( dx,  1, 1 ).cursor();
		final Cursor< S > dxby1 = translated( dx, -1, 1 ).cursor();
		final Cursor< S > dxfz1 = translated( dx,  1, 2 ).cursor();
		final Cursor< S > dxbz1 = translated( dx, -1, 2 ).cursor();

		// dy
		final Cursor< S > dyfx1 = translated( dy,  1, 0 ).cursor();
		final Cursor< S > dybx1 = translated( dy, -1, 0 ).cursor();
		final Cursor< S > dyfy1 = translated( dy,  1, 1 ).cursor();
		final Cursor< S > dyby1 = translated( dy, -1, 1 ).cursor();
		final Cursor< S > dyfz1 = translated( dy,  1, 2 ).cursor();
		final Cursor< S > dybz1 = translated( dy, -1, 2 ).cursor();

		// dz
		final Cursor< S > dzfx1 = translated( dz,  1, 0 ).cursor();
		final Cursor< S > dzbx1 = translated( dz, -1, 0 ).cursor();
		final Cursor< S > dzfy1 = translated( dz,  1, 1 ).cursor();
		final Cursor< S > dzby1 = translated( dz, -1, 1 ).cursor();
		final Cursor< S > dzfz1 = translated( dz,  1, 2 ).cursor();
		final Cursor< S > dzbz1 = translated( dz, -1, 2 ).cursor();


		System.out.println( "resolutions: " + Arrays.toString( resolutions ));

//		boolean debug = false;

		while( dxCenter.hasNext() )
		{
			dxCenter.fwd();
			dxbx1.fwd(); dxfx1.fwd();
			dxby1.fwd(); dxfy1.fwd();
			dxbz1.fwd(); dxfz1.fwd();
			
//			if( dxCenter.getIntPosition( 0 ) == 196 && 
//				dxCenter.getIntPosition( 1 ) == 140 && 
//				dxCenter.getIntPosition( 2 ) == 93 )
//			{
//				System.out.println( Util.printCoordinates( dxCenter ));
//				debug = true;
//			}
			
			double d2xdx2 = secondDeriv( dxbx1, dxCenter, dxfx1, resolutions[ 0 ]);
			double d2xdy2 = secondDeriv( dxby1, dxCenter, dxfy1, resolutions[ 1 ]);
			double d2xdz2 = secondDeriv( dxbz1, dxCenter, dxfz1, resolutions[ 2 ]);
			
			dyCenter.fwd();
			dybx1.fwd(); dyfx1.fwd();
			dyby1.fwd(); dyfy1.fwd();
			dybz1.fwd(); dyfz1.fwd();
			double d2ydx2 = secondDeriv( dybx1, dyCenter, dyfx1, resolutions[ 0 ] );
			double d2ydy2 = secondDeriv( dyby1, dyCenter, dyfy1, resolutions[ 1 ]);
			double d2ydz2 = secondDeriv( dybz1, dyCenter, dyfz1, resolutions[ 2 ] );

			dzCenter.fwd();
			dzbx1.fwd(); dzfx1.fwd();
			dzby1.fwd(); dzfy1.fwd();
			dzbz1.fwd(); dzfz1.fwd();
			double d2zdx2 = secondDeriv( dzbx1, dzCenter, dzfx1, resolutions[ 0 ] );
			double d2zdy2 = secondDeriv( dzby1, dzCenter, dzfy1, resolutions[ 1 ] );
			double d2zdz2 = secondDeriv( dzbz1, dzCenter, dzfz1, resolutions[ 2 ] );
			
			
			resultRa.setPosition( dxCenter.getIntPosition( 0 ), 0 );
			resultRa.setPosition( dxCenter.getIntPosition( 1 ), 1 );
			resultRa.setPosition( dxCenter.getIntPosition( 2 ), 2 );
			
			int i = 0;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( d2xdx2 ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( d2xdy2 ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( d2xdz2 ); i++;

			resultRa.setPosition( i, 3 ); resultRa.get().setReal( d2ydx2 ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( d2ydy2 ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( d2ydz2 ); i++;

			resultRa.setPosition( i, 3 ); resultRa.get().setReal( d2zdx2 ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( d2zdy2 ); i++;
			resultRa.setPosition( i, 3 ); resultRa.get().setReal( d2zdz2 ); i++;
		}

	}

	public static <T extends RealType<T>> IterableInterval< T > translated( 
			final RandomAccessibleInterval<T> in,
			final int amount, final int dimension )
	{
		return Views.flatIterable( Views.interval( in, Intervals.translate( in, amount, dimension )));
		
	}

	public static <T extends RealType<T>, S extends RealType<S>> void secondDerivativesOverkill( 
			final RandomAccessibleInterval<T> result,
			final RandomAccessibleInterval<S> deformationField,
			final double[] resolutions )
	{
		IntervalView< S > dx = Views.hyperSlice( deformationField, 3, 0 );
		IntervalView< S > dy = Views.hyperSlice( deformationField, 3, 1 );
		IntervalView< S > dz = Views.hyperSlice( deformationField, 3, 2 );
		
		Cursor<S> dxCenter = Views.flatIterable( dx ).cursor();
		Cursor<S> dyCenter = Views.flatIterable( dy ).cursor();
		Cursor<S> dzCenter = Views.flatIterable( dz ).cursor();
		
		// dx
		Cursor< S > dxbx1 = Views.flatIterable( Views.translate( dx, -1, 0, 0 ) ).cursor();
		Cursor< S > dxfx1 = Views.flatIterable( Views.translate( dx,  1, 0, 0 )).cursor(); 
		Cursor< S > dxbx2 = Views.flatIterable( Views.translate( dx, -2, 0, 0 )).cursor(); 
		Cursor< S > dxfx2 = Views.flatIterable( Views.translate( dx,  2, 0, 0 )).cursor(); 

		Cursor< S > dxby1 = Views.flatIterable( Views.translate( dx, 0, -1, 0 )).cursor(); 
		Cursor< S > dxfy1 = Views.flatIterable( Views.translate( dx, 0,  1, 0 )).cursor(); 
		Cursor< S > dxby2 = Views.flatIterable( Views.translate( dx, 0, -2, 0 )).cursor(); 
		Cursor< S > dxfy2 = Views.flatIterable( Views.translate( dx, 0,  2, 0 )).cursor(); 

		Cursor< S > dxbz1 = Views.flatIterable( Views.translate( dx, 0, 0, -1 )).cursor(); 
		Cursor< S > dxfz1 = Views.flatIterable( Views.translate( dx, 0, 0,  1 )).cursor(); 
		Cursor< S > dxbz2 = Views.flatIterable( Views.translate( dx, 0, 0, -2 )).cursor(); 
		Cursor< S > dxfz2 = Views.flatIterable( Views.translate( dx, 0, 0,  2 )).cursor(); 

		// dy 
		Cursor< S > dybx1 = Views.flatIterable( Views.translate( dy, -1, 0, 0 )).cursor(); 
		Cursor< S > dyfx1 = Views.flatIterable( Views.translate( dy,  1, 0, 0 )).cursor(); 
		Cursor< S > dybx2 = Views.flatIterable( Views.translate( dy, -2, 0, 0 )).cursor(); 
		Cursor< S > dyfx2 = Views.flatIterable( Views.translate( dy,  2, 0, 0 )).cursor(); 

		Cursor< S > dyby1 = Views.flatIterable( Views.translate( dy, 0, -1, 0 )).cursor(); 
		Cursor< S > dyfy1 = Views.flatIterable( Views.translate( dy, 0,  1, 0 )).cursor(); 
		Cursor< S > dyby2 = Views.flatIterable( Views.translate( dy, 0, -2, 0 )).cursor(); 
		Cursor< S > dyfy2 = Views.flatIterable( Views.translate( dy, 0,  2, 0 )).cursor(); 

		Cursor< S > dybz1 = Views.flatIterable( Views.translate( dy, 0, 0, -1 )).cursor(); 
		Cursor< S > dyfz1 = Views.flatIterable( Views.translate( dy, 0, 0,  1 )).cursor(); 
		Cursor< S > dybz2 = Views.flatIterable( Views.translate( dy, 0, 0, -2 )).cursor(); 
		Cursor< S > dyfz2 = Views.flatIterable( Views.translate( dy, 0, 0,  2 )).cursor(); 

		// dz 
		Cursor< S > dzbx1 = Views.flatIterable( Views.translate( dz, -1, 0, 0 )).cursor(); 
		Cursor< S > dzfx1 = Views.flatIterable( Views.translate( dz,  1, 0, 0 )).cursor(); 
		Cursor< S > dzbx2 = Views.flatIterable( Views.translate( dz, -2, 0, 0 )).cursor(); 
		Cursor< S > dzfx2 = Views.flatIterable( Views.translate( dz,  2, 0, 0 )).cursor(); 

		Cursor< S > dzby1 = Views.flatIterable( Views.translate( dz, 0, -1, 0 )).cursor(); 
		Cursor< S > dzfy1 = Views.flatIterable( Views.translate( dz, 0,  1, 0 )).cursor(); 
		Cursor< S > dzby2 = Views.flatIterable( Views.translate( dz, 0, -2, 0 )).cursor(); 
		Cursor< S > dzfy2 = Views.flatIterable( Views.translate( dz, 0,  2, 0 )).cursor(); 

		Cursor< S > dzbz1 = Views.flatIterable( Views.translate( dz, 0, 0, -1 )).cursor(); 
		Cursor< S > dzfz1 = Views.flatIterable( Views.translate( dz, 0, 0,  1 )).cursor(); 
		Cursor< S > dzbz2 = Views.flatIterable( Views.translate( dz, 0, 0, -2 )).cursor(); 
		Cursor< S > dzfz2 = Views.flatIterable( Views.translate( dz, 0, 0,  2 )).cursor(); 


		while( dxbx1.hasNext() )
		{
			dxCenter.fwd();
			dxbx1.fwd(); dxfx1.fwd(); dxbx2.fwd(); dxfx2.fwd();
			dxby1.fwd(); dxfy1.fwd(); dxby2.fwd(); dxfy2.fwd();
			dxbz1.fwd(); dxfz1.fwd(); dxbz2.fwd(); dxfz2.fwd();

			dyCenter.fwd();
			dybx1.fwd(); dyfx1.fwd(); dybx2.fwd(); dyfx2.fwd();
			dyby1.fwd(); dyfy1.fwd(); dyby2.fwd(); dyfy2.fwd();
			dybz1.fwd(); dyfz1.fwd(); dybz2.fwd(); dyfz2.fwd();

			dzCenter.fwd();
			dzbx1.fwd(); dzfx1.fwd(); dzbx2.fwd(); dzfx2.fwd();
			dzby1.fwd(); dzfy1.fwd(); dzby2.fwd(); dzfy2.fwd();
			dzbz1.fwd(); dzfz1.fwd(); dzbz2.fwd(); dzfz2.fwd();
		}

	}
	
	public static final <T extends RealType<T>> double derivForward(
			final Cursor<T> b1,
			final Cursor<T> c,
			final double h,
			final boolean debug )
	{
		double vb1 = b1.get().getRealDouble();
		double vc = c.get().getRealDouble();
	
		if( debug )
		{
			System.out.println( "  vb1 = " + vb1 );
			System.out.println( "  vf1 = " + vc );
		}

		return ( vc - vb1 ) / h;
	}
	
	public static final <T extends RealType<T>> double derivCentral(
			final Cursor<T> b1,
			final Cursor<T> f1,
			final double h,
			final boolean debug )
	{
		double vb1 = b1.get().getRealDouble();
		double vf1 = f1.get().getRealDouble();
	
		if( debug )
		{
			System.out.println( "  vb1 = " + vb1 );
			System.out.println( "  vf1 = " + vf1 );
		}

		return ( vf1 - vb1 ) / ( 2 * h );
	}

	public static final <T extends RealType<T>> double secondDeriv(
			final Cursor<T> b1,
			final Cursor<T> center,
			final Cursor<T> f1,
			final double h )
	{
		double vb1 = b1.get().getRealDouble();
		double vc = center.get().getRealDouble();
		double vf1 = f1.get().getRealDouble();
	
//		if( debug )
//		{
//			System.out.println( "vb1 = " + vb1 );
//			System.out.println( "vc  = " + vc );
//			System.out.println( "vf1 = " + vf1 );
//		}

		return (vf1 - 2*vc + vb1) / (h*h);
	}

	public static final <T extends RealType<T>> double secondDerivOverkill(
			final Cursor<T> b2,
			final Cursor<T> b1,
			final Cursor<T> center,
			final Cursor<T> f1,
			final Cursor<T> f2,
			final double h)
	{
		double vb2 = b2.get().getRealDouble();
		double vb1 = b1.get().getRealDouble();
		double vc = center.get().getRealDouble();
		double vf1 = f1.get().getRealDouble();
		double vf2 = f2.get().getRealDouble();
		
		return (-vf2 + 16*vf1 - 30*vc + 16*vb1 - vb2) / ( 12 * h );
	}
	

	public static <T extends RealType<T>> void run( IterableInterval<T> result, RealTransform transform ) 
	{

//		final RandomAccessibleInterval< T > back = Views.interval( source, Intervals.translate( result, -1, dimension ) );
//		final RandomAccessibleInterval< T > front = Views.interval( source, Intervals.translate( result, 1, dimension ) );

		Cursor<T> c = result.cursor();	
		while( c.hasNext() )
		{
			c.fwd();
			
			
		}
	}

	public static double jacobianDeterminant( 
			final double a11, final double a12, final double a13, 
			final double a21, final double a22, final double a23, 
			final double a31, final double a32, final double a33 )
	{
		return a11 * ( a22* a33 - a32 * a32 ) + (2 * a21 * a32 * a31) - (a31 * a31 * a22 ) - (a21 * a21 * a33);
	}

	public static double jacobianDeterminant( 
			final double a11, final double a12,
			final double a21, final double a22 )
	{
		return a11 * a22 - a21 * a21;
	}

	public double jacobianDeterminant( final int nd )
	{
		if( nd == 2 )
			return a11 * a22 - a21 * a21;
		else if( nd == 3 )
			return a11 * ( a22* a33 - a32 * a32 ) + (2 * a21 * a32 * a31) - (a31 * a31 * a22 ) - (a21 * a21 * a33);
		
		return Double.NaN;
	}

	public double jacobianDeterminantSymmetric( final int nd )
	{
		if( nd == 2 )
			return a11 * a22 - a21 * a21;
		else if( nd == 3 )
			return a11 * ( a22* a33 - a32 * a32 ) + (2 * a21 * a32 * a31) - (a31 * a31 * a22 ) - (a21 * a21 * a33);
		
		return Double.NaN;
	}

}
