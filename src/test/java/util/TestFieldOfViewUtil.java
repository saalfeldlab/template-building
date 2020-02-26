package util;

import java.util.Arrays;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.util.Intervals;


public class TestFieldOfViewUtil
{
	public static Optional<double[]> noDouble = Optional.empty();
	public static Optional<double[]> ones = Optional.of( new double[]{1,1});

	public static Optional<long[]> noLong = Optional.empty();
	public static Optional<Interval> noInterval = Optional.empty();
	public static Optional empty = Optional.empty();

	@Test
	public void testEmpty()
	{
		int nd = 2;
		FieldOfView fov = new FieldOfView( nd );
		Assert.assertNull( "return null for no parameters", 
				fov.parse( nd, empty, empty,  empty,  empty, empty  ));

		Assert.assertNull( "return false for inadequate parameters", 
				fov.parse( nd, empty, ones, empty, empty, ones ));
	}

	@Test
	public void testPixelSpacing()
	{
		FieldOfView fov = FieldOfView.fromSpacingSize( new double[]{ 1.0, 1.0 }, new FinalInterval( new long[]{ 10, 10 }));
		Assert.assertArrayEquals( "pixel and spacing only simple", new double[]{ 10, 10 }, fov.getPhysicalWidth(), 1e-6 );
	}

	@Test
	public void testPhysicalPixel()
	{
		FieldOfView fov = FieldOfView.fromPhysicalPixel( new double[]{ 9.0, 9.0 }, new FinalInterval( new long[]{ 10, 10 }));
		Assert.assertArrayEquals( "from physical pixel. simple", new double[]{ 1, 1 }, fov.getSpacing(), 1e-6 );

		fov = FieldOfView.fromPhysicalPixel( new double[]{ -3, -3 }, new double[]{ 3.0, 3.0 }, new FinalInterval( new long[]{ 7, 7 }));
		Assert.assertArrayEquals( "from physical pixel. less simple", new double[]{ 1.0, 1.0 }, fov.getSpacing(), 1e-6 );

		fov = FieldOfView.fromPhysicalPixel( new double[]{ -3, -3 }, new double[]{ 3.0, 3.0 }, new FinalInterval( new long[]{ 13, 13 }));
		Assert.assertArrayEquals( "from physical pixel. even less simple", new double[]{ 0.5, 0.5 }, fov.getSpacing(), 1e-6 );
	}

	
	@Test
	public void testSubsetAndPinning()
	{
		double eps = 1e-6;

		double[] origMin = new double[]{ -10.0, -10.0 };
		double[] origMax = new double[]{  10.0,  10.0 };
		long[] origPixWidth = new long[]{ 21, 21 };

		double[] newSpacing = new double[]{ 1.5, 1.5 };

		double[] originA = new double[]{ 0.0, 0.0 };
		RealPoint originP = RealPoint.wrap( originA );

		double[] testA = new double[]{ 1.2, 1.2 };
		RealPoint testP = RealPoint.wrap( testA );
		

		FieldOfView fov = FieldOfView.fromPhysicalPixel( origMin, origMax, new FinalInterval( origPixWidth ));

		Assert.assertTrue( "origin on original grid", fov.onGrid( originP, eps ));
		Assert.assertFalse( "test not on original grid", fov.onGrid( testP, eps ));

		FieldOfView fovsubmin = fov.copy();
		fovsubmin.setSpacing( newSpacing );
		fovsubmin.updatePhysicalPinMin();

		FieldOfView fovsubmax = fov.copy();
		fovsubmax.setSpacing( newSpacing );
		fovsubmax.updatePhysicalPinMax();

		FieldOfView fovsubcenter = fov.copy();
		fovsubcenter.setSpacing( newSpacing );
		fovsubcenter.updatePhysicalPinCenter();


		System.out.println("fov: " + fov );
		System.out.println("fovsubmin: " + fovsubmin );
		System.out.println("fovsubmax: " + fovsubmax );
		System.out.println("fovsubcenter: " + fovsubcenter );

//		FieldOfView fovsub = fovsubcenter;
//		System.out.println("  fov phy w: " + Arrays.toString( fov.getPhysicalWidth() ));
//		System.out.println("  fov pix w: " + Arrays.toString( fov.getPhysicalWidthFromPixelSpacing() ));
//		System.out.println(" ");
//		System.out.println("  fovsub phy w: " + Arrays.toString( fovsub.getPhysicalWidth() ));
//		System.out.println("  fov pix w: " + Arrays.toString( fov.getPhysicalWidthFromPixelSpacing() ));
//		System.out.println(" ");
		
		
		Assert.assertArrayEquals( "check pinned min", origMin, Intervals.minAsDoubleArray( fovsubmin.getPhysical() ), 1e-6 );
		Assert.assertArrayEquals( "check pinned max", origMax, Intervals.maxAsDoubleArray( fovsubmax.getPhysical() ), 1e-6 );
	}

}
