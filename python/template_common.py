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
                      'F-cmtkFlip_lof' : 'CMTK groupwise', 'FCWB':'FCWB',
                      'jfrc 2013' : 'JFRC2013', 'ANTs groupwise' : 'JRC2018',
                      'jfrc 2010' : 'JFRC2010' }

def template_name( name ):
    if name in template_name_map:
        return template_name_map[ name ]
    else:
        return name

def alg_name( name ):
    if name in alg_name_map:
        return alg_name_map[ name ]
    else:
        return name


# Label stuff
labels = [16,64,8,32,2,4,65,66,33,67,34,17,69,70,35,71,9,18,72,36,73,74,37,75,19,76,38,77,39,78,79,20,5,40,80,10,81,82,83,84,85,86,11,22,23,24,12,3,6,49,50,25,51,13,52,26,53,27,54,55,56,28,7,14,57,58,29,59,30,60,15,61,31,62,63]
label_names_file = '/groups/saalfeld/home/bogovicj/vfb/DrosAdultBRAINdomains/refData/Original_Index.tsv'
label_names = pd.read_csv( label_names_file, delimiter='\t', header=0 )

def get_label_name( label_id ):
    return label_names[ label_names['Stack id'] == label_id ]['JFRCtempate2010.mask130819' ].iloc[0]


