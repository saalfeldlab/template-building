#@ File (label="Container path", style="both") n5H5ZarrContainer
#@ String (label="Dataset") dataset
#@ Boolean (label="Unquantize", value=true) unquantize

import net.imglib2.*;
import net.imglib2.converter.*;
import net.imglib2.util.*;
import net.imglib2.img.display.imagej.*;
import org.janelia.saalfeldlab.n5.ij.*;
import org.janelia.saalfeldlab.n5.imglib2.*;
import net.imglib2.type.numeric.integer.*;
import net.imglib2.type.numeric.real.*;

n5 = new N5Factory().openReader( n5H5ZarrContainer.getAbsolutePath());

m = n5.getAttribute(dataset, N5DisplacementField.MULTIPLIER_ATTR, Double.class);
println( "multiplier: " + m );

src = N5Utils.open(n5, dataset );

class QuantConverter implements Converter<ShortType,FloatType>
{
	double m;
	QuantConverter(double m) { this.m = m; }

	void convert( ShortType input, FloatType output) {
		output.setReal( input.getRealDouble() * m );
	}
}

src_perm = N5DisplacementField.vectorAxisLast(src);
if( unquantize )
	src_converted = Converters.convertRAI(src_perm, new QuantConverter(m), new FloatType());
else
	src_converted = Converters.convertRAI(src_perm, new QuantConverter(1.0), new FloatType());

ImageJFunctions.wrap( src_converted, "src_conv" ).show()