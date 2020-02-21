package util;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import net.imglib2.FinalInterval;


public class TestFieldOfViewUtil
{
	public static Optional<double[]> noDouble = Optional.empty();
	public static Optional<long[]> noLong = Optional.empty();

	@Test
	public void testEmpty()
	{
		int nd = 2;
		FieldOfView fov = new FieldOfView( nd );
		Assert.assertFalse( "return false for no parameters", 
				fov.parse( nd, noDouble, noDouble, noLong, noDouble ) );

		Assert.assertFalse( "return false for inadequate parameters", 
				fov.parse( nd, Optional.of( new double[ nd ] ), noDouble, noLong, Optional.of( new double[]{ 1, 1 } )) );
	}

	@Test
	public void testPixelSpacing()
	{
		FieldOfView fov = FieldOfView.fromSpacingSize( new double[]{ 1.0, 1.0 }, new long[]{ 10, 10 });
		Assert.assertArrayEquals( "pixel and spacing only simple", new double[]{ 10, 10 }, fov.getPhysicalWidth(), 1e-6 );
	}

	@Test
	public void testPhysicalPixel()
	{
		FieldOfView fov = FieldOfView.fromPhysicalPixel( new double[]{ 9.0, 9.0 }, new long[]{ 10, 10 });
		Assert.assertArrayEquals( "from physical pixel. simple", new double[]{ 1, 1 }, fov.getSpacing(), 1e-6 );

		fov = FieldOfView.fromPhysicalPixel( new double[]{ -3, -3 }, new double[]{ 3.0, 3.0 }, new long[]{ 7, 7 });
		Assert.assertArrayEquals( "from physical pixel. less simple", new double[]{ 1.0, 1.0 }, fov.getSpacing(), 1e-6 );

		fov = FieldOfView.fromPhysicalPixel( new double[]{ -3, -3 }, new double[]{ 3.0, 3.0 }, new long[]{ 13, 13 });
		Assert.assertArrayEquals( "from physical pixel. even less simple", new double[]{ 0.5, 0.5 }, fov.getSpacing(), 1e-6 );
	}
	
	@Test
	public void testSubsetAndPinning()
	{
		FieldOfView fov = FieldOfView.fromPhysicalPixel( new double[]{ 20.0, 20.0 }, new long[]{ 11, 11 });
		FieldOfView fovsub = fov.getPixelSubset( new FinalInterval( new long[]{2, 2}, new long[]{ 9, 9 }) );
		System.out.println("fov: " + fov );
		System.out.println("  fov phy w: " + Arrays.toString( fov.getPhysicalWidth() ));
		System.out.println("  fov pix w: " + Arrays.toString( fov.getPhysicalWidthFromPixelSpacing() ));
		System.out.println(" ");
		System.out.println("fovsub: " + fovsub );
		System.out.println("  fovsub phy w: " + Arrays.toString( fovsub.getPhysicalWidth() ));
		System.out.println("  fov pix w: " + Arrays.toString( fov.getPhysicalWidthFromPixelSpacing() ));
		System.out.println(" ");
	}

}
