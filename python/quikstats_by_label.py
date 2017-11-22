#!/urs/bin/python

import sys
import numpy as np
from numpy import float32, int32, uint8, dtype, genfromtxt

N = len( sys.argv )
datPath = sys.argv[ 1 ]

doAll = False
if N > 2:
    if sys.argv[ 2 ] == 'all':
        doAll = True

dat = genfromtxt( datPath, delimiter=',' )
##print( dat )

if doAll:
    print( "group by labels")
    print( "all COUNT %f" % ( dat.shape[0] ))
    print( "all MEDIAN %f" % ( np.median( dat[:,1] )))
    print( "all MEAN %f" % ( np.mean( dat[:,1] )))
    print( "all STDDEV %f" % ( np.std( dat[:,1] )))
    print( "all MIN %f" % ( np.min( dat[:,1] )))
    print( "all MAX %f" % ( np.max( dat[:,1] )))
else:
    labels = np.unique( dat[:,0] )

    for l in labels:
        i = (dat[:,0] == l)
        x = dat[i,1]
        print( "%d COUNT %f" % ( l, x.shape[0] ))
        print( "%d MEDIAN %f" % ( l, np.median( x )))
        print( "%d MEAN %f" % ( l, np.mean( x )))
        print( "%d STDDEV %f" % ( l, np.std( x )))
        print( "%d MIN %f" % ( l, np.min( x )))
        print( "%d MAX %f" % ( l, np.max( x )))
