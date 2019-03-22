#!/bin/bash
        
thisdir=$(pwd)

# The forward transform
fwd="JRC2018F_FCWB_verysmall_fwd.nrrd"

# The estimated inverse trasnform
inv_it="JRC2018F_FCWB_verysmall_invITERATIVE-EST.nrrd"

# Compute the inverse numerically
transform2Dfield \
    -t "$thisdir/$fwd?invopt;tolerance=0.001;maxIters=1000?i" \
    -o $thisdir/$inv_it \
    -r 7.04,7.04,7.04 \
    -s 3,90,42,26 \


# Evaluatethe results
pts="testPts_verysmall.csv"
ptsxfm="testPts_verysmall_xfm.csv" # transformed points


# Generate a list of points in the field of view
pointSamples 14,14,14:614,274,164 10,10,10 > $thisdir/$pts

# Apply both transformations
#   the result should be close to the original points
transformPoints \
    -i "$thisdir/$pts" \
    -o $thisdir/$ptsxfm \
    -t $thisdir/$fwd \
    -t $thisdir/$inv_it \
    -d ','

# See how close the transformed points are to the originals
tDiff="testPts_verysmall_diffmag.csv"
pointDifference $thisdir/$pts $thisdir/$ptsxfm $thisdir/$ptDiff -d ',' --mag

echo " "
maxAndMean.awk $ptDiff
