import sys
import glob
import re
import math
import os
import itertools
import numpy as np
import pandas as pd

# Name Map
alg_name_map = { 'antsRegDog': 'ANTs A', 'antsRegOwl' : 'ANTs B',
        'antsRegYang' : 'ANTs C', 'cmtkCOG' : 'CMTK A',
        'cmtkHideo' : 'CMTK B', 'cmtkCow' : 'CMTK C',
        'ANTs Dog' : 'ANTs A', 'ANTs Owl' : 'ANTs B', 'ANTs Yang' : 'ANTs C',
        'CMTK COG' : 'CMTK A', 'CMTK Hideo' : 'CMTK B', 'CMTK Cow' : 'CMTK C' }

template_name_map = { 'JFRC2013_lo':'JFRC2013', 'JFRCtemplate2010' : 'JFRC2010', 
                      'TeforBrain_f' : 'Tefor', 'F-antsFlip_lo' : 'JRC2018',
                      'F-antsFlip_lof' : 'JRC2018', 
                      'F-cmtkFlip_lof' : 'CMTK groupwise', 'FCWB':'FCWB',
                      'jfrc 2013' : 'JFRC2013', 'ANTs groupwise' : 'JRC2018',
                      'jfrc 2010' : 'JFRC2010',
                      'jrc18_1p2':'JFRC2018 (1.2um)',
                      'jrc18_2p4':'JFRC2018 (2.4um)',
                      'jrc18_3p6':'JFRC2018 (3.6um)',
                      'TeforBrain_1p2iso':'Tefor (1.2um)',
                      'TeforBrain_2p4iso':'Tefor (2.4um)',
                      'TeforBrain_3p6iso':'Tefor (3.6um)'}

def template_name( name_raw ):
    name = name_raw.lstrip(' ').rstrip(' ')
    if name in template_name_map:
        return template_name_map[ name ]
    else:
        return name

def alg_name( name_raw ):
    name = name_raw.lstrip(' ').rstrip(' ')
    if name in alg_name_map:
        return alg_name_map[ name ]
    else:
        return name

def clean_cols( df ):
    ## clean up the weird columns
    df.columns = [ c[0] if c[1].startswith('Unnamed') else c[1] for c in df.columns.values ]

# Label stuff
labels = [16,64,8,32,2,4,65,66,33,67,34,17,69,70,35,71,9,18,72,36,73,74,37,75,19,76,38,77,39,78,79,20,5,40,80,10,81,82,83,84,85,86,11,22,23,24,12,3,6,49,50,25,51,13,52,26,53,27,54,55,56,28,7,14,57,58,29,59,30,60,15,61,31,62,63]
label_names_file = '/groups/saalfeld/home/bogovicj/vfb/DrosAdultBRAINdomains/refData/Original_Index.tsv'
label_names = pd.read_csv( label_names_file, delimiter='\t', header=0 )

def get_label_name( label_id ):
    return label_names[ label_names['Stack id'] == label_id ]['JFRCtempate2010.mask130819' ].iloc[0]

def get_label_string( label_id ):
    row = label_names[label_names['Stack id'] == label_id ].reset_index()
    print( row )
    return '{} : {}'.format( row.loc[0,'JFRCtempate2010.mask130819'], row.loc[0,'Name'])


# old entries:
#  'JFRC2010':'firebrick'
#  'JFRC2013':'navy'
#  'FCWB':'darkgreen'
#  'CMTK groupwise':'gray'
template_color_map = { 'JFRC2010':'#E66101',
                       'JFRC2013':'#FDB863',
                      'JFRC2013_lo':'#FDB863',
                       'FCWB':'#B2ABD2',
                       'Tefor':'#5E3C99',
                       'JRC2018':'black',
                       'CMTK groupwise':'darkorange',
                      'jrc18_0p6':'black',
                      'jrc18_1p2':'black',
                      'jrc18_2p4':'black',
                      'jrc18_3p6':'black',
                      'F-antsFlip_lo':'black',
                      'F-antsFlip_lof':'black',
                      'TeforBrain_f':'#5E3C99',
                      'TeforBrain_1p2iso':'#5E3C99',
                      'TeforBrain_2p4iso':'#5E3C99',
                      'TeforBrain_3p6iso':'#5E3C99'
                     }

