package process;

import java.io.File;
import java.io.IOException;

import org.janelia.utility.parse.ParseUtils;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import ij.IJ;
import ij.ImagePlus;
import io.AffineImglib2IO;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.ants.ANTSDeformationField;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class RenderRegAtHiResDefField
{

	public static void main( String[] args ) throws FormatException, IOException
	{

		String imF = args[ 0 ];
		String affineF = args[ 1 ];
		String flipF = args[ 2 ];
		String warpF = args[ 3 ];
		String outSz = args[ 4 ];
		String outF = args[ 5 ];

		// TODO expose this as a parameter
		double[] factors = new double[]{ 8, 8, 4 };

		
		FinalInterval destInterval =  null;
		if( outSz.contains( ":" ))
		{
			String[] minMax = outSz.split( ":" );
			System.out.println( " " + minMax[ 0 ] );
			System.out.println( " " + minMax[ 1 ] );

			long[] min = ParseUtils.parseLongArray( minMax[ 0 ] );
			long[] max = ParseUtils.parseLongArray( minMax[ 1 ] );
			destInterval = new FinalInterval( min, max );
		}
		else
		{
			long[] outputSize = ParseUtils.parseLongArray( outSz );
			destInterval = new FinalInterval( outputSize );	
		}
		

		AffineTransform up3d = new AffineTransform( 3 );
		up3d.set( factors[ 0 ], 0, 0 );
		up3d.set( factors[ 1 ], 1, 1 );
		up3d.set( factors[ 2 ], 2, 2 );
		up3d.set( 4, 0, 3 );
		up3d.set( 4, 1, 3 );
		up3d.set( 2, 2, 3 );

		AffineTransform down3d = up3d.inverse();

		/*
		 * READ THE TRANSFORM
		 */
		AffineTransform totalAffine = null;
		if ( !flipF.equals( "none" ) )
		{
			System.out.println( "flip affine" );
			AffineTransform flipAffine = AffineImglib2IO.readXfm( 3, new File( flipF ) );
			totalAffine = flipAffine.inverse();
		} else
		{
			System.out.println( "raw down" );
			totalAffine = down3d.copy();
		}

		// The affine part
		AffineTransform3D affine = null;
		if ( affineF != null )
		{
			affine = ANTSLoadAffine.loadAffine( affineF );
		}
		totalAffine.preConcatenate( affine.inverse() );

		Img< FloatType > defLowImg = ImageJFunctions.wrap( 
				NiftiIo.readNifti( new File( warpF ) ) );
		System.out.println( defLowImg );

		// the deformation
		ANTSDeformationField df = null;
		if ( warpF != null )
		{
			System.out.println( "loading warp - factors 1 1 1" );
			df = new ANTSDeformationField( defLowImg, new double[]{1,1,1} );
			System.out.println( df.getDefInterval() );
		}

		// Concatenate all the transforms
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		if ( affine != null )
			totalXfm.add( totalAffine );

		if ( df != null )
			totalXfm.add( df );

		totalXfm.add( up3d );

		// LOAD THE IMAGE
		ImagePlus bip = IJ.openImage( imF );
		Img< FloatType > baseImg = ImageJFunctions.wrap( bip );

		IntervalView< FloatType > imgHiXfm =
		Views.interval( Views.raster( 
				RealViews.transform(
						Views.interpolate( Views.extendZero( baseImg ), new NLinearInterpolatorFactory<FloatType>() ),
						totalXfm )),
				destInterval );

//		Bdv bdv = BdvFunctions.show( imgHiXfm, "img hi xfm" );
//		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 0 ).setRange( 0, 1200 );

		System.out.println("saving to: " + outF );
		IJ.save( ImageJFunctions.wrap( imgHiXfm, "imgLoXfm" ), outF );
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends RealType<T>> RandomAccessibleInterval<T> defField3dUp( 
			RandomAccessibleInterval<T> defField, 
			double[] factors )
	{
		T t = Views.flatIterable( defField ).firstElement().copy();

		Converter< T, T > convX = new Converter< T, T >()
		{
			@Override
			public void convert( T input, T output )
			{
				output.set( input );
				output.mul( factors[ 0 ] );
			}
		};

		Converter< T, T > convY = new Converter< T, T >()
		{
			@Override
			public void convert( T input, T output )
			{
				output.set( input );
				output.mul( factors[ 1 ] );
			}
		};

		Converter< T, T > convZ = new Converter< T, T >()
		{
			@Override
			public void convert( T input, T output )
			{
				output.set( input );
				output.mul( factors[ 2 ] );
			}
		};

		RandomAccessibleInterval< T > xpos = Converters.convert( 
					Views.hyperSlice( defField, 3, 0 ), convX, t.copy());

		RandomAccessibleInterval< T > ypos = Converters.convert( 
				Views.hyperSlice( defField, 3, 1 ), convY, t.copy());

		RandomAccessibleInterval< T > zpos = Converters.convert( 
				Views.hyperSlice( defField, 3, 2 ), convZ, t.copy());

		return Views.stack( xpos, ypos, zpos );
	}

}
