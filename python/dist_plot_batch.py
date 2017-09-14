# Taken/adapted from analysis notebook:
# DistanceAnalysis-wDistDist-groupLR-line1 

import sys
import glob
import re
import math
import fnmatch
import argparse
from os import listdir
from os.path import join, isfile, basename

import numpy as np
from numpy import float32, int32, uint8, dtype, genfromtxt

import pandas
import cPickle as pickle

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

###############################
# Fixed parameters
###############################

neuron_list_file="/nrs/saalfeld/john/projects/flyChemStainAtlas/eval/prefix_list_by_neuron.txt"
f=open(neuron_list_file)
tmp=f.read().splitlines()
f.close()
neurons = [ n.split(' ') for n in tmp ]

# VFB labels and label names
labels = [16,64,8,32,2,4,65,66,33,67,34,17,69,70,35,71,9,18,72,36,73,74,37,75,19,76,38,77,39,78,79,20,5,40,80,10,81,82,83,84,85,86,11,22,23,24,12,3,6,49,50,25,51,13,52,26,53,27,54,55,56,28,7,14,57,58,29,59,30,60,15,61,31,62,63]
label_names_file = '/groups/saalfeld/home/bogovicj/vfb/DrosAdultBRAINdomains/refData/Original_Index.tsv'

label_names = pandas.read_csv( label_names_file, delimiter='\t', header=0 )

def get_label_name( label_id ):
    return label_names[ label_names['Stack id'] == label_id ]['JFRCtempate2010.mask130819' ].iloc[0]

label_shorthand_col ='JFRCtempate2010.mask130819'
label_id_col ='Stack id'


# Find left-right matching labels
rnames = label_names[ label_names.apply( lambda x : x[label_shorthand_col].endswith('_R'), axis=1 )]

lr_pair_list = []
for rn in rnames.loc[:,label_shorthand_col]:
    ln = rn.replace('_R','_L')
    id_R = label_names[ label_names[label_shorthand_col]==rn ].loc[:,label_id_col]
    id_L = label_names[ label_names[label_shorthand_col]==ln ].loc[:,label_id_col]
    lr_pair_list += [[id_R.values[0], id_L.values[0]]]
lr_pair_list = np.array( lr_pair_list )


###############################
# Function definitions
###############################

def color_boxplot_by_group( bp, groups, colors ):
    for box,i in zip( bp['boxes'], np.arange(len( bp['boxes']))):
        box.set( color=colors[groups[i]])
    
    for med,i in zip( bp['medians'], np.arange(len( bp['medians']))):
        med.set( color='k')
        
    # repeat because there are two whiskers for each box
    for wskr,i in zip(  bp['whiskers'], np.repeat( np.array(groups), 2, axis = 0 ) ):
        wskr.set( color=colors[groups[i]])

def color_boxplots( bp, c ):
    for box in bp['boxes']:
        box.set( color=c )
    
    for med in bp['medians']:
        med.set( color='k')
        
    # repeat because there are two whiskers for each box
    for wskr in  bp['whiskers']:
        wskr.set( color=c )

def set_ticklabel_size( ax, size ):
    for tick in ax.xaxis.get_major_ticks():
        tick.label.set_fontsize( size )
    for tick in ax.yaxis.get_major_ticks():
        tick.label.set_fontsize( size )


def flatten( list_of_lists ):
    return [ item for sublist in list_of_lists for item in sublist ]

def getCount( df_counts, pair_name ):
    res = df_counts[ df_counts.pair == pair_name ]['count']

def normalizeHistogram( hist ):
    hist[:,1] = hist[:,1]/np.sum( hist[:,1])
    return hist

def plotHistBar( hist, title ):
    x = hist[1:-1,0]
    y = hist[1:-1,1]
    plt.bar( x, y )
    plt.xlabel('Distance')
    plt.ylabel('Freq')
    plt.title( title )
    
def plotHistsTogether( hists, names ):
    for i in range( len( hists )):
        hist = hists[ i ]
        x = hist[1:-1,0]
        y = hist[1:-1,1]
        plt.plot( x, y )

    plt.xlabel('Distance')
    plt.ylabel('Freq')
    plt.legend( names )
    
def findHistCsv( folder, label, line ):
    testf = join( folder, 'combined__labelHist_{}_line{}.csv'.format( label, line ))
    if isfile( testf ):
        return testf
    else:
        print'could not find file for label {} and line {} in folder {}'.format( label, line, folder )
        return None
    
def findHistStatCsv( line_names, folder, label, line ):
    out = []
    for prefix in line_names[ line ]:
        pattern = '{}/{}*_labelHist_stats_{}.csv'.format(folder,prefix,label)
#         print pattern
        possibles = glob.glob( pattern )
        #print 'possibles ', possibles
        out += possibles
    return out

def findDistDataCsv( folder, label, line ):
    out = []
    pattern = '{}/combined__distXfmData_{}_line{}.csv'.format(folder,label, line)
#     print pattern
    possibles = glob.glob( pattern )
    #print 'possibles ', possibles
    out += possibles
    return out

def findCombinedDataCsv( folder, label, line ):
    out = []
    pattern = '{}/combined__labelData_{}_line{}.csv'.format(folder,label,line)
    possibles = glob.glob( pattern )
    out += possibles
    return out

def concatenateCentiles( centile_files, cent=0.5 ):
    out = np.array([[]])
    for f in centile_files:
        try:
            stats = genfromtxt( f, delimiter=',' )
            a = stats[1, np.argwhere(stats[0,:]==cent )]
            out = np.concatenate( (out,a), axis=1)
        except ValueError:
            continue
    return out

def getHistogramCounts( centile_files ):
    out = np.array([[]])

def cheap_hist_percentile( hist_in, percentile ):
    hist = hist_in[1:-1,:]
    total=np.sum(hist[:,1])
    cumsum=np.cumsum(hist[:,1])
    return hist[ cumsum > ( percentile * total ), 0 ][ 0 ]

def counts( df_pair_label_counts, names ):
    results = []
    for n in names: 
        pair=basename( n.replace('_stats_','_'))
#         print pair
        thiscount = df_pair_label_counts[ df_pair_label_counts[ 'pair' ] == pair ]['count']

        if thiscount.size != 1:
            continue

        results += [ thiscount.values ]
#         print results
        
    return np.mean( np.array( results ))

def load_the_data( dir, line ):
    sample_counts = {}

    all_data = {}

    sz =  None 
    for l in labels:

        if l in lr_pair_list:

            i,j =  np.where( lr_pair_list == l )
            jother = 0 if j==1 else 1
            label_pair = lr_pair_list[i,jother][0]
            
            if l < label_pair:
                
                grpDataFileList  = findCombinedDataCsv(  dir, l, line )
                dat = genfromtxt( grpDataFileList[0], delimiter=',' )
                
                other_grpDataFileList  = findCombinedDataCsv(  dir, label_pair, line )
                other_dat = genfromtxt( other_grpDataFileList[0], delimiter=',' )
                
                all_data[ l ] = np.concatenate( (dat, other_dat ))
                all_data[ label_pair ] = np.array([])
                
                sample_counts[ l ] = len( all_data[ l ])
                sample_counts[ label_pair ] =  0
                
        else:
            dataFileList  = findCombinedDataCsv(  dir, l, line )
            grp_dat = genfromtxt( dataFileList[0], delimiter=',' )

            all_data[ l ] = grp_dat
            sample_counts[ l ] = len( all_data[ l ])

    #print sample_counts

    # Sort and return
    sorted_idxs  = np.argsort( np.array( sample_counts.values() ))

    sorted_labels = np.array( sample_counts.keys())[ sorted_idxs ]
    sorted_labels = list(reversed(sorted_labels))

    sorted_label_counts = np.array( sample_counts.values())[ sorted_idxs ]
    sorted_label_counts = list(reversed(sorted_label_counts))

    return all_data, sample_counts, sorted_labels, sorted_label_counts


def plot_the_data( all_data, x_positions, outputpath, xlabels,
        do_scatter=True, do_boxplot=False,
	max_scatter_pts=200, scatter_radius=1.0, y_scatter_radius=1.0,
	color='#ff0000' ):

	whisker_props = { 'linewidth' : 0.75, 'linestyle' : '-'}
	box_props = { 'linewidth' : 1.4 }
	med_props = { 'linewidth' : 1.4 }

	if do_boxplot:
            bp = plt.boxplot( all_data, positions=x_positions, whis=2, showcaps=False, showfliers=False, 
                    boxprops=box_props, whiskerprops=whisker_props, medianprops=med_props )

	if do_scatter:
            for i in range( len( all_data )):
                if( len(all_data[ i ]) <= max_scatter_pts ):
                    sample = all_data[ i ]
                else:
                    sample = all_data[ i ][ np.random.choice( len(all_data[i]), max_scatter_pts)]

                x = np.repeat( (1 +x_positions[i]), len( sample ))
                x = x + ( scatter_radius * np.random.rand( *x.shape ) - (scatter_radius / 2.) )

                sample = sample + ( y_scatter_radius * np.random.rand( *sample.shape ) - ( y_scatter_radius / 2.) )

                # make sure everything is above zero
                sample[ sample < 0 ] = -sample[ sample < 0 ]

                plt.plot( x, sample, linestyle='None', marker='o', markersize=4, markeredgewidth=0,
                         markerfacecolor=color, alpha=0.1 )

	#maxx = math.ceil( x[-1] + 1 )

	ax = plt.gca()
	# Turn off top and right spines
	ax.spines['top'].set_visible(False)
	ax.spines['right'].set_visible(False)
	ax.spines['bottom'].set_visible(False)
	ax.spines['left'].set_visible(False)

	ax.get_xaxis().tick_bottom()
	ax.get_yaxis().tick_left()

	# Turn off tick markers
	plt.tick_params( axis='x', bottom='off' )
	plt.tick_params( axis='y', left='off' )

	#plt.gca().set_xlim([-1,maxx + 1])
	#plt.gca().set_ylim([-0.2,30])

        print x_positions
        print xlabels

	plt.xticks( x_positions, xlabels, rotation=45, fontsize=14 )

	if do_boxplot:
            color_boxplots( bp, color )

	fig = plt.gcf()
	_ = fig.set_size_inches( 18, 10 )

	ax.yaxis.grid(True)
	plt.ylabel( 'Distance (um)', fontsize=14 )

	set_ticklabel_size( ax, 14 )

	plt.savefig( outputpath )

###############################
# Main stuff
###############################
def main( dir, line, outputpath ):
    print 'the dir:'
    print dir

    all_data, sample_counts, sorted_labels, sorted_label_counts = load_the_data( dir, line )
    #print all_data

    xlabels = flatten( [ [ str(get_label_name(l)).replace('_R','')] for l in sorted_labels ] )
    print xlabels

    # Order the data and build x positions for appropriate spacing
    x_positions = []
    small_space = 3
    big_space = 6
    p = 1
    
    print ' '
    print 'sorted_label_counts'
    print sorted_label_counts
    print ' '

    print ' '
    print 'sorted_labels'
    print sorted_labels
    print ' '


    slc = np.array( sorted_label_counts )
    sl = np.array( sorted_labels )
    labels_of_interest = sl[ slc > 0 ]

    print ' '
    print 'labels_of_interest'
    print labels_of_interest
    print ' '

    plot_data = []
    for l in labels_of_interest:
        print l
        plot_data += [ all_data[ l ] ]
        x_positions += [ p ]
        p += ( small_space )

    plot_the_data( plot_data, x_positions, outputpath, xlabels )

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument( '-d', '--dir', help="The source directory with distance data")
    parser.add_argument( '-l', '--line', help="The Driver line index [0-3]")
    parser.add_argument( '-o', '--output', help="Output name", default=None)
    args = parser.parse_args()

    main( args.dir, args.line, args.output )
    

