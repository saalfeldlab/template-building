#!/urs/bin/python

import sys
import numpy as np
from numpy import float32, int32, uint8, dtype, genfromtxt

N = len( sys.argv )
#print N 

histcsv = sys.argv[ 1 ]
hist = genfromtxt( histcsv, delimiter=',' )

total=np.sum(hist[:,1])
cumsum=np.cumsum(hist[:,1])

for i in range( 2, N ):
    x = float( sys.argv[i])  
    val = hist[ cumsum > ( x * total ), 0 ][ 0 ]
    print( "%f quantile :  %f" % ( x, val ))

