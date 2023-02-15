package transforms;

import java.io.File;
import java.io.IOException;
import java.util.function.BiConsumer;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;

import com.fasterxml.jackson.databind.deser.BuilderBasedDeserializer;

import ij.IJ;
import ij.ImagePlus;
import io.IOHelper;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.DeformationFieldTransform;
import net.imglib2.realtransform.DisplacementFieldTransform;
import net.imglib2.realtransform.InvertibleDisplacementFieldTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale2D;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.TransformToDeformationField;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class Test2dN5Dfield
{

	String remoteBoats = "https://imagej.nih.gov/ij/images/boats.gif";

	File localBoats = new File( "testData/boats.tif" );

	File dfieldH5File = new File( "testData/boats_affine_dfield.h5" );

	File trueTransformedImage = new File( "testData/boats_xfm.tif" );

	File trueInvTransformedImage = new File( "testData/boats_invxfm.tif" );

	File trueTransformedInvTransformedImage = new File( "testData/boats_xfm_invxfm.tif" );

	File testTransformedImage = new File( "testData/boats_xfm_withH5.tif" );

	File testInvTransformedImage = new File( "testData/boats_invxfm_withH5.tif" );

	File testTransformedInvTransformedImage = new File( "testData/boats_xfm_invxfm_withH5.tif" );

	public static void main( String[] args ) throws IOException
	{
		testFwdInvAffine();
	}

	public static void testFwdInvAffine() throws IOException
	{
		Test2dN5Dfield obj = new Test2dN5Dfield();
		RandomAccessibleInterval< UnsignedByteType > boatsImage = obj.openBoats();

		AffineTransform2D affine = new AffineTransform2D();
		affine.translate( 40, 20 );
		affine.rotate( Math.PI / 12 );

		affine = affine.inverse();

		// ImageJFunctions.show( boatsImage );

		double[] mediumTranslation = new double[] { -25, 25 };
		double[] bigTranslation = new double[] { -25, 25 };
		double[] center = new double[] { 500, 100 };
		double[] width = new double[] { 85, 85 };

		RandomAccessibleInterval< DoubleType > dfieldRai = buildDeformationField( boatsImage, bigTranslation, center, width );

		DisplacementFieldTransform trueDfield = new DisplacementFieldTransform( dfieldRai );
		InvertibleDisplacementFieldTransform invXfm = new InvertibleDisplacementFieldTransform( trueDfield );

		Scale2D pixelToPhysical = new Scale2D( 1.0, 1.0 );
		FloatImagePlus< FloatType > invdfield = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( dfieldRai ) );
		TransformToDeformationField.transformToDeformationField( invXfm, invdfield, pixelToPhysical );

		if ( !obj.dfieldH5File.exists() )
		{
			System.out.println("writing h5 transform file.");
			N5HDF5Writer n5Writer = new N5HDF5Writer( obj.dfieldH5File.getAbsolutePath(), 2, 32, 32 );
			N5DisplacementField.save( n5Writer, N5DisplacementField.FORWARD_ATTR, affine, dfieldRai, new double[] { 1, 1 }, new int[] { 2, 32, 32 }, new GzipCompression() );
			N5DisplacementField.save( n5Writer, N5DisplacementField.INVERSE_ATTR, affine.inverse(), invdfield, new double[] { 1, 1 }, new int[] { 2, 32, 32 }, new GzipCompression() );
			n5Writer.close();
			try
			{
				System.out.println("waiting");
				Thread.sleep( 2000 );
				System.out.println("continuing..");
			}
			catch ( InterruptedException e )
			{
				e.printStackTrace();
			}
		}


		RealTransformSequence totalXfm = new RealTransformSequence();
		totalXfm.add( trueDfield );
		totalXfm.add( affine );

		RealTransformSequence totalInvXfm = new RealTransformSequence();
		totalInvXfm.add( affine.inverse() );
		totalInvXfm.add( invXfm );


		RandomAccessibleInterval< UnsignedByteType > boatsXfmTrue = 
				applyAndWrite( totalXfm, boatsImage, obj.trueTransformedImage );

		applyAndWrite( totalInvXfm, boatsImage, obj.trueInvTransformedImage );

		applyAndWrite( totalInvXfm, boatsXfmTrue, obj.trueTransformedInvTransformedImage );

//		if( true )
//			return;

		boolean success = false;
		RealTransform h5xfm = null;
		RealTransform h5invxfm = null;

		int nTries = 5;
		int i = 0;
		while( !success && i < nTries )
		{
			System.out.println( "try : " + i );
			try
			{
				i++;
				h5xfm = N5DisplacementField.open( new N5HDF5Reader( obj.dfieldH5File.getAbsolutePath(), 2, 32, 32 ), N5DisplacementField.FORWARD_ATTR, false );
				h5invxfm = N5DisplacementField.open( new N5HDF5Reader( obj.dfieldH5File.getAbsolutePath(), 2, 32, 32 ), N5DisplacementField.INVERSE_ATTR, true );
				success = true;
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				System.out.println( " " );
			}

			try
			{
				System.out.println("waiting");
				Thread.sleep( 2000 );
				System.out.println("continuing..");
			}
			catch ( InterruptedException e )
			{
				e.printStackTrace();
			}
		}
		
		if( h5invxfm == null )
		{
			System.out.println( "failed" );
			return;
		}

		applyAndWrite( h5xfm, boatsImage, obj.testTransformedImage );
		applyAndWrite( h5invxfm, boatsImage, obj.testInvTransformedImage );
		applyAndWrite( h5invxfm, boatsXfmTrue, obj.testTransformedInvTransformedImage );

	}

	public static RandomAccessibleInterval<UnsignedByteType> applyAndWrite( final RealTransform xfm, final RandomAccessibleInterval< UnsignedByteType > img, final File outputFile )
	{
		IntervalView< UnsignedByteType > imgXfm = Views.interval( Views.raster( new RealTransformRandomAccessible<>( Views.interpolate( Views.extendZero( img ), new NLinearInterpolatorFactory<>() ), xfm ) ), img );
		ImagePlus ip = ImageJFunctions.wrap( imgXfm, "boats Xfm" );
		IOHelper.write( ip, outputFile );
		return imgXfm;
	}

	public static RandomAccessibleInterval< DoubleType > buildDeformationField( final Interval imageInterval, final double[] translation, final double[] center, final double[] width )
	{
		FinalInterval displacementInterval = new FinalInterval( new long[] { imageInterval.min( 0 ), imageInterval.min( 1 ), 0 }, new long[] { imageInterval.max( 0 ), imageInterval.max( 1 ), 1 } );

		FunctionRandomAccessible< DoubleType > displacement = new FunctionRandomAccessible<>( 3, new BiConsumer< Localizable, DoubleType >()
		{
			@Override
			public void accept( Localizable p, DoubleType v )
			{
				int i = ( p.getDoublePosition( 2 ) < 0.5 ) ? 0 : 1;

				double x = ( p.getDoublePosition( 0 ) - center[ 0 ] );
				double y = ( p.getDoublePosition( 1 ) - center[ 1 ] );
				v.setReal( translation[ i ] * Math.exp( -( x * x + y * y ) / ( width[ i ] * width[ i ] ) ) );
			}
		}, DoubleType::new );

		IntervalView< DoubleType > displacementFieldRai = Views.interval( displacement, displacementInterval );

		return displacementFieldRai;
	}

	public RandomAccessibleInterval< UnsignedByteType > openBoats() throws IOException
	{
		ImagePlus ip = null;
		if ( localBoats.exists() )
		{
			ip = IJ.openImage( localBoats.getPath() );
			// hyperslice because 'boats' opens as an RGB image, with all
			// channels identical
			// return Views.hyperSlice(
			// (RandomAccessibleInterval<UnsignedByteType>)obj, 2, 0 );
		}
		else
		{
			ip = IJ.openImage( remoteBoats );
			IJ.save( ip, localBoats.getAbsolutePath() );
//			ImagePlus ip =  IJ.openImage( remoteBoats );
//
//			// hyperslice because 'boats' opens as an RGB image, with all channels identical 
//			RandomAccessibleInterval<UnsignedByteType> imgout = Views.hyperSlice( (RandomAccessibleInterval<UnsignedByteType>)obj, 2, 0 );
//			return imgout;
		}
		return ImageJFunctions.wrapByte( ip );
	}

}
