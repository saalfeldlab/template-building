#!/urs/bin/python

import sys
import numpy as np
from numpy import float32, int32, uint8, dtype, genfromtxt

N = len( sys.argv )

datPath = sys.argv[ 1 ]
dat = genfromtxt( datPath )
#print( dat )

print( "MEDIAN %f" % ( np.median(dat) ))
print( "MEAN %f" % ( np.mean(dat) ))
print( "STDDEV %f" % ( np.std(dat) ))
print( "MIN %f" % ( np.min(dat) ))
print( "MAX %f" % ( np.max(dat) ))
