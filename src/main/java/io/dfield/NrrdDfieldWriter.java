package io.dfield;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Date;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

public class NrrdDfieldWriter extends AbstractDfieldWriter
{
	private final String DIM_ORDER=  "VXYZ";
	
	private String encoding = "raw";
	private String type = "float";
	
	public void setType( final String type )
	{
		if( type.equals("float"))
			this.type = type;
		else if( type.equals("double"))
			this.type = type;
		else
			System.err.println("type must be \"float\" or \"double\"");
	}

	public void setEncoding( final String encoding )
	{
		if( encoding.equals("raw"))
			this.encoding = encoding;
		else if( type.equals("gzip"))
			this.encoding = encoding;
		else
			System.err.println("type must be \"raw\" or \"gzip\"");
	}
	


	public String buildHeader(
			RandomAccessibleInterval<?> dfield )
	{
		if( pixel_spacing == null )
			pixel_spacing = new double[]{1,1,1};

		return buildHeader(
				dfield,
				type, encoding, little_endian,
				new long[]{ dfield.dimension(0),
						 	dfield.dimension(1),
						 	dfield.dimension(2)},
				pixel_spacing,
				new long[]{1,1,1} );
	}
	
	public <T extends RealType<T>> int writeHeader( OutputStream out, RandomAccessibleInterval<T> dfield ) throws IOException
	{
		DataOutputStream dout = new DataOutputStream( out );
		dout.writeUTF( buildHeader( dfield ));
		dout.flush();
		dout.close();
		return dout.size();
	}

	@Override
	public int vectorDimension()
	{
		return 0;
	}
	
	@Override
	public <T extends RealType<T>> int write( OutputStream out, RandomAccessibleInterval<T> dfield ) throws IOException
	{
		DataOutputStream dout = new DataOutputStream( out );
		Cursor<T> c = Views.flatIterable( dfield ).cursor();
		while( c.hasNext() )
		{
			c.fwd();
			dout.writeFloat( c.get().getRealFloat() );
		}
		dout.flush();
		dout.close();

		return dout.size();
	}

	private static String dimmedLine(String tag,int dimension,String x1,String x2,String x3,String x4) {
		String rval=null;
		if(dimension==2) rval=tag+": "+x1+" "+x2+"\n";
		else if(dimension==3) rval=tag+": "+x1+" "+x2+" "+x3+"\n";
		else if(dimension==4) rval=tag+": "+x1+" "+x2+" "+x3+" "+x4+"\n";
		return rval;
	}

	public static String buildHeader(
			RandomAccessibleInterval<?> dfield,
			String type,
			String encoding,
			boolean little_endian,
			long[] size,
			double[] pixel_spacing,
			long[] subsample_factors )
	{
		System.out.println("Nrrd_Writer makeDisplacementFieldHeader");
		int dimension = dfield.numDimensions();
		if( dimension != 4 )
			return null;
		
		StringWriter out=new StringWriter();
		out.write("NRRD0004\n");
		out.write("# Created by NrrdDisplacementFieldWriter at "+(new Date())+"\n");

		// Fetch and write the data type
		out.write("type: "+ type +"\n");
		
		// write dimensionality
		out.write("dimension: "+dimension+"\n");
		
		// Fetch and write the encoding
		out.write("encoding: "+ encoding +"\n");

		out.write("space: right-anterior-superior\n");


		if( subsample_factors != null )
		{
		out.write(dimmedLine("sizes",dimension,"3",
				size[0] / subsample_factors[0]+"",
				size[1] / subsample_factors[1]+"",
				size[2] / subsample_factors[2]+""));
			
	    //out.write("space dimension"+dimension+"\n");
		out.write(dimmedLine("space directions", dimension, "none",
				"("+subsample_factors[0] * pixel_spacing[0]+",0,0)",
				"(0,"+subsample_factors[1] * pixel_spacing[1]+",0)",
				"(0,0,"+subsample_factors[2] * pixel_spacing[2]+")"));
		}
		else
		{
			out.write(dimmedLine("sizes",dimension,
					size[0] + "",
					size[1] + "",
					size[2] + "",
					size[3] + ""));

		    //out.write("space dimension"+dimension+"\n");
			out.write(dimmedLine("space directions", dimension, "none",
					"("+ pixel_spacing[0]+",0,0)",
					"(0,"+ pixel_spacing[1]+",0)",
					"(0,0,"+ pixel_spacing[2]+")"));
		}
		
		out.write("kinds: vector domain domain domain\n");
		out.write("labels: \"Vx;Vy;Vz\" \"x\" \"y\" \"z\"\n");
		
		if( little_endian ) out.write("endian: little\n");
		else out.write("endian: big\n");
		
		out.write("space origin: (0,0,0)\n");

		return out.toString();
	}

}
