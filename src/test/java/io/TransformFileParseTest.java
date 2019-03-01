package io;

import static org.junit.Assert.*;

import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.junit.Before;
import org.junit.Test;

import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;

public class TransformFileParseTest
{
	static final String TEST_MAT_FILE = "src/test/resources/affinemtx.mat";
	static final String TEST_TXT_FILE = "src/test/resources/affinemtx.txt";

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
}
