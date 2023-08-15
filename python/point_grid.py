import sys
import argparse
import numpy as np

parser = argparse.ArgumentParser(description='Output a point grid.')
parser.add_argument('-l', '--min', nargs=3, metavar=('minx', 'miny', 'minz'), type=float, 
                    help='the bounding box min')
parser.add_argument('-x', '--max', nargs=3, metavar=('maxx', 'maxy', 'maxz'), type=float, 
                    help='the bounding box max')
parser.add_argument('-r', '--res', nargs=3, metavar=('rx', 'ry', 'rz'), type=float, 
                    help='the sample spacing')
parser.add_argument('-o', '--out', type=str, help='the output path')


args = parser.parse_args(sys.argv[1:])
lo = args.min
hi = args.max
r = args.res

x,y,z = np.meshgrid( *[ np.arange( lo[i], hi[i], r[i] ) for i in range(3) ] )
grid = np.stack( [x,y,z])
table = grid.reshape( 3, np.prod(grid.shape[1:]))

np.savetxt(args.out, table.T , delimiter=',', fmt='%0.5f')
