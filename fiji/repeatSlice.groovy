// @Integer(label="Num Slices", min=1, max=20000, value=1) nSlices

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

imp = IJ.getImage();
proc = imp.getProcessor();
stack = new ImageStack( proc.getWidth(), proc.getHeight() );

1.upto( nSlices ) {
	stack.addSlice( proc );
}

out = new ImagePlus( imp.getTitle()+"_repeated", stack );
out.show()