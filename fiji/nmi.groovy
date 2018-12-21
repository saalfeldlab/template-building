#@Dataset(label="Image A") im1
#@Dataset(label="Image B") im2
#@Float(label="Histogram min", value=0) min
#@Float(label="Histogram max", value=255) max
#@Integer(label="Number of histogram bins", min=1, value=32) nbins

nmi = net.imglib2.algorithm.stats.MutualInformation.nmi( im1, im2, min, max, nbins );
println( nmi )