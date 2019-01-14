package transforms;

import java.io.IOException;

import ij.IJ;
import net.imglib2.Cursor;
import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import process.RenderTransformed;

public class EstimateBoundingBoxMask
{

	public static void main( String[] args ) throws Exception
	{
		int i = 0;
		String maskPath = args[ i++ ];
		double maskThresh = Double.parseDouble( args[ i++ ]);
		int dsFactor = Integer.parseInt( args[ i++ ]);
		int pad = Integer.parseInt( args[ i++ ]);

//		String maskPath = "/groups/saalfeld/saalfeldlab/FROM_TIER2/fly-light/20160107_31_63X_female/data_1/A2/A2_TileConfiguration_lens.registered.tif";
//		double maskThresh = 20;
//		int dsFactor = 16;
//		int pad = 32;
//
//		String[] xfmPaths = new String[]
//		{
//			"/nrs/saalfeld/john/projects/flyChemStainAtlas/take6_groupwise_template_442/f-flip-r1p5-affineCC/ALLF-F-A2_TileConfiguration_lens_registered_downAffine.mat",
//			"/nrs/saalfeld/john/projects/flyChemStainAtlas/downsample_gauss/female_442_toCanonical/A2_TileConfiguration_lens.registered_down.txt",
//			"/nrs/saalfeld/john/projects/flyChemStainAtlas/take6_groupwise_template_442/f-flip-r1p5-affineCC/ALLF-F-A2_TileConfiguration_lens_registered_downWarp.nii"	
//		};
//		boolean[] invXfms = new boolean[]{ true, true, false };
//
//		int i = 0;
//		for( String xfmP : xfmPaths )
//		{
//			totalXfm.add( RenderTransformed.loadTransform( xfmP, invXfms[ i++ ] ));
//		}


		final InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		while( i < args.length )
		{
			boolean invert = false;
			if( args[ i ].equals( "-i" ))
			{
				invert = true;
				i++;
			}

			if( invert )
				System.out.println( "loading transform from " + args[ i ] + " AND INVERTING" );
			else
				System.out.println( "loading transform from " + args[ i ]);

			InvertibleRealTransform xfm = null;
			try 
			{
				xfm = RenderTransformed.loadTransform( args[ i ], invert );
			} 
			catch (IOException e) {
				e.printStackTrace();
			}

			if( xfm == null )
			{
				System.err.println("  failed to load transform ");
				System.exit( 1 );
			}

			totalXfm.add( xfm );
			i++;
		}

		final Img< FloatType > mask = ImageJFunctions.convertFloat( IJ.openImage( maskPath ) );
		final Cursor< FloatType > c = Views.flatIterable( Views.subsample( mask, dsFactor )).cursor();

		final int nd = mask.numDimensions();
		final double[] min = new double[ nd ];
		final double[] max = new double[ nd ];

		System.out.print( "sampling...");
		long start = System.currentTimeMillis();
		final RealPoint ptOriginal = new RealPoint( nd );
		final RealPoint ptXfm = new RealPoint( nd );
		while( c.hasNext() )
		{
			c.fwd();
			if( c.get().get() < maskThresh )
			{
				continue;
			}
			for( int d = 0; d < nd; d++ )
				ptOriginal.setPosition( dsFactor * c.getDoublePosition( d ), d );

			totalXfm.applyInverse( ptXfm, ptOriginal );
//			System.out.println("pt orig : " + ptOriginal );
//			System.out.println("pt xfm  : " + ptXfm );

			for( int d = 0; d < nd; d++ )
			{
				if( ptXfm.getDoublePosition( d ) < min[ d ])
					min[ d ] = ptXfm.getDoublePosition( d );

				if( ptXfm.getDoublePosition( d ) > max[ d ])
					max[ d ] = ptXfm.getDoublePosition( d );
			}
		}
		long end = System.currentTimeMillis();
		long timeSecs = ( end - start ) / 1000;
		System.out.print( "done, took " + timeSecs + " seconds\n");

		long[] min_l = new long[ nd ];
		long[] max_l = new long[ nd ];
		String min_s = "";
		String max_s = "";
		for( int d = 0; d < nd; d++ )
		{
			min_l[ d ] = (long)Math.floor( min[ d ]);
			max_l[ d ] = (long)Math.ceil( max[ d ]);

			if( d == 0 )
			{
				min_s = min_s + min_l[ d ];
				max_s = max_s + max_l[ d ];
			}
			else
			{
				min_s = min_s + "," + min_l[ d ];
				max_s = max_s + "," + max_l[ d ];
			}
		}
		System.out.println( "BBOX " + min_s + ":" + max_s );
	}
}

