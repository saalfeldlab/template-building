package io.dfield;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;


import ij.ImagePlus;
import io.nii.NiftiHeader;
import io.nii.NiftiIo;
import loci.formats.FormatException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;

public class NiiDfieldWriter extends AbstractDfieldWriter
{
	public boolean little_endian = false;	// Change this to force little endian output 

	public static final int ANALYZE_7_5 = 0; 
	public static final int NIFTI_ANALYZE = 1; 
	public static final int NIFTI_FILE = 2; 

	private boolean signed16Bit = false;

	public static void main( String[] args ) throws FormatException, IOException
	{
		
		String fin  = "/groups/saalfeld/home/bogovicj/tmp/testDfieldConv/realDfield.nii";
		String fout = "/groups/saalfeld/home/bogovicj/tmp/testDfieldConv/realDfield_out.nii";
		
		ImagePlus ip = NiftiIo.readNifti(new File( fin ));
//		ImagePlus ip = NiftiIo.readNifti(new File("/groups/saalfeld/home/bogovicj/tmp/testDfieldConv/dfield.nii"));
		System.out.println( ip );

		Img<FloatType> dfield = ImageJFunctions.convertFloat( ip );		
		System.out.println( "dfield: " + Util.printInterval( dfield ));

		FileOutputStream outStream = new FileOutputStream( fout );
		NiiDfieldWriter writer = new NiiDfieldWriter();
		writer.unit = "micron";
		writer.pixel_spacing = new double[]{1,1,1};
		
		writer.writeHeader( outStream, dfield );
		
		int n = writeRaw( outStream, dfield );
		outStream.flush();
		outStream.close();
		
		System.out.println( "wrote " + n + " bytes" );
	}

	@Override
	public <T extends RealType<T>> int write(OutputStream out, RandomAccessibleInterval<T> dfield) throws IOException
	{
		// TODO fix this
		return 0;
	}

	/**
	 * The last dimension of the volume stores vector components
	 */
	@Override
	public int vectorDimension()
	{
		return -1;
	}

	public <T extends RealType<T>> int writeHeader( OutputStream out, RandomAccessibleInterval<T> dfield ) throws IOException
	{
		DataOutputStream dout = new DataOutputStream(out);
		writeHeader( dout, dfield, unit, Float.MAX_VALUE, Float.MAX_VALUE, NIFTI_FILE );
		dout.flush();
//		dout.close();
		return dout.size();
	}

	private void writeHeader( DataOutputStream output, 
			RandomAccessibleInterval<?> dfield,
			String unit,
			double min, double max,
			int type ) throws IOException
	{

		/**
		 * TODO only support floats at the moment
		 */
		short datatype = 16; 		// DT_FLOAT 
		short bitsallocated = 32;


		writeInt(output, 348); 				// sizeof_hdr
		int i;
		for (i = 0; i < 10; i++) output.write( 0 );	// data_type
		for (i = 0; i < 18; i++) output.write( 0 ); 	// db_name 
		writeInt(output, 16384); 			// extents 
		output.writeShort( 0); 				// session_error
		output.writeByte ( (int) 'r' );			// regular 
		output.writeByte ( 0 );				// hkey_un	dim_info 

		// image_dimension
		short [] dims = new short[8];
		dims[0] = (short) 5;
			
		dims[1] = (short) dfield.dimension( 0 );
		dims[2] = (short) dfield.dimension( 1 ); 
		dims[3] = (short) dfield.dimension( 2 );
//		/* John Bogovic changed this to place nicely with ANTS */
//		dims[4] = (short) imp.getNChannels();
//		dims[5] = (short) imp.getNFrames();  
		
		/* This was the original code */
		dims[4] = (short) 1;
		dims[5] = (short) 3; 
		
		for (i = 0; i < 8; i++) writeShort( output, dims[i] );		// dim[0-7]

		writeFloat( output, 0.0 );	//		intent_p1
		writeFloat( output, 0.0 );	//		intent_p2
		writeFloat( output, 0.0 );	//		intent_p3
		
		// "displacement intent code" 
		writeShort( output, (short)1007 );
				
		
		writeShort( output, (short) datatype );			// datatype 
		writeShort( output, (short) bitsallocated );	// bitpix
		output.writeShort( 0 ); 						// slice_start 
		
		
		double [] quaterns = new double [ 5 ]; 
		int qform_code = NiftiHeader.NIFTI_XFORM_UNKNOWN;
		int sform_code = NiftiHeader.NIFTI_XFORM_UNKNOWN;
		double qoffset_x = 0.0, qoffset_y = 0.0, qoffset_z = 0.0; 
		for (i=0; i<5; i++) quaterns[i] = 0.0;
		double [][] srow = new double[3][4];
		for (i=0; i<3; i++) srow[i][i] = 1.0;

		
		float [] pixdims = new float[8];
		
		pixdims[0] = 1.0f; // the vector dimension
		pixdims[1] = (float) pixel_spacing[ 0 ];
		pixdims[2] = (float) pixel_spacing[ 1 ];
		pixdims[3] = (float) pixel_spacing[ 2 ];
		pixdims[4] = (float) 0.0;
		
		for (i = 0; i < 8; i++) writeFloat( output, pixdims[i] );	// pixdim[4-7]

		writeFloat( output, (type==NIFTI_FILE) ? 352 : 0 );		// vox_offset 
		double [] coeff = new double[2];
		coeff[0] = 0.0;
		coeff[1] = 1.0;
		
		
		double c_offset = coeff[0];
		if (signed16Bit) { 
			if (coeff[1]!=0.0) c_offset += 32768.0 * coeff[1];	
			else c_offset += 32768.0;	
		}
		if (type==ANALYZE_7_5) { 
			writeFloat( output, 1 );		// roi_scale 
			writeFloat( output, 0 );		// funused1 
			writeFloat( output, 0 );		// funused2 
		} else { 
			writeFloat( output, coeff[1] ); 	// scl_slope
			writeFloat( output, c_offset ); 	// scl_inter

			writeShort( output,  (short) (dims[3]-1) ); 	// slice_end
			output.write( 0 );	 	// slice_code

			byte xyzt_unit = NiftiHeader.UNITS_UNKNOWN;
			if (unit.equals("meter") || unit.equals("metre") || unit.equals("m") ) { 
				xyzt_unit |= NiftiHeader.UNITS_METER;
			} else if (unit.equals("mm")) { 
				xyzt_unit |= NiftiHeader.UNITS_MM;
			} else if (unit.equals("micron")) { 
				xyzt_unit |= NiftiHeader.UNITS_MICRON;
			} else if (unit.equals("um")) { 
				xyzt_unit |= NiftiHeader.UNITS_MICRON;
			}	
			output.write( xyzt_unit ); 			// xyzt_units	
					
		}
		
		writeFloat( output, coeff[0]+(coeff[1]*max) );		// cal_max 
		writeFloat( output, coeff[0]+(coeff[1]*min) );		// cal_min 

		writeFloat( output, 0.0); 		// slice_duration
		writeFloat( output, 0.0); 		// toffset
		writeFloat( output, 0.0 );			// glmax
		writeFloat( output, 0.0 );			// glmin
		

		// data_history 

		if (type==ANALYZE_7_5) { 
			for (i = 0; i < 80; i++) output.write( 0 );		// descrip  
			for (i = 0; i < 24; i++) output.write( 0 );		// aux_file 
		
			output.write(0);											// orient 
			for (i = 0; i < 10; i++) output.write( 0 );		// originator 
			for (i = 0; i < 10; i++) output.write( 0 );		// generated 
			for (i = 0; i < 10; i++) output.write( 0 );		// scannum 
			for (i = 0; i < 10; i++) output.write( 0 );		// patient_id  
			for (i = 0; i < 10; i++) output.write( 0 );		// exp_date 
			for (i = 0; i < 10; i++) output.write( 0 );		// exp_time  
			for (i = 0; i < 3; i++)  output.write( 0 );		// hist_un0
			output.writeInt( 0 );		// views 
			output.writeInt( 0 );		// vols_added 
			output.writeInt( 0 );		// start_field  
			output.writeInt( 0 );		// field_skip
			output.writeInt( 0 );		// omax  
			output.writeInt( 0 );		// omin 
			output.writeInt( 0 );		// smax  
			output.writeInt( 0 );		// smin 
		} else { 
			String desc = new String();
			int length = desc.length(); 
			if (length > 80) { 
				output.writeBytes( desc.substring(0,80) ); 
			} else { 
				output.writeBytes( desc );
				for (i=length; i<80; i++) output.write(0); 
			}

			desc = "";
			length = desc.length(); 
			if (length > 24) { 
				output.writeBytes( desc.substring(0,24) ); 
			} else { 
				output.writeBytes( desc );
				for (i=length; i<24; i++) output.write(0); 
			}
	
			
			sform_code = NiftiHeader.NIFTI_XFORM_SCANNER_ANAT;
			qform_code = NiftiHeader.NIFTI_XFORM_ALIGNED_ANAT;
			srow[0][0] = -1;
			srow[1][1] = -1;
			srow[2][2] = 1;

			writeShort( output, (short) qform_code ); 	// qform_code 
			writeShort( output, (short) sform_code );  	// sform_code 
			writeFloat( output, quaterns[2]); 	// quatern_b	
			writeFloat( output, quaterns[3]); 	// quatern_c 	
			writeFloat( output, quaterns[4]); 	// quatern_d 	
		
			writeFloat( output, qoffset_x ); 	// qoffset_x 
			writeFloat( output, qoffset_y ); 	// qoffset_y  
			writeFloat( output, qoffset_z ); 	// qoffset_z  
		
			for (i=0; i<3; i++) { 
				for (int j=0; j<4; j++) writeFloat( output, srow[i][j]); // srow_x, srow_y, srow_z
			}	
			
			desc =  "";
			length = desc.length(); 
			if (length > 16) { 
				output.writeBytes( desc.substring(0,16) ); 
			} else { 
				output.writeBytes( desc );
				for (i=length; i<16; i++) output.write(0); 
			}

			output.writeBytes( (type==NIFTI_ANALYZE) ? "ni1\0" : "n+1\0" ); 
			if (type==NIFTI_FILE) output.writeInt( 0 ); 	// extension	
		}
		
	}
	
	private void writeFloat(DataOutputStream input, float value) throws IOException {
		writeInt(input, Float.floatToIntBits( value ) ); 
	}
	private void writeFloat(DataOutputStream input, double value) throws IOException {
		writeFloat(input, (float) value ); 
	}

	private void writeInt(DataOutputStream input, int value) throws IOException {
		if (little_endian) { 
			byte b1 = (byte) (value & 0xff);
			byte b2 = (byte) ((value >> 8) & 0xff);
			byte b3 = (byte) ((value >> 16) & 0xff);
			byte b4 = (byte) ((value >> 24) & 0xff); 
			input.writeByte(b1);
			input.writeByte(b2);
			input.writeByte(b3);
			input.writeByte(b4);
		} else { 
			input.writeInt( value );  
		}
	}
	
	private void writeShort(DataOutputStream input, short value) throws IOException {
		if (little_endian) { 
			byte b1 = (byte) (value & 0xff);
			byte b2 = (byte) ((value >> 8) & 0xff);
			input.writeByte(b1);
			input.writeByte(b2);
		} else { 	
			input.writeShort( value ); 
		}
	}
}

