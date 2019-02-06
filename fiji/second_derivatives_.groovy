//@Dataset(label="dataset") img
//@Integer(label="dimension") dim
//@Integer(label="num threads") ntasks
//@UIService ui

import java.util.concurrent.*;

import net.imglib2.view.*;
import net.imglib2.util.*;
import net.imglib2.img.array.*;
import net.imglib2.algorithm.gradient.*

println( img )

exec = Executors.newFixedThreadPool( ntasks );
dims = Intervals.dimensionsAsLongArray( img );

d1 = img.duplicateBlank()
d1.setName( "d1_"+dim )

PartialDerivative.gradientCentralDifferenceParallel( Views.extendBorder( img ), d1, dim, ntasks, exec )
ui.show( d1 )

d2 = img.duplicateBlank()
d2.setName( "d2_"+dim )
PartialDerivative.gradientCentralDifferenceParallel( Views.extendBorder( d1 ), d2, dim, ntasks, exec )
ui.show( d2 )