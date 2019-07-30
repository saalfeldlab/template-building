#!/usr/bin/env python3

import sys
import numpy as np
from numpy import genfromtxt

dat = genfromtxt( sys.argv[ 1 ], delimiter=',')
fullmtx = dat.reshape(3,4)
mtx = fullmtx[:,0:3]
print( np.linalg.det( mtx ))
