import sys
import glob
import re
import fnmatch
import math
import os
from os import listdir
from os.path import join, isfile, basename

import itertools

import numpy as np
from numpy import float32, int32, uint8, dtype, genfromtxt

import scipy
from scipy.stats import ttest_ind

import pandas as pd

import colorsys


## Parse inputs

base_dir = sys.argv[ 1 ]
template = sys.argv[ 2 ]
alg = sys.argv[ 3 ]

print( 'template ', template )
print( 'alg ', alg )

# For debug
#base_dir = os.getcwd()
#base_dir = sys.argv[ 1 ]
#base_dir='/nrs/saalfeld/john/projects/flyChemStainAtlas/all_evals'

#template='FCWB'
#alg='antsRegDog8'

exp_dir = join( base_dir, template, alg )
print( exp_dir )


eval_dir = join( exp_dir, 'evalComp' )
df_tot = pd.DataFrame( columns=['TEMPLATE','ALG','LINE','LABEL','DISTANCE'])

for line in [0,1,2,3]:
    # Read label stats
    datFile = '{}/combined_labelData_line{}.csv'.format( eval_dir, line )
    print( 'loading ', datFile )
    
    df_line = pd.read_csv( datFile, header=None, names=['LABEL','DISTANCE'] )
    df_line['LINE'] = line
    df_line['TEMPLATE'] = template
    df_line['ALG'] = alg
    
    #print( len( df_line ))
    #print(df_line.head())
    #print( ' ' )
    df_tot = df_tot.append( df_line )


print( len( df_tot ))




labels = [16,64,8,32,2,4,65,66,33,67,34,17,69,70,35,71,9,18,72,36,73,74,37,75,19,76,38,77,39,78,79,20,5,40,80,10,81,82,83,84,85,86,11,22,23,24,12,3,6,49,50,25,51,13,52,26,53,27,54,55,56,28,7,14,57,58,29,59,30,60,15,61,31,62,63]
label_names_file = '/groups/saalfeld/home/bogovicj/vfb/DrosAdultBRAINdomains/refData/Original_Index.tsv'

label_names = pd.read_csv( label_names_file, delimiter='\t', header=0 )

def get_label_name( label_id ):
    return label_names[ label_names['Stack id'] == label_id ]['JFRCtempate2010.mask130819' ].iloc[0]




# drop the LINE column
print('grouping')
# df_atl = df_raw.drop(['LINE'], axis=1).groupby(['ALG','TEMPLATE','LABEL'])
df = df_tot.drop(['LINE'], axis=1)
df_atl = df.groupby(['ALG','TEMPLATE','LABEL'],as_index=False)
df_lat = df.groupby(['LABEL','ALG','TEMPLATE'],as_index=False)




# Functions

def perc_fun( perc ):
    def f(series):
        return np.percentile( series, perc*100)
    return f

def p10(series):
    return np.percentile( series, 10 )

def p90(series):
    return np.percentile( series, 90 )

def ray_params_fl(series, eps=0.001):
    return scipy.stats.gamma.fit( series + eps , floc=0. )

def gam_params_fl(series, eps=0.001):
    return scipy.stats.gamma.fit( series + eps , floc=0. )

def gam_mode( series, eps=0.001 ):
    gam_params = scipy.stats.gamma.fit( series + eps , floc=0. )
    return ( gam_params[0] -1 ) * gam_params[2]




# Compute statistics over labels
df_atl_perc = df_atl.agg( { 'DISTANCE' : [ 'count', gam_mode, p10, p90 ]})



print( 'ouput table len: ', len(df_atl_perc))
dest_file = '{}/all_combinedStats_df.csv'.format( eval_dir ) 

print( 'writing to ', dest_file )
df_atl_perc.to_csv( dest_file )
print( 'done' )

