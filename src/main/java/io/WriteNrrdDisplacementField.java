package io;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.zip.GZIPOutputStream;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.measure.Calibration;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ants.ANTSLoadAffine;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.GenericComposite;

import sc.fiji.io.Dfield_Nrrd_Reader;
import sc.fiji.io.Dfield_Nrrd_Writer;

public class WriteNrrdDisplacementField {

	public static class Options implements Serializable
	{

		private static final long serialVersionUID = -5666039337474416226L;

		@Option( name = "-d", aliases = {"--dfield"}, required = true, usage = "" )
		private String field;
		
		@Option( name = "-o", aliases = {"--output"}, required = true, usage = "" )
		private String output;

		@Option( name = "-i", aliases = {"--inverse"}, required = false, usage = "" )
		private boolean isInverse = false;
		
		@Option( name = "-a", aliases = {"--affine"}, required = false, usage = "" )
		private String affine = "";
		
		@Option( name = "-e", aliases = {"--encoding"}, required = false, usage = "" )
		private String encoding = "raw";
		
		@Option( name = "--affineFirst", required = false, usage = "" )
		private boolean isAffineFirst = false;
		

		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);
			} catch (final CmdLineException e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}

		/**
		 * @return is this an inverse transform
		 */
		public boolean isInverse()
		{
			return isInverse;
		}
		
		/**
		 * @return is this an inverse transform
		 */
		public boolean affineFirst()
		{
			return isAffineFirst;
		}

		/**
		 * @return output path
		 */
		public String getOutput()
		{
			return output;
		}
		
		/**
		 * @return output path
		 */
		public String getField()
		{
			return field;
		}

		/**
		 * @return affine File
		 */
		public String getAffine() {
			return affine;
		}
		
		/**
		 * @return nrrd encoding
		 */
		public String getEncoding() {
			return encoding;
		}
	}
	
	public static void main(String[] args) throws FormatException, IOException
	{
		final Options options = new Options(args);

		String imF = options.getField();
		String fout = options.getOutput();
		String affineFile = options.getAffine();
		String nrrdEncoding = options.getEncoding(); 
		boolean invert = options.isInverse();
		boolean affineFirst = options.affineFirst();

		
		AffineGet affine = null;
		if( !affineFile.isEmpty() )
			affine = loadAffine( affineFile, invert );

		if( imF.endsWith("h5"))
		{
			System.out.println( "N5" );
			N5Reader n5 = new N5HDF5Reader( imF, 3, 32, 32, 32 );
			writeImage( n5, affine, affineFirst, nrrdEncoding, new File( fout ));
			return;
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
			// This will never work since the Nrrd_Reader can't handle 4d volumes, actually
			Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
			File imFile = new File( imF );
			baseIp = nr.load( imFile.getParent(), imFile.getName());
		}
		else
		{
			baseIp = IJ.openImage( imF );
		}

//		final long[] subsample_factors = new long[]{ 4, 4, 4, 1 };
		final long[] subsample_factors = new long[]{ 1, 1, 1, 1 };

		System.out.println("ip: " + baseIp);
		writeImage( baseIp, affine, affineFirst, nrrdEncoding, 
				subsample_factors, new File( fout ));
	}
	
	@SuppressWarnings("unchecked")
	public static void writeImage(final N5Reader n5, final AffineGet affine, 
			final boolean affineFirst, final String nrrdEncoding,
			final File outFile )
			throws IOException
	{
		long[] subsample_factors = null;
		RandomAccessibleInterval<?> img = N5Utils.open(n5, "/dfield");
		
		DatasetAttributes attr = n5.getDatasetAttributes("dfield");
		DataType type = attr.getDataType();
		
		HashMap<String, Object> attrMap = n5.getDatasetAttributes("dfield").asMap();
		
		
		double[] spacing;
		if( attrMap.containsKey("spacing"))
			spacing= (double[])attrMap.get("spacing");
		else
			spacing = new double[]{ 1, 1, 1 };
		
		double m = 1;
		if( attrMap.containsKey("multiplier"))
			m = (double)(attrMap.get("multiplier"));

		System.out.println( "multiplier: " + m );
		
		RandomAccessibleInterval<FloatType> write_me = null;
		if( type.equals( DataType.INT16 ))
		{
			write_me = toFloat( (RandomAccessibleInterval<ShortType>)img, m );
		}
		else if( type.equals( DataType.FLOAT32 ) )
		{
			write_me = (RandomAccessibleInterval<FloatType>)img;
		}
		else
		{
			System.err.println("CURRENTLY ONLY WORKS FOR FLOAT AND SIGNED-SHORT");
			return;
		}

		FileOutputStream out = new FileOutputStream( outFile );

		// First write out the full header
		Writer bw = new BufferedWriter(new OutputStreamWriter(out));
		
		long[] dims = new long[img.numDimensions()];
		img.dimensions(dims);
		
		String nrrdHeader = makeDisplacementFieldHeader(
				img.numDimensions(), 
				"float", "raw", true,
				dims, spacing, subsample_factors );
		
		// Blank line terminates header
		bw.write( nrrdHeader + "\n" );
		// Flush rather than close
		bw.flush();		

		OutputStream dataStream;
		if(nrrdEncoding.equals("gzip")) {
			dataStream = new GZIPOutputStream(new BufferedOutputStream( out ));
		}
		else
		{
			dataStream = out;
		}
		
		if( affine == null )
		{
			dumpFloatImg( write_me, subsample_factors, false, dataStream );
		}
		else
		{
			dumpFloatImg( write_me, affine, affineFirst, spacing, dataStream );
		}
	}
	
	public static <T extends RealType<T>> RandomAccessibleInterval<FloatType> toFloat( RandomAccessibleInterval<T> img, double m )
	{
		return Converters.convert(
				img, 
				new Converter<T, FloatType>()
				{
					@Override
					public void convert( T input, FloatType output) {
						output.setReal( input.getRealDouble() * m );
					}
				}, 
				new FloatType());
	}

	public static void writeImage(final ImagePlus ip, final AffineGet affine, 
			final boolean affineFirst, final String nrrdEncoding, 
			final long[] subsample_factors,
			final File outFile )
			throws IOException
	{
		FileInfo fi = ip.getFileInfo();


		String nrrdHeader = makeDisplacementFieldHeader( ip, subsample_factors, nrrdEncoding );
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

			if( affine != null )
				dumpFloatImg( ip, affine, affineFirst,  zStream );
			else
				dumpFloatImg( ip, subsample_factors, true, zStream );
			
			zStream.flush();
			zStream.close();
		} else {
			if( affine != null )
				dumpFloatImg( ip, affine, affineFirst, out );
			else
				dumpFloatImg( ip, subsample_factors, true, out );

			out.flush();
			out.close();
		}
	}
	
	public static AffineGet loadAffine( String filePath, boolean invert ) throws IOException
	{
		if( filePath.endsWith( "mat" ))
		{
			try
			{
				AffineTransform3D xfm = ANTSLoadAffine.loadAffine( filePath );
				if( invert )
				{
					System.out.println("inverting");
					System.out.println( "xfm: " + xfm );
					return xfm.inverse().copy();
				}
				return xfm;
			} catch ( IOException e )
			{
				e.printStackTrace();
			}
		}
		else if( filePath.endsWith( "txt" ))
		{
			if( Files.readAllLines( Paths.get( filePath ) ).get( 0 ).startsWith( "#Insight Transform File" ))
			{
				System.out.println("Reading itk transform file");
				try
				{
					AffineTransform3D xfm = ANTSLoadAffine.loadAffine( filePath );
					if( invert )
					{
						System.out.println("inverting");
						return xfm.inverse().copy();
					}
					return xfm;
				} catch ( IOException e )
				{
					e.printStackTrace();
				}
			}
			else
			{
				System.out.println("Reading imglib2 transform file");
				try
				{
					AffineTransform xfm = AffineImglib2IO.readXfm( 3, new File( filePath ) );
					System.out.println( Arrays.toString(xfm.getRowPackedCopy() ));
					if( invert )
					{
						System.out.println("inverting");
						return xfm.inverse().copy();
					}
					return xfm;
				} catch ( IOException e )
				{
					e.printStackTrace();
				}
			}
		}

		return null;
	}
	
	/**
	 * Expects a 
	 * @param img
	 * @param subsample_factors
	 * @param permute
	 * @param out
	 * @throws IOException
	 */
	public static void dumpFloatImg(
			final RandomAccessibleInterval<FloatType> img,
			final long[] subsample_factors,
			final boolean permute,
			final OutputStream out ) throws IOException
	{
		RandomAccessibleInterval<FloatType> imgToPermute;
		DataOutputStream dout = new DataOutputStream( out );
		
		if( subsample_factors != null )
		{
			imgToPermute = Views.subsample( img, subsample_factors );
		}
		else
		{
			imgToPermute = img;
		}
		
		RandomAccessibleInterval<FloatType> img_perm;
		if( permute )
			img_perm = Views.permute( Views.permute( Views.permute( imgToPermute, 0, 3 ), 1, 3 ), 2, 3 );
		else
			img_perm = imgToPermute;
			
		Cursor<FloatType> c = Views.flatIterable( img_perm ).cursor();
		while( c.hasNext() )
		{
			c.fwd();
			//System.out.println( Util.printCoordinates( c ));
			dout.writeFloat( c.get().getRealFloat() );
		}
		dout.flush();
		dout.close();
	}

	public static void dumpFloatImg(
			final ImagePlus ip,
			final long[] subsample_factors,
			final boolean permute,
			final OutputStream out ) throws IOException
	{
		final Img<FloatType> img = ImageJFunctions.wrapFloat( ip );
		dumpFloatImg(img, subsample_factors, permute, out );
	}

	public static void dumpFloatImg( final ImagePlus ip, final AffineGet affine,
			final boolean affineFirst,
			final OutputStream out ) throws IOException
	{
		final Img<FloatType> img = ImageJFunctions.wrapFloat( ip );
		double[] pixelSpacing = new double[]{ 
				ip.getCalibration().pixelWidth,
				 ip.getCalibration().pixelHeight,
				 ip.getCalibration().pixelDepth
		};
		
		dumpFloatImg( img, affine, affineFirst, pixelSpacing, out );
	}
	
	public static void dumpFloatImg( final RandomAccessibleInterval<FloatType> img, 
			final AffineGet affine,
			final boolean affineFirst, final double[] pixelSpacing,
			final OutputStream out ) throws IOException
	{
		DataOutputStream dout = new DataOutputStream( out );
		CompositeIntervalView<FloatType, ? extends GenericComposite<FloatType>> img_col = Views.collapse( img );

		AffineTransform3D pix2Physical = new AffineTransform3D();
		pix2Physical.set( pixelSpacing[0], 0, 0 );
		pix2Physical.set( pixelSpacing[1], 1, 1 );
		pix2Physical.set( pixelSpacing[2], 2, 2 );
		
		RealPoint pix = new RealPoint( 3 );
		RealPoint p = new RealPoint( 3 );
		RealPoint q = new RealPoint( 3 );
		RealPoint result = new RealPoint( 3 );

		Cursor<? extends GenericComposite<FloatType>> c = Views.flatIterable( img_col ).cursor();
		while( c.hasNext() )
		{
			c.fwd();
			
			pix.setPosition( c.getIntPosition( 0 ), 0 );
			pix.setPosition( c.getIntPosition( 1 ), 1 );
			pix.setPosition( c.getIntPosition( 2 ), 2 );
			
//			if( c.getIntPosition(0) == 100 && 
//				c.getIntPosition(1) == 30 &&./x
//				c.getIntPosition(2) == 40 )
//			{
//				System.out.println("HERE");
//			}

			pix2Physical.apply( pix, p );
			
			if( affineFirst )
			{
				affine.apply( p, q );
				displacePoint( q, c.get(), result );
			}
			else
			{
				displacePoint( p, c.get(), q );
				affine.apply( q, result );
			}
			
			// the point 'q' must hold
			getDisplacement( p, result, q );
			
			dout.writeFloat( q.getFloatPosition( 0 ) );
			dout.writeFloat( q.getFloatPosition( 1 ) );
			dout.writeFloat( q.getFloatPosition( 2 ) );
		}
		dout.flush();
		dout.close();
		
		System.out.println( "Wrote  " + dout.size() + " bytes." );
	}

	/**
	 * Only works for 3D 
	 * @param p must be 3D
	 * @param destination
	 * @param vec
	 */
	public static <C extends GenericComposite<FloatType>> void displacePoint( RealPoint p, C vec , RealPositionable destination)
	{
		destination.setPosition( p.getDoublePosition(0) + vec.get(0).getRealDouble(), 0 );
		destination.setPosition( p.getDoublePosition(1) + vec.get(1).getRealDouble(), 1 );
		destination.setPosition( p.getDoublePosition(2) + vec.get(2).getRealDouble(), 2 );
	}
	
	/**
	 * Only works for 3D 
	 * @param p must be 3D
	 * @param destination
	 * @param vec
	 */
	public static <C extends GenericComposite<FloatType>> void getDisplacement( 
			RealLocalizable currentPosition, RealLocalizable destinationPosition, RealPositionable output )
	{
//		output.setPosition( currentPosition.getDoublePosition(0) - destinationPosition.getDoublePosition(0), 0 );
//		output.setPosition( currentPosition.getDoublePosition(1) - destinationPosition.getDoublePosition(1), 1 );
//		output.setPosition( currentPosition.getDoublePosition(2) - destinationPosition.getDoublePosition(2), 2 );
		output.setPosition( destinationPosition.getDoublePosition(0) - currentPosition.getDoublePosition(0), 0 );
		output.setPosition( destinationPosition.getDoublePosition(1) - currentPosition.getDoublePosition(1), 1 );
		output.setPosition( destinationPosition.getDoublePosition(2) - currentPosition.getDoublePosition(2), 2 );
	}

	public static String makeDisplacementFieldHeader( 
			int dimension,
			String type,
			String encoding,
			boolean little_endian,
			long[] size,
			double[] pixel_spacing,
			long[] subsample_factors )
	{
		System.out.println("Nrrd_Writer makeDisplacementFieldHeader");
		
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
	
	public static String makeDisplacementFieldHeader( ImagePlus ip, long[] subsample_factors, String encoding )
	{
		System.out.println("Nrrd_Writer makeDisplacementFieldHeader");
		
		FileInfo fi = ip.getFileInfo();
		Calibration cal = ip.getCalibration();
		
		int dimension = ip.getNDimensions();
		
		if( dimension != 4 )
			return null;

		System.out.println( "nrrd nc: " + ip.getNChannels() );
		System.out.println( "nrrd nz: " + ip.getNSlices() );
		System.out.println( "nrrd nt: " + ip.getNFrames() );

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
		out.write("type: "+Dfield_Nrrd_Writer.imgType(fi.fileType)+"\n");
		
		// write dimensionality
		out.write("dimension: "+dimension+"\n");
		
		// Fetch and write the encoding
		out.write("encoding: "+ encoding +"\n");

		out.write("space: right-anterior-superior\n");
		out.write(dimmedLine("sizes",dimension,"3",
				fi.width/subsample_factors[0]+"",
				fi.height/subsample_factors[1]+"",
				ip.getNSlices()/subsample_factors[2]+""));

		if(cal!=null){
		    //out.write("space dimension"+dimension+"\n");
			out.write(dimmedLine("space directions", dimension, "none",
					"("+subsample_factors[0]*cal.pixelWidth+",0,0)",
					"(0,"+subsample_factors[1]*cal.pixelHeight+",0)",
					"(0,0,"+subsample_factors[2]*cal.pixelDepth+")"));
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
