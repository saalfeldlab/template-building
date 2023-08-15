package io;

import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Exception;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;


public class BuildH5Test
{

	public static void main( String[] args )
	{
		System.out.println( TransformFileParseTest.TEST_H5_FILE );
		
		try
		{
			N5HDF5Writer writer = new N5HDF5Writer( TransformFileParseTest.TEST_H5_FILE, 1, 1, 1, 1 );
			writer.createDataset(
					TransformFileParseTest.TEST_H5_DATASET_A + "/dfield", new long[] { 1, 1, 1, 1 }, new int[] { 1, 1, 1, 1 }, DataType.UINT8, new GzipCompression() );
			writer.createDataset(
					TransformFileParseTest.TEST_H5_DATASET_A + "/invdfield", new long[] { 1, 1, 1, 1 }, new int[] { 1, 1, 1, 1 }, DataType.UINT8, new GzipCompression() );
			writer.createDataset(
					TransformFileParseTest.TEST_H5_DATASET_B + "/dfield", new long[] { 1, 1, 1, 1 }, new int[] { 1, 1, 1, 1 }, DataType.UINT8, new GzipCompression() );
			writer.createDataset(
					TransformFileParseTest.TEST_H5_DATASET_B + "/invdfield", new long[] { 1, 1, 1, 1 }, new int[] { 1, 1, 1, 1 }, DataType.UINT8, new GzipCompression() );

			writer.close();
		}
		catch ( final N5Exception e )
		{
			e.printStackTrace();
		}

	}

}
