#@File(label="Swc file") swcFile
#@File(label="Output Swc file") fileOut
#@String(label="Transformation file path list") xfmPathListString

import java.util.List;
import evaluation.TransformSwc;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;

import org.janelia.saalfeldlab.swc.Swc;
import org.janelia.saalfeldlab.swc.SwcPoint;
import org.janelia.saalfeldlab.transform.io.TransformReader;


xfmPathList = xfmPathListString.split("\\s") as List

RealTransformSequence totalXfm = new RealTransformSequence();
if ( xfmPathList == null || xfmPathList.size() < 1 )
	totalXfm.add( new AffineTransform3D() );
else
	totalXfm = TransformReader.readTransforms( xfmPathList );

res = TransformSwc.transformSWC( totalXfm, Swc.read( swcFile ));
Swc.write( res, fileOut );