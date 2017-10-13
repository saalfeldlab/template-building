package org.janelia;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.real.AbstractRealType;
import net.imglib2.type.numeric.real.DoubleType;


public class TbarPrediction extends AbstractRealType<TbarPrediction> implements Serializable, RealLocalizable
{

	private static final long serialVersionUID = -4733626426701554835L;

	public String status;
	public double confidence;
	public int body_ID;
	public int[] location;
	public TbarPartner[] partners;

	public static void main( String[] args ) throws IOException
	{

//		String path = "/data-ssd/john/flyem/small_tbars2.json";
//		String outpath = "/data-ssd/john/flyem/small_tbars2.ser";
		
//		String path = "/data-ssd/john/flyem/test_tbars2.json";
//		String outpath = "/data-ssd/john/flyem/test_tbars2.ser";
		
//		String path = "/data-ssd/john/flyem/small_synapses.json";
//		String outpath = "/data-ssd/john/flyem/small_synapses.ser";
		
		String path = "/data-ssd/john/flyem/synapses.json";
		String outpath = "/data-ssd/john/flyem/synapses.ser";

		System.out.println( "reading" );
		TbarCollection tbars = TbarPrediction.loadAll( path );
		System.out.println( tbars.toString() );
		
		System.out.println( "writing" );
		TbarPrediction.write( tbars, outpath );
		TbarCollection tbars2 = TbarPrediction.loadSerialized( outpath );
		
		System.out.println( "done" );
	}

	public static void write( TbarCollection tbars, String outpath )
	{
		try {
	         FileOutputStream fileOut = new FileOutputStream( outpath );
	         ObjectOutputStream out = new ObjectOutputStream(fileOut);

	         out.writeObject( tbars );
	         out.close();
	         fileOut.close();
	         System.out.printf("Serialized data is saved in " + outpath + "\n" );
		}
		catch(IOException i)
		{
			i.printStackTrace();
		}
	}
	
	public static TbarPrediction load( JSONObject obj )
	{
		TbarPrediction out = new TbarPrediction();
		JSONObject tbar = (JSONObject)obj.get( "T-bar" );
		
		out.status = tbar.getString( "status" );
		out.confidence = tbar.getDouble( "confidence" );
		out.body_ID = tbar.getInt( "body ID" );
		JSONArray locArray = (JSONArray)tbar.get( "location" );

		JSONArray partnerObj = (JSONArray)obj.get( "partners" );
		//System.out.println( "partners length: " + partnerObj.length() );
		TbarPartner[] partners = new TbarPartner[ partnerObj.length() ];
		for( int i = 0; i < partnerObj.length(); i++ )
		{
			partners[ i ] = TbarPartner.load( partnerObj.getJSONObject( i ) );
		}
		out.partners = partners;

		out.location = new int[ 3 ];
		out.location[ 0 ] = locArray.getInt( 0 );
		out.location[ 1 ] = locArray.getInt( 1 );
		out.location[ 2 ] = locArray.getInt( 2 );

		return out;
	}

	public static TbarCollection loadSerialized( String path )
	{
		if( !path.endsWith( ".ser" ))
		{
			System.err.println("loadSerialized: path must end in .ser");
			return null;
		}
		TbarCollection tbars = null;
		try {
	         FileInputStream fileIn = new FileInputStream( path );
	         ObjectInputStream in = new ObjectInputStream(fileIn);
	         tbars = (TbarCollection) in.readObject();
	         in.close();
	         fileIn.close();
	      }catch(IOException i) {
	         i.printStackTrace();
	         return null;
	      }catch(ClassNotFoundException c) {
	         System.out.println("Employee class not found");
	         c.printStackTrace();
	         return null;
	      }
		return tbars;
	}
	
	public static TbarCollection loadAll( String path )
	{
//		String jsonData = "";
		BufferedReader br = null;
		StringBuffer jsonData = new StringBuffer();
		try {
			String line;
			br = new BufferedReader(new FileReader( path ));
			System.out.println("reading");
			int i = 0;
			while ((line = br.readLine()) != null) {
				jsonData.append( line );
				jsonData.append( '\n' );
				i++;

//				if( i % 10000 == 0 )
//				{
//					System.out.println( "line i: " + i + " out of ~12m" );
//				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		JSONObject obj = new JSONObject(jsonData.toString());
		JSONArray data = obj.getJSONArray("data");
		TbarCollection tbars = TbarPrediction.loadAll( data );
		return tbars;
	}

	public static TbarCollection loadAll( JSONArray tbarArray )
	{
		ArrayList<TbarPrediction> tbars = new ArrayList<TbarPrediction>();
		long[] min = new long[ 3 ];
		long[] max = new long[ 3 ];

		Arrays.fill( min, Long.MAX_VALUE );
		Arrays.fill( max, Long.MIN_VALUE );

		int i = 0;
		for( Object obj : tbarArray )
		{
			TbarPrediction tbp = TbarPrediction.load((JSONObject) obj);
			updateMinMax( min, max, tbp );
			tbars.add( tbp );

			i++;
//			System.out.println( "i: " + i + " out of ~100k" );
//			if( i % 1000 == 0 )
//			{
//				System.out.println( "i: " + i + " out of ~100k" );
//			}
		}
		return new TbarCollection( tbars, min, max );
	}

	public static void updateMinMax( final long[] min, final long[] max, final TbarPrediction tbp )
	{
		for( int d = 0; d < min.length; d++ )
		{
			if( tbp.location[ d ] < min[ d ])
				min[ d ] = tbp.location[ d ];
			
			if( tbp.location[ d ] > max[ d ])
				max[ d ] = tbp.location[ d ];
		}
	}
	
	public static class TbarPartner implements Serializable
	{
		private static final long serialVersionUID = 7342311436224395347L;
	
		public double confidence;
		public int body_ID;
		public int[] location;
		
		public TbarPartner( final double confidence, final int body_ID, final int[] location )
		{
			this.confidence = confidence;
			this.body_ID = body_ID;
			this.location = location;
		}
		public static TbarPartner load( JSONObject obj )
		{
			double confidence = obj.getDouble( "confidence" );
			int body_ID = obj.getInt( "body ID" );
			JSONArray locArray = (JSONArray)obj.get( "location" );
			
			int[] location = new int[ 3 ];
			location[ 0 ] = locArray.getInt( 0 );
			location[ 1 ] = locArray.getInt( 1 );
			location[ 2 ] = locArray.getInt( 2 );

			return new TbarPartner( confidence, body_ID, location );
		}
	}

	public static class TbarCollection implements Serializable
	{
		private static final long serialVersionUID = -1063017232502453517L;

		public final ArrayList<TbarPrediction> list;
		public final long[] min;
		public final long[] max;

		public TbarCollection( 
				final ArrayList<TbarPrediction> list,
				final long[] min,
				final long[] max ){
			this.list = list;
			this.min = min;
			this.max = max;
		}

		@Override
		public String toString()
		{
			System.out.println( "tostring" );
			String out = "Tbars ( " + list.size() + ") ";
			out += Arrays.toString( min ) + " : ";
			out += Arrays.toString( max );
			return out;
		}
		
		public List<DoubleType> getValues( final double thresh )
		{
			return list.stream()
					.map( x -> x.confidence > thresh ? new DoubleType(1) : new DoubleType( 0 ) )
					.collect( Collectors.toList() );
		}
		
		public List<RealPoint> getPoints()
		{
			return list.stream()
					.map( x -> new RealPoint( (double)x.location[0], (double)x.location[1], (double)x.location[2] ))
					.collect( Collectors.toList() );
		}
		
	}

	public static List<RealPoint> transformPoints( final List<RealPoint> pts, final RealTransform xfm )
	{
		return pts.stream().map( x -> transform( x, xfm ) )
			.collect( Collectors.toList() );
	}

	public static RealPoint transform( final RealLocalizable pt, final RealTransform xfm )
	{
		RealPoint out = new RealPoint( pt.numDimensions() );
		xfm.apply( pt, out );
		return out;
	}

	@Override
	public int numDimensions()
	{
		return 3;
	}

	@Override
	public void localize( float[] position )
	{
		position[ 0 ] = (float)location[ 0 ];
		position[ 1 ] = (float)location[ 1 ];
		position[ 2 ] = (float)location[ 2 ];
	}

	@Override
	public void localize( double[] position )
	{
		position[ 0 ] = (double)location[ 0 ];
		position[ 1 ] = (double)location[ 1 ];
		position[ 2 ] = (double)location[ 2 ];
	}

	@Override
	public float getFloatPosition( int d )
	{
		return (float)location[ d ];
	}

	@Override
	public double getDoublePosition( int d )
	{
		return (double)location[ d ];
	}

	@Override
	public double getMaxValue()
	{
		return 1;
	}

	@Override
	public double getMinValue()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getMinIncrement()
	{
		// TODO Auto-generated method stub
		return 0.001;
	}

	@Override
	public int getBitsPerPixel()
	{
		return 0;
	}

	@Override
	public double getRealDouble()
	{
		return confidence > 0.85 ? 50 : 0;
	}

	@Override
	public float getRealFloat()
	{
		return confidence > 0.85 ? 50f : 0f;
	}

	@Override
	public void setReal( float f )
	{
		// TODO Auto-generated method stub
	}

	@Override
	public void setReal( double f )
	{
		// TODO Auto-generated method stub
	}

	@Override
	public TbarPrediction createVariable()
	{
		return null;
	}

	@Override
	public TbarPrediction copy()
	{
		return null;
	}

	@Override
	public boolean valueEquals( TbarPrediction t )
	{
		return confidence == t.confidence;
	}
}
