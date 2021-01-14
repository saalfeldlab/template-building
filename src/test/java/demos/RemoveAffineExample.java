package demos;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5DisplacementField;
import org.janelia.saalfeldlab.transform.io.TransformReader;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.numeric.real.DoubleType;

public class RemoveAffineExample
{

	public static void main( String[] args ) throws Exception 
	{
		final String dfieldPath = args[ 0 ];
		final String destinationN5 = args[ 1 ];
		final String dataset = args[ 2 ]; 

		final String affinePath;
		if( args.length > 3 )
			affinePath = args[ 3 ];
		else
			affinePath = "";


		write( dfieldPath, destinationN5, dataset, affinePath );

		testOutput( dfieldPath, destinationN5, dataset );
	}

	public static void testOutput( 
			final String dfieldPath,
			final String destinationN5,
			final String dataset ) throws Exception
	{
		final RealTransform dfieldRaw = TransformReader.read( dfieldPath );

		final N5Reader n5 = new N5FSReader( destinationN5 );	
		RealTransform transform = N5DisplacementField.open( n5, dataset, false );

		RealPoint p = new RealPoint( 294.5, 137.2, 91.7 );
		RealPoint q = new RealPoint( 3 );

		dfieldRaw.apply( p, q );
		System.out.println( "df: " + q );

		transform.apply( p, q );
		System.out.println( "n5: " + q );
	}

	public static void write( 
			final String dfieldPath,
			final String destinationN5,
			final String dataset,
			final String affinePath ) throws IOException
	{
		// dfield
		final RealTransform dfieldRaw = TransformReader.read( dfieldPath );
		System.out.println( dfieldRaw );

		final InvertibleRealTransform affine = TransformReader.readInvertible( affinePath );
		System.out.println( affine );

		final Interval interval = new FinalInterval( 505, 235, 147 );

		final N5Writer n5 = new N5FSWriter( destinationN5 );
		final int[] blockSize = new int[] { 32, 32, 32 };
		final GzipCompression compression = new GzipCompression();

		final Scale3D pixelToPhysical = new Scale3D( 1.24296, 1.24296, 1.24296 );
		final DoubleType quantizationType = new DoubleType();
		double maxError = 0;

		ExecutorService threadPool = Executors.newSingleThreadExecutor();

		
		/*
		 * The below needs a method for saving an arbitrary RealTransform
		 * as a n5 dfield.
		 * 
		 * Wait for merge of
		 * https://github.com/bogovicj/n5-imglib2/tree/dfieldApiAdditions
		 * or similar
		 */

//		if( !affinePath.isEmpty() )
//		{
//			final RealTransformSequence transform = new RealTransformSequence();
//			transform.add( dfieldRaw );
//			transform.add( affine.inverse() );
//
//			N5DisplacementField.save( 
//					n5, dataset, blockSize, compression, 
//					transform, pixelToPhysical, interval, quantizationType, maxError, threadPool );
//			n5.setAttribute( dataset, "spacing", new double[] { 1.24296, 1.24296, 1.24296 } );
//			
//			N5DisplacementField.saveAffine( ( AffineGet ) affine, n5, dataset ); 
//		}
//		else
//		{
//			final RealTransform transform = dfieldRaw;
//			N5DisplacementField.save( n5, dataset, blockSize, compression, transform, pixelToPhysical, interval, quantizationType, maxError, threadPool );
//			n5.setAttribute( dataset, "spacing", new double[] { 1.24296, 1.24296, 1.24296 } );
//		}
//

	}


}
