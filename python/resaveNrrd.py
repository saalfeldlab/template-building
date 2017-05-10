import sys
import numpy as np
import nrrd

def resave( f, outf ):
	data_in,opts_in = nrrd.read( f )
	print( opts_in )

	space_directions=[['1.0', '0.0', '0.0'], ['0.0', '1.0', '0.0'], ['0.0', '0.0', '1.0']]
	space_units=['"microns"','"microns"','"microns"']
	#space='left-posterior-superior'
	space='right-anterior-superior'

	opts_out = opts_in
	opts_out['space directions'] = space_directions
	opts_out['space units'] = space_units
	opts_out['space'] = space

	print( opts_out )
	nrrd.write( outf, data_in, options=opts_out )

	print 'done'

if __name__ == "__main__":

	N = len( sys.argv)
	print N 

	f = sys.argv[ 1 ]
	outf = sys.argv[ 2 ]
	print( f )
	print( outf )
	resave( f, outf )
