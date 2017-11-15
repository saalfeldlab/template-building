#!/urs/bin/python

import sys
import numpy as np
from numpy import float32, int32, uint8, dtype, genfromtxt

N = len( sys.argv )

datPath = sys.argv[ 1 ]
dat = genfromtxt( datPath, delimiter=',' )
#print( dat )

labels = np.unique( dat[:,0] )

for l in labels:
    i = (dat[:,0] == l)
    print( "%d COUNT %f" % ( l, i.shape[0] ))
    print( "%d MEDIAN %f" % ( l, np.median( dat[i,1] )))
    print( "%d MEAN %f" % ( l, np.mean( dat[i,1] )))
    print( "%d STDDEV %f" % ( l, np.std( dat[i,1] )))
    print( "%d MIN %f" % ( l, np.min( dat[i,1] )))
    print( "%d MAX %f" % ( l, np.max( dat[i,1] )))
