package org.janelia.saalfeldlab.n5.metadata.transforms;

import org.janelia.saalfeldlab.n5.universe.metadata.N5GenericSingleScaleMetadataParser;

public class DisplacementFieldCoordinateTransform extends N5GenericSingleScaleMetadataParser
{
	public DisplacementFieldCoordinateTransform()
	{
		super("", "", "spacing", "offset", "" , "" );
	}

}
