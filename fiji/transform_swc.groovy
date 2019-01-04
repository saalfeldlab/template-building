#@File(label="Swc file") swcFile
#@File(label="Output Swc file") fileOut
#@String(label="Transformation file path list") xfmPathList

import evaluation.TransformSwc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import process.RenderTransformed;
import sc.fiji.skeleton.SwcIO;
import sc.fiji.skeleton.SWCPoint;
import tracing.SNT;


InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
boolean invert = false;
for( p in (xfmPathList.split( "\\s+" )) )
{
	if( p.equals( "-i" ))
	{
		invert = true;
	}
	else
	{
		totalXfm.add( RenderTransformed.loadTransform( p, invert ) );
		invert = false;
	}
}

res = TransformSwc.transformSWC( totalXfm, SwcIO.loadSWC( swcFile ) );
try
{
	pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileOut), "UTF-8"));
	TransformSwc.flushSWCPoints( res, pw);
}
catch (final IOException ioe)
{
	System.err.println("Saving to " + fileOut + " failed");
}
