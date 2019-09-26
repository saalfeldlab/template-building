package io;

import static org.junit.Assert.*;

import java.lang.reflect.Field;

import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.janelia.saalfeldlab.transform.io.TransformReader.H5TransformParameters;
import org.junit.Test;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.DeformationFieldTransform;
import net.imglib2.realtransform.InvertibleDeformationFieldTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.inverse.InverseRealTransformGradientDescent;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.ConstantUtils;

public class TransformFileParseTest
{
	static final String TEST_MAT_FILE = "src/test/resources/affinemtx.mat";
	static final String TEST_TXT_FILE = "src/test/resources/affinemtx.txt";

	static final String TEST_H5_FILE = "src/test/resources/my.h5";

	static final String TEST_H5_DATASET_A = "foo";
	static final String TEST_H5_DATASET_AFWD = "foo/dfield";
	static final String TEST_H5_DATASET_AINV = "foo/invdfield";

	static final String TEST_H5_DATASET_B = "bar";
	static final String TEST_H5_DATASET_BFWD = "bar/dfield";
	static final String TEST_H5_DATASET_BINV = "bar/invdfield";

	@Test
	public void testParse()
	{
		RealTransform result = TransformReader.read( TEST_MAT_FILE );
		assertTrue( ".mat an AffineTransform3D", result instanceof AffineTransform3D );

		RealTransform result_inv = TransformReader.read( TEST_MAT_FILE +"?i" );
		assertTrue( "inv .mat an AffineTransform3D", result_inv instanceof AffineTransform3D );

		RealTransform result_txt = TransformReader.read( TEST_TXT_FILE );
		assertTrue( ".txt an AffineTransform", result_txt instanceof AffineTransform );


		AffineTransform3D affine = ((AffineTransform3D) result );
		AffineTransform3D affine_inv = ((AffineTransform3D) result_inv );
		AffineTransform affine_txt = ((AffineTransform) result_txt );

		assertArrayEquals( "mat inverse", affine_inv.getRowPackedCopy(), affine.inverse().getRowPackedCopy(), 1e-6 );

		assertArrayEquals( "mat vs txt", affine_txt.getRowPackedCopy(), affine.getRowPackedCopy(), 1e-6 );
	}

	@Test
	public void testH5Parse()
	{

		String inv = "i";

		String fileDataset = String.join( "?", TEST_H5_FILE, TEST_H5_DATASET_A );
		String fileDatasetInv = String.join( "?", TEST_H5_FILE, TEST_H5_DATASET_A, inv );


		H5TransformParameters params = TransformReader.H5TransformParameters.parse( fileDataset );
		H5TransformParameters invparams = TransformReader.H5TransformParameters.parse( fileDatasetInv );
		
		assertTrue( "params not inverted", !params.inverse );
		assertEquals( "params dataset", TEST_H5_DATASET_AFWD, params.fwddataset );
		assertEquals( "params dataset", TEST_H5_DATASET_AINV, params.invdataset );

		assertTrue( "inv params are inverted", invparams.inverse );
		assertEquals( "inv params dataset", TEST_H5_DATASET_AFWD, invparams.fwddataset );
		assertEquals( "inv params dataset", TEST_H5_DATASET_AINV, invparams.invdataset );

	}

	@Test
	public void testIterativeInverseParse() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException
	{
		assertTrue( "true", true );

		// get an optimizer
		RandomAccessibleInterval< FloatType > img = ConstantUtils.constantRandomAccessibleInterval( new FloatType(), 3, new FinalInterval( 2, 2, 2 ) );
		InverseRealTransformGradientDescent optimizer = new InvertibleDeformationFieldTransform<>( new DeformationFieldTransform<>( img ) ).getOptimzer();

		int maxIterVal = 200;
		double tolVal = 0.012;
		double cVal = 0.0005;
		double betaVal = 0.7777;
		String s = String.format( "a?invopt;maxIters=%d;tolerance=%f;c=%f;beta=%f?i", maxIterVal, tolVal, cVal, betaVal );
		TransformReader.setIterativeInverseParameters( optimizer, s );

		// Use reflection to check that values were set correctly
		Class< ? extends InverseRealTransformGradientDescent > c = optimizer.getClass();

		Field maxItersField = c.getDeclaredField( "maxIters" );
		maxItersField.setAccessible( true );
		assertEquals( "is maxiters set?", maxItersField.getInt( optimizer ), maxIterVal );

		Field tolField = c.getDeclaredField( "tolerance" );
		tolField.setAccessible( true );
		assertEquals( "is tolerance set?", tolField.getDouble( optimizer ), tolVal, 1e-9 );

		Field cField = c.getDeclaredField( "c" );
		cField.setAccessible( true );
		assertEquals( "is c set?", cField.getDouble( optimizer ), cVal, 1e-9 );

		Field betaField = c.getDeclaredField( "beta" );
		betaField.setAccessible( true );
		assertEquals( "is beta set?", betaField.getDouble( optimizer ), betaVal, 1e-9 );
	}
}
