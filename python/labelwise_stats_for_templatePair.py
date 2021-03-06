import sys
import math
import os
from os import listdir
from os.path import join, isfile, basename

import itertools

import numpy as np
from numpy import float32, int32, uint8, dtype, genfromtxt

import scipy.stats
from scipy.stats import ttest_ind
import pandas as pd

N=-1 
Nargs = len( sys.argv )

print( 'Nargs', Nargs )

#output_file = sys.argv[ 1 ]
line = int( sys.argv[ 1 ] )
alg = sys.argv[ 2 ]
template_list = sys.argv[ 3 ]

N = int(sys.argv[ 4 ])

def str2bool( s ):
    return s.lower() in ["true", "t", "yes", "y", "1" ]

def cohend( m1, s1, n1, m2, s2, n2 ):
    print( 'cohen d' )
    dof = n1 + n2 - 2
    return (m1 - m2)/math.sqrt( ((n1-1)*s1 ** 2 + (n2-1)*s2 ** 2 ) / dof )

merge_labels = False
do_all = False

thearg = sys.argv[ 5 ]
if thearg.lower() == 'all':
    do_all = True
    merge_labels = False
else:
    merge_labels = str2bool( thearg )

confidence=0.95
confidence = float(sys.argv[ 6 ])


print( 'line: ', line )
print( 'algs: ', alg )
print( 'template: ', template_list )
print( 'merge labels?: ', merge_labels )
print( 'N: ', N )
print( 'confidence: ', confidence )

base_dir = '/nrs/saalfeld/john/projects/flyChemStainAtlas/all_evals'

data_file = '{}/label_data_line{}.csv.gz'.format( base_dir, line )
print( 'data file: ', data_file )

base_labels = [16,64,8,32,2,4,65,66,33,67,34,17,69,70,35,71,9,18,72,36,73,74,37,75,19,76,38,77,39,78,79,20,5,40,80,10,81,82,83,84,85,86,11,22,23,24,12,3,6,49,50,25,51,13,52,26,53,27,54,55,56,28,7,14,57,58,29,59,30,60,15,61,31,62,63]

if do_all:
    labels = base_labels
    labels[-1] = -1
else:
    if merge_labels:
        labels = [-1]
    else:
        labels = base_labels 

dist_samples_df = pd.read_csv( data_file, header=None, names=['TEMPLATE','ALG','LINE','LABEL','DISTANCE'] )
print( dist_samples_df.shape )

out_dir = '{}/templateStatsByLabel/{}'.format( base_dir, alg)

if not os.path.exists(out_dir):
    print( 'creating folder: ', out_dir )
    os.makedirs(out_dir)

print( ' ' )
for template1,template2 in itertools.combinations( template_list.split(' '), 2 ):

    print( template1, ' vs ', template2 )
    if merge_labels:
        out_file = '{}/mergeLabels_{}_vs_{}_line{}.csv'.format( out_dir, template1, template2, line )
    else:
        out_file = '{}/{}_vs_{}_line{}.csv'.format( out_dir, template1, template2, line )
    print( 'out data file: ', out_file )

    if alg == 'none':
        df_alg = dist_samples_df
    else:
        df_alg = dist_samples_df[ (dist_samples_df.ALG == alg) ]

    print( 'after filtering by alg, size: ', len(df_alg))

    df_template1 = df_alg[ (df_alg.TEMPLATE == template1) ]
    df_template2 = df_alg[ (df_alg.TEMPLATE == template2) ]

    print( 'after filtering by t1, size: ', len(df_template1))
    print( 'after filtering by t2, size: ', len(df_template2))
    
    # for all lines / labels
    out_line_list = []
    out_label_list = []
    out_count1_list = []
    out_count2_list = []
    out_mean1_list = []
    out_mean2_list = []
    out_tstat_list = []
    out_pval_list = [] 

    # Effect size
    out_cohend_list = [] 

    # Wilcoxon rank sum stat and p-value
    out_ws_list = []
    out_wp_list = []

    # Kruskal-Wallis test that the samples have different median
    out_ks_list = []
    out_kp_list = []

    # bayesian confidence intervals of mean,variance,and sttddev
    out_mc1_list = []
    out_mc1min_list = []
    out_mc1max_list = []
    out_vc1_list = []
    out_vc1min_list = []
    out_vc1max_list = []
    out_sc1_list = []
    out_sc1min_list = []
    out_sc1max_list = []

    out_mc2_list = []
    out_mc2min_list = []
    out_mc2max_list = []
    out_vc2_list = []
    out_vc2min_list = []
    out_vc2max_list = []
    out_sc2_list = []
    out_sc2min_list = []
    out_sc2max_list = []

 
    for label in labels:
        print( 'line: {},  label {}'.format( line, label ))
   
        if label < 0:
            df_template1_line_label = df_template1[ (df_template1.LINE == line) ]
            df_template2_line_label = df_template2[ (df_template2.LINE == line) ]
        else:
            df_template1_line_label = df_template1[ (df_template1.LINE == line) & (df_template1.LABEL == label) ]
            df_template2_line_label = df_template2[ (df_template2.LINE == line) & (df_template2.LABEL == label) ]
        
        print( df_template1_line_label.shape )
        print( df_template2_line_label.shape )

        count_1 = df_template1_line_label.shape[0] 
        count_2 = df_template2_line_label.shape[0] 

        if( N < 0 or count_1 <= N ):
            s1 = df_template1_line_label
        else:
            s1 = df_template1_line_label.sample(N)
           
        if( N < 0 or count_2 <= N ):
            s2 = df_template2_line_label
        else:
            s2 = df_template2_line_label.sample(N)
        
        mn1 = s1['DISTANCE'].mean()
        mn2 = s2['DISTANCE'].mean()
       
        t,p = ttest_ind( s1['DISTANCE'], s2['DISTANCE'])

        ks,kp = scipy.stats.kruskal( s1['DISTANCE'], s2['DISTANCE'])
        out_ks_list += [ ks ]
        out_kp_list += [ kp ]

        if count_1 > 10 and count_2 > 10:

            # wilcoxon rank sum test
            min_count = count_1
            if count_1 > count_2:
                min_count = count_2

            ws,wp = scipy.stats.wilcoxon( s1['DISTANCE'].sample(min_count), s2['DISTANCE'].sample(min_count))
            out_ws_list += [ ws ]
            out_wp_list += [ wp ]

            mc1,vc1,sc1 = scipy.stats.bayes_mvs( s1['DISTANCE'], alpha=confidence) 

            out_mc1_list += [ mc1.statistic ]
            out_mc1min_list += [ mc1.minmax[0] ]
            out_mc1max_list += [ mc1.minmax[1] ]
            out_vc1_list += [ vc1.statistic ]
            out_vc1min_list += [ vc1.minmax[0] ]
            out_vc1max_list += [ vc1.minmax[1] ]
            out_sc1_list += [ sc1.statistic ]
            out_sc1min_list += [ sc1.minmax[0] ]
            out_sc1max_list += [ sc1.minmax[1] ]

            mc2,vc2,sc2 = scipy.stats.bayes_mvs( s2['DISTANCE'], alpha=confidence) 

            out_mc2_list += [ mc2.statistic ]
            out_mc2min_list += [ mc2.minmax[0] ]
            out_mc2max_list += [ mc2.minmax[1] ]
            out_vc2_list += [ vc2.statistic ]
            out_vc2min_list += [ vc2.minmax[0] ]
            out_vc2max_list += [ vc2.minmax[1] ]
            out_sc2_list += [ sc2.statistic ]
            out_sc2min_list += [ sc2.minmax[0] ]
            out_sc2max_list += [ sc2.minmax[1] ]

            out_cohend_list += [ cohend( mc1.statistic, sc1.statistic, count_1, mc2.statistic, sc2.statistic, count_2 )] 

        else:
            print( 'skipping')
            out_mc1_list += [ float('nan') ]
            out_mc1min_list += [ float('nan') ]
            out_mc1max_list += [ float('nan') ]
            out_vc1_list += [ float('nan') ]
            out_vc1min_list += [ float('nan') ]
            out_vc1max_list += [ float('nan') ]
            out_sc1_list += [ float('nan') ]
            out_sc1min_list += [ float('nan') ]
            out_sc1max_list += [ float('nan') ]
            out_mc2_list += [ float('nan') ]
            out_mc2min_list += [ float('nan') ]
            out_mc2max_list += [ float('nan') ]
            out_vc2_list += [ float('nan') ]
            out_vc2min_list += [ float('nan') ]
            out_vc2max_list += [ float('nan') ]
            out_sc2_list += [ float('nan') ]
            out_sc2min_list += [ float('nan') ]
            out_sc2max_list += [ float('nan') ]

            out_cohend_list += [float('nan')]

            out_ws_list += [ float('nan') ]
            out_wp_list += [ float('nan') ]

        
        out_line_list += [ line ]
        out_label_list += [ label ]
        out_count1_list += [ count_1 ]
        out_count2_list += [ count_2 ]
        out_mean1_list += [ mn1 ]
        out_mean2_list += [ mn2 ]
        out_tstat_list += [ t ]
        out_pval_list += [ p ]
    
    df = pd.DataFrame( {'LINE':out_line_list,
                        'LABEL':out_label_list,
                        ('COUNT_'+template1):out_count1_list,
                        ('COUNT_'+template2):out_count2_list,
                        ('MEAN_'+template1):out_mean1_list,
                        ('MEAN_'+template2):out_mean2_list,
                        'TSTAT':out_tstat_list,
                        'PVAL':out_pval_list,
                        'COHEND':out_cohend_list,
                        'WILCOXON':out_ws_list,
                        'WILCOXONP':out_wp_list,
                        'KRUSKAL':out_ks_list,
                        'KRUSKALP':out_kp_list,
                        ('MNSTAT_'+template1):out_mc1_list,
                        ('MNMIN_'+template1):out_mc1min_list,
                        ('MNMAX_'+template1):out_mc1max_list,
                        ('VRSTAT_'+template1):out_vc1_list,
                        ('VRMIN_'+template1):out_vc1min_list,
                        ('VRMAX_'+template1):out_vc1max_list,
                        ('SDSTAT_'+template1):out_sc1_list,
                        ('SDMIN_'+template1):out_sc1min_list,
                        ('SDMAX_'+template1):out_sc1max_list,
                        ('MNSTAT_'+template2):out_mc2_list,
                        ('MNMIN_'+template2):out_mc2min_list,
                        ('MNMAX_'+template2):out_mc2max_list,
                        ('VRSTAT_'+template2):out_vc2_list,
                        ('VRMIN_'+template2):out_vc2min_list,
                        ('VRMAX_'+template2):out_vc2max_list,
                        ('SDSTAT_'+template2):out_sc2_list,
                        ('SDMIN_'+template2):out_sc2min_list,
                        ('SDMAX_'+template2):out_sc2max_list
                        })

    df.to_csv( out_file )
print( 'all done' )
