import sys
import math
import os
from os import listdir
from os.path import join, isfile, basename

import itertools

import numpy as np
from numpy import float32, int32, uint8, dtype, genfromtxt

from scipy.stats import ttest_ind
import pandas as pd

N=-1 
Nargs = len( sys.argv )

print( 'Nargs', Nargs )

#output_file = sys.argv[ 1 ]
line = int( sys.argv[ 1 ] )
alg_list = sys.argv[ 2 ]
template = sys.argv[ 3 ]

if Nargs >= 5:
    N = int(sys.argv[ 4 ])

def str2bool( s ):
    return s.lower() in ["true", "t", "yes", "y", "1" ]

merge_labels = False
if Nargs >= 6:
    merge_labels = str2bool(sys.argv[ 5 ])

print( 'line: ', line )
print( 'algs: ', alg_list )
print( 'template: ', template )
print( 'merge labels?: ', merge_labels )
print( 'N: ', N )

base_dir = '/nrs/saalfeld/john/projects/flyChemStainAtlas/all_evals'

data_file = '{}/label_data_line{}.csv.gz'.format( base_dir, line )
print( 'data file: ', data_file )

if merge_labels:
    labels = [-1]
else:
    labels = [16,64,8,32,2,4,65,66,33,67,34,17,69,70,35,71,9,18,72,36,73,74,37,75,19,76,38,77,39,78,79,20,5,40,80,10,81,82,83,84,85,86,11,22,23,24,12,3,6,49,50,25,51,13,52,26,53,27,54,55,56,28,7,14,57,58,29,59,30,60,15,61,31,62,63]

dist_samples_df = pd.read_csv( data_file, header=None, names=['TEMPLATE','ALG','LINE','LABEL','DISTANCE'] )
print( dist_samples_df.shape )

out_dir = '{}/algStatsByLabel/{}'.format( base_dir, template)

if not os.path.exists(out_dir):
    print( 'creating folder: ', out_dir )
    os.makedirs(out_dir)

print( ' ' )
for alg1,alg2 in itertools.combinations( alg_list.split(' '), 2 ):

    print( alg1, ' vs ', alg2 )

    if merge_labels:
        out_file = '{}/mergeLabels_{}_vs_{}_line{}.csv'.format( out_dir, alg1, alg2, line )
    else:
        out_file = '{}/{}_vs_{}_line{}.csv'.format( out_dir, alg1, alg2, line )
    print( 'out data file: ', out_file )

    if template == 'none':
        df_template = dist_samples_df
    else:
        df_template = dist_samples_df[ (dist_samples_df.TEMPLATE == template) ]

    df_alg1 = df_template[ (df_template.ALG == alg1) ]
    df_alg2 = df_template[ (df_template.ALG == alg2) ]
    
    # for all lines / labels
    out_line_list = []
    out_label_list = []
    out_count1_list = []
    out_count2_list = []
    out_mean1_list = []
    out_mean2_list = []
    out_tstat_list = []
    out_pval_list = [] 
 
    for label in labels:
        print( 'line: {},  label {}'.format( line, label ))
   
        if label < 0:
            df_alg1_line_label = df_alg1[ (df_alg1.LINE == line) ]
            df_alg2_line_label = df_alg2[ (df_alg2.LINE == line) ]
        else:
            df_alg1_line_label = df_alg1[ (df_alg1.LINE == line) & (df_alg1.LABEL == label) ]
            df_alg2_line_label = df_alg2[ (df_alg2.LINE == line) & (df_alg2.LABEL == label) ]
        
        print( df_alg1_line_label.shape )
        print( df_alg2_line_label.shape )
        
        if( N < 0 or df_alg1_line_label.shape[0] <= N ):
            s1 = df_alg1_line_label
        else:
            s1 = df_alg1_line_label.sample(N)
           
        if( N < 0 or df_alg2_line_label.shape[0] <= N ):
            s2 = df_alg2_line_label
        else:
            s2 = df_alg2_line_label.sample(N)
        
        mn1 = s1['DISTANCE'].mean()
        mn2 = s2['DISTANCE'].mean()
        
        t,p = ttest_ind( s1['DISTANCE'], s2['DISTANCE'])
        
        out_line_list += [ line ]
        out_label_list += [ label ]
        out_count1_list += [ df_alg1_line_label.shape[0] ]
        out_count2_list += [ df_alg2_line_label.shape[0] ]
        out_mean1_list += [ mn1 ]
        out_mean2_list += [ mn2 ]
        out_tstat_list += [ t ]
        out_pval_list += [ p ]
    
    df = pd.DataFrame( {'LINE':out_line_list,
                        'LABEL':out_label_list,
                        ('COUNT_'+alg1):out_count1_list,
                        ('COUNT_'+alg2):out_count2_list,
                        ('MEAN_'+alg1):out_mean1_list,
                        ('MEAN_'+alg2):out_mean2_list,
                        'TSTAT':out_tstat_list,
                        'PVAL':out_pval_list })
    
    df.to_csv( out_file )

print( 'all done' )
