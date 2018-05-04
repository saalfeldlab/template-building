import numpy as np

import sys
import numpy as np
from numpy import float32, int32, uint8, dtype, genfromtxt

N = len( sys.argv )

direction = sys.argv[ 1 ]
cx = float(sys.argv[ 2 ])
cx = float(sys.argv[ 2 ])
cy = float(sys.argv[ 3 ])
cz = float(sys.argv[ 4 ])

t=np.array([[1.0,0.0,0.0,cx],[0.0,1.0,0.0,cy],[0.0,0.0,1.0,cz],[0.0,0.0,0.0,1.0]])


sqrt2=0.70710678118

def translation_inv( t ):
    ti = np.copy( t )
    ti[0,3] = -ti[0,3]
    ti[1,3] = -ti[1,3]
    ti[2,3] = -ti[2,3]
    return ti

ti=translation_inv(t)


rotcw  = np.array([[sqrt2,sqrt2,0.0,0.0],[-sqrt2,sqrt2,0.0,0.0],[0.0,0.0,0.0,0.0],[0.0,0.0,0.0,1.0]])
rotccw = np.array([[sqrt2,-sqrt2,0.0,0.0],[sqrt2,sqrt2,0.0,0.0],[0.0,0.0,0.0,0.0],[0.0,0.0,0.0,1.0]])

if( direction == 'CW' or direction == 'cw' ):
    rot = rotcw
else:
    rot = rotccw

# Concatenate
xfm = np.matmul( t, np.matmul( rot, ti ))

print( xfm )
