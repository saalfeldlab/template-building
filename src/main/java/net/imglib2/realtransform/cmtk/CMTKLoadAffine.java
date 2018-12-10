package net.imglib2.realtransform.cmtk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import net.imglib2.realtransform.AffineTransform3D;

/*
 *This string:
 * 
registration {
	reference_study "/nrs/saalfeld/john/projects/flyChemStainAtlas/cmtk_test/MakeAverageBrain/refbrain/start-1.nrrd"
	floating_study "images/F-A5_01.nrrd"
	affine_xform {
		xlate -2.4 -10.4 1.6 
		rotate -1.534754103 0.285372899 5.294666352 
		scale 1.0112 0.8972 0.9244 
		shear 0 0 0 
		center 500 350 150 
	}
}

 * should produce:
1.00687	-0.0933103	-0.00503646	0	
0.0826429	0.893062	-0.0240297	0	
0.00686751	0.0242281	0.924057	0	
-35.7916	70.049	23.9201	1
	
 * according to cmtk's dof2mat
 * 
 */

/*
 * echo 500 350 150 | streamxform -- /nrs/saalfeld/john/projects/flyChemStainAtlas/cmtk_test/MakeAverageBrain/Registration/affine/start-1_F-A5_01_9dof.list
 * yields
 * 497.6 339.6 151.6
 */

public class CMTKLoadAffine
{
	
//	public static final String REFERENCE_STUDY = "reference_study";
//	public static final String FLOATING_STUDY = "floating_study";

	protected String reference_study;
	protected String floating_study;

	protected double[] xlate;	// parameters [0,1,2]
	protected double[] rotate;	// parameters [3,4,5]
	protected double[] scale;	// parameters [6,7,8]
	protected double[] shear;	// parameters [9,10,11]
	protected double[] center;	// parameters [12,13,14]
	protected boolean logScaleFactors = false;
	
	protected final AffineTransform3D mtx;

	/**
	 * See cmtkCompatibilityMatrix4x4
	 */
	public CMTKLoadAffine()
	{
		mtx = new AffineTransform3D();
	}
	
	/**
	 * Converts the parameters to a single transformation matrix
	 * @return
	 */
	public AffineTransform3D toAffine()
	{
		// alpha, theta and phi are in radians,
		// expect rotate vector to be in degrees
		final double alpha = Math.toRadians( rotate[ 0 ] );
		final double theta = Math.toRadians( rotate[ 1 ] );
		final double phi   = Math.toRadians( rotate[ 2 ] );
		
		final double cos0 = Math.cos( alpha );
		final double cos1 = Math.cos( theta );
		final double cos2 = Math.cos( phi );
		final double sin0 = Math.sin( alpha );
		final double sin1 = Math.sin( theta );
		final double sin2 = Math.sin( phi );
		
		final double sin0xsin1 = sin0 * sin1;
		final double cos0xsin1 = cos0 * sin1;
		
		final double scaleX = logScaleFactors ? Math.exp( scale[ 0 ] ) : scale[ 0 ];
		final double scaleY = logScaleFactors ? Math.exp( scale[ 1 ] ) : scale[ 1 ];
		final double scaleZ = logScaleFactors ? Math.exp( scale[ 2 ] ) : scale[ 2 ];
		
		mtx.set( scaleX *  cos1 * cos2, 0, 0 );
		mtx.set( scaleX * -cos1 * sin2, 0, 1 );
		mtx.set( scaleX * -sin1, 0, 2 );

		mtx.set( scaleY * (  sin0xsin1*cos2 + cos0*sin2 ), 1, 0 );
		mtx.set( scaleY * ( -sin0xsin1*sin2 + cos0*cos2 ), 1, 1 );
		mtx.set( scaleY * sin0 * cos1, 1, 2 );
		
		mtx.set( scaleZ * (  cos0xsin1*cos2 - sin0*sin2 ), 2, 0 );
		mtx.set( scaleZ * ( -cos0xsin1*sin2 - sin0*cos2 ), 2, 1 );
		mtx.set( scaleZ * cos0 * cos1, 2, 2 );

		// TODO generate shears
		// (skipping for the moment)
		
		// transform rotation center
		double[] xfmCenter = new double[ 3 ];
		transpose( mtx ).apply( center, xfmCenter );
		

		// set translations
		mtx.set( xlate[ 0 ] - xfmCenter[ 0 ] + center[ 0 ], 0, 3 );
		mtx.set( xlate[ 1 ] - xfmCenter[ 1 ] + center[ 1 ], 1, 3 );
		mtx.set( xlate[ 2 ] - xfmCenter[ 2 ] + center[ 2 ], 2, 3 );

		return mtx;
	}

	public static AffineTransform3D transpose( AffineTransform3D in )
	{
		AffineTransform3D out = new AffineTransform3D();
		for( int i = 0; i < 3; i++ ) for( int j = 0; j < 3; j++ )
		{
			out.set( in.get( i, j ), j, i );
		}
		for( int i = 0; i < 3; i++ )
		{
			out.set( in.get( i, 3 ), i, 3 );
		}
		return out;
	}

	/**
	 * Performs checks but will be slower than 'load'
	 * @param f
	 * @return
	 * @throws IOException
	 */
	public AffineTransform3D loadSafe( final File f ) throws IOException
	{
		String val = new String( Files.readAllBytes( Paths.get( f.getAbsolutePath() ) ));
		System.out.println( val );
		
		if( !val.contains( "registration" ) || !val.contains( "affine_xform" ))
			return null;
		
		System.out.println("reading");
		
//		String[] lines = ;
		return load( Arrays.asList( val.split( "\n" )) );
	}
	
	public AffineTransform3D load( final File f ) throws IOException
	{
		return load( Files.readAllLines(  Paths.get( f.getAbsolutePath() ) ) );
	}
	
	public AffineTransform3D load( final Collection<String> lines  )
	{
		for( String l : lines )
		{
			String lt = l.trim(); 
			if( lt.startsWith( "reference_study" ))
			{
				reference_study = getValue( lt ).replaceAll( "\"", "" );
			}
			else if( lt.startsWith( "floating_study" ))
			{
				floating_study = getValue( lt ).replaceAll( "\"", "" );
			}
			else if( lt.startsWith( "xlate" ))
			{
				xlate = parseDouble( lt.replaceAll( "xlate ", "" ), " " );
			}
			else if( lt.startsWith( "rotate" ))
			{
				rotate = parseDouble( lt.replaceAll( "rotate ", "" ), " " );
			}
			else if( lt.startsWith( "scale" ))
			{
				scale = parseDouble( lt.replaceAll( "scale ", "" ), " " );
			}
			else if( lt.startsWith( "log_scale" ))
			{
				logScaleFactors = true;
				scale = parseDouble( lt.replaceAll( "scale ", "" ), " " );
			}
			else if( lt.startsWith( "shear" ))
			{
				shear = parseDouble( lt.replaceAll( "shear ", "" ), " " );
			}
			else if( lt.startsWith( "center" ))
			{
				center = parseDouble( lt.replaceAll( "center ", "" ), " " );
			}
		}
		//System.out.println("reference_study " + reference_study );
		
		return toAffine();
	}
	
	public String getValue( String line )
	{
		return line.substring( line.indexOf( " " ) + 1 );
	}
	
	public double[] parseDouble( String line, String delim )
	{
		String[] pieces = line.split( delim );
		double[] out = new double[ pieces.length ];
		
		for( int i = 0; i < pieces.length; i++ )
			out[ i ] = Double.parseDouble( pieces[ i ] );
		
		return out;
	}

	public static void main( String[] args ) throws IOException
	{
//		String path = "/nrs/saalfeld/john/projects/flyChemStainAtlas/cmtk_test/MakeAverageBrain/Registration/affine/start-1_F-A5_01_9dof.list/registration";
//		String string = "affine_xform {\nxlate -12.4 4.4 4.4\nrotate -1.17363549 -1.339057449 -5.764051667\nscale 0.9724 1.0142 0.9326\nshear 0 0 0\ncenter 500 350 150\n}";
		
		String in = args[ 0 ];
		//System.out.println( in );
		
		File f = new File( in );
		
		
		CMTKLoadAffine cla = new CMTKLoadAffine();
		
		AffineTransform3D res = null;
		if( f.exists() )
		{
			res = cla.load( f );
		}
		else
		{
			res = cla.load( Arrays.asList( in.split( "\n" ) ));
		}
		//System.out.println( res );
		System.out.println( Arrays.toString( res.getRowPackedCopy() ).replace("[","").replace("]",""));
		
	}

}
