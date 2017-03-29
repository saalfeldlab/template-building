#!/urs/bin/python

import sys
import numpy as np
from numpy import float32, int32, uint8, dtype, genfromtxt

N = len( sys.argv)
print N 

outf = sys.argv[ 1 ]
combined_dat = []
for i in range( 2, N ):
    f = sys.argv[i]
    print f
    #print combined_dat
    if i == 2:
        combined_dat = genfromtxt( f, delimiter=',' )
    else:
        dat = genfromtxt( f, delimiter=',' )
        combined_dat[:,1] += dat[:,1]

np.savetxt( outf, combined_dat, delimiter=',' )

