package io;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.measure.Calibration;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import sc.fiji.io.Nrrd_Reader;
import sc.fiji.io.Nrrd_Writer;

public class WriteNrrdDisplacementField {

	public static void main(String[] args) throws FormatException, IOException
	{
		String imF = args[ 0 ];
		String fout = args[ 1 ];

		String encoding = "raw";
		if( args.length >= 3 )
		{
			encoding = args[ 2 ];
		}
		
		ImagePlus baseIp = null;
		if( imF.endsWith( "nii" ))
		{
			try
			{
				baseIp =  NiftiIo.readNifti( new File( imF ) );
			} catch ( FormatException e )
			{
				e.printStackTrace();
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if( imF.endsWith( "nrrd" ))
		{
			Nrrd_Reader nr = new Nrrd_Reader();
			File imFile = new File( imF );
			
			baseIp = nr.load( imFile.getParent(), imFile.getName());
			System.out.println( "baseIp");
		}
		else
		{
			baseIp = IJ.openImage( imF );
		}

		System.out.println("ip: " + baseIp);
		writeImage( baseIp, encoding, new File( fout ));
	}

	public static void writeImage(final ImagePlus ip, final String nrrdEncoding, final File outFile)
			throws IOException
	{
		FileInfo fi = ip.getFileInfo();

		String nrrdHeader = makeDisplacementFieldHeader(ip);
		if( nrrdHeader == null )
		{
			System.err.println("Failed");
			return;
		}
		
		FileOutputStream out = new FileOutputStream( outFile );

		// First write out the full header
		Writer bw = new BufferedWriter(new OutputStreamWriter(out));
		// Note, right now this is the only way compression that is implemented
		if(nrrdEncoding.equals("gzip"))
			fi.compression=GZIP;

		// Blank line terminates header
		bw.write( nrrdHeader + "\n" );
		// Flush rather than close
		bw.flush();		

		// Then the image data
		if(nrrdEncoding.equals("gzip")) {
			GZIPOutputStream zStream = new GZIPOutputStream(new BufferedOutputStream( out ));
			dumpFloatImg( ip, zStream );
			zStream.flush();
			zStream.close();
		} else {
			dumpFloatImg( ip, out );
			out.flush();
			out.close();
		}
	}

	public static void dumpFloatImg( final ImagePlus ip, final OutputStream out ) throws IOException
	{
		DataOutputStream dout = new DataOutputStream( out );

		final Img<FloatType> img = ImageJFunctions.wrapFloat( ip );
		IntervalView<FloatType> img_perm = Views.permute( Views.permute( Views.permute(img, 0, 3 ), 1, 3 ), 2, 3 );

		Cursor<FloatType> c = Views.flatIterable( img_perm ).cursor();
		while( c.hasNext() )
		{
			c.fwd();
			dout.writeFloat( c.get().getRealFloat() );
		}
		dout.flush();
		dout.close();

		System.out.println( "Wrote  " + dout.size() + " bytes." );
	}

	public static String makeDisplacementFieldHeader( ImagePlus ip )
	{
		System.out.println("Nrrd_Writer makeDisplacementFieldHeader");
		
		FileInfo fi = ip.getFileInfo();
		Calibration cal = ip.getCalibration();
		
		int dimension = ip.getNDimensions();
		
		if( dimension != 4 )
			return null;

		boolean channels = true;
		if( ip.getNChannels() == 3 && ip.getNFrames() == 1)
		{
			channels = true;
		}
		else if( ip.getNChannels() == 1 && ip.getNFrames() == 3 )
		{
			channels = false;
		}
		else
		{
			System.err.println("Either NChannels or NFrames must equal 3");
			return null;
		}
		
		StringWriter out=new StringWriter();
		out.write("NRRD0004\n");
		out.write("# Created by NrrdDisplacementFieldWriter at "+(new Date())+"\n");

		// Fetch and write the data type
		out.write("type: "+Nrrd_Writer.imgType(fi.fileType)+"\n");
		
		// write dimensionality
		out.write("dimension: "+dimension+"\n");
		
		// Fetch and write the encoding
		out.write("encoding: "+Nrrd_Writer.getEncoding(fi)+"\n");

		out.write("space: right-anterior-superior\n");
		out.write(dimmedLine("sizes",dimension,"3",fi.width+"",fi.height+"",ip.getNSlices()+""));
		
		if(cal!=null){
		    //out.write("space dimension"+dimension+"\n");
			out.write(dimmedLine("space directions", dimension, "none",
					"("+cal.pixelWidth+",0,0)","(0,"+cal.pixelHeight+",0)","(0,0,"+cal.pixelDepth+")"));
		}
		
		out.write("kinds: vector domain domain domain\n");
		out.write("labels: \"Vx;Vy;Vz\" \"x\" \"y\" \"z\"\n");
		
		if(fi.intelByteOrder) out.write("endian: little\n");
		else out.write("endian: big\n");
		
		out.write("space origin: (0,0,0)\n");

		return out.toString();
	}

	private static String dimmedLine(String tag,int dimension,String x1,String x2,String x3,String x4) {
		String rval=null;
		if(dimension==2) rval=tag+": "+x1+" "+x2+"\n";
		else if(dimension==3) rval=tag+": "+x1+" "+x2+" "+x3+"\n";
		else if(dimension==4) rval=tag+": "+x1+" "+x2+" "+x3+" "+x4+"\n";
		return rval;
	}	
	
	// Additional compression modes for fi.compression
	public static final int GZIP = 1001;
	public static final int ZLIB = 1002;
	public static final int BZIP2 = 1003;
	
	// Additional file formats for fi.fileFormat
	public static final int NRRD = 1001;
	public static final int NRRD_TEXT = 1002;
	public static final int NRRD_HEX = 1003;
}
