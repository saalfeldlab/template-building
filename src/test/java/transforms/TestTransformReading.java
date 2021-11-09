package transforms;

import java.io.File;
import java.io.IOException;

import org.janelia.saalfeldlab.transform.io.TransformReader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.Translation3D;

public class TestTransformReading {

	private File dir;

	public static final double[] trueAffine = new double[] { 1, 0.2, 0.3, 14, 0.5, 6, 0.7, 18, 0.9, 0.10, 11, 112 };

	public static final double[] trueScale = new double[] { 11, 0, 0, 0, 0, 23, 0, 0, 0, 0, 37, 0 };

	public static final double[] trueTranslate = new double[] { 1, 0, 0, 111, 0, 1, 0, 222, 0, 0, 1, 333 };

	@Before
	public void before() throws IOException {
		dir = new File("src/test/resources/transformJson");
	}

	@Test
	public void testRead()
	{
		File affineF = new File( dir, "affine.json");
		File scaleF = new File( dir, "scale.json");
		File translatenF = new File( dir, "translation.json");

		try {
			InvertibleRealTransform affine = TransformReader.readInvertible( affineF.getCanonicalPath());

			Assert.assertTrue( "is affine", affine instanceof AffineGet );
			Assert.assertArrayEquals( "affine", 
					trueAffine,
					((AffineGet)affine).getRowPackedCopy(),
					1e-9);

			InvertibleRealTransform scale = TransformReader.readInvertible( scaleF.getCanonicalPath());
			Assert.assertTrue( "is scale3d", scale instanceof Scale3D );
			Assert.assertArrayEquals( "scale", 
					trueScale,
					((AffineGet)scale).getRowPackedCopy(),
					1e-9);

			InvertibleRealTransform translation = TransformReader.readInvertible( translatenF.getCanonicalPath());
			Assert.assertTrue( "is translation3d", translation instanceof Translation3D );
			Assert.assertArrayEquals( "translation", 
					trueTranslate,
					((AffineGet)translation).getRowPackedCopy(),
					1e-9);

		} catch (IOException e) {
			Assert.fail(e.getMessage());
		}

	}
}
