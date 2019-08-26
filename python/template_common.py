import sys
import glob
import re
import math
import os
import itertools
import numpy as np
import pandas as pd

import matplotlib.pyplot as plt

alg_list = ['antsRegDog', 'antsRegOwl', 'antsRegYang', 'cmtkCOG', 'cmtkCow', 'cmtkHideo', 'elastixSFA', 'elastixBigfootA' ]
template_list = [ 'JFRC2013_lo', 'JFRCtemplate2010', 'TeforBrain_f', 'F-antsFlip_lo', 'FCWB']

template_list_res_old = [ 'F-antsFlip_lo', 'jrc18_1p2', 'jrc18_2p4', 'jrc18_3p6', \
                    'TeforBrain_f', 'TeforBrain_1p2', 'TeforBrain_2p4', 'TeforBrain_3p6']

template_list_res = [ 'F-antsFlip_lo', 'jrc18_1p2', 'jrc18_2p4', 'jrc18_3p6', \
                    'TeforBrain_f', 'TeforBrain_1p2iso', 'TeforBrain_2p4iso', 'TeforBrain_3p6iso']

jrc18_template_name_list = [ 'F-antsFlip_lo', 'F-antsFlip_lof','jrc18_1p2', 'jrc18_2p4', 'jrc18_3p6', \
                           'JFRC2018','JFRC2018 (1.2um)','JFRC2018 (2.4um)','JFRC2018 (3.6um)']

# Name Map
alg_name_map = { 'antsRegDog': 'ANTs A', 'antsRegDog8': 'ANTs A', 
                'antsRegOwl' : 'ANTs B',
        'antsRegYang' : 'ANTs C', 'cmtkCOG' : 'CMTK A',
        'cmtkHideo' : 'CMTK B', 'cmtkCow' : 'CMTK C',
        'ANTs Dog' : 'ANTs A', 'ANTs Owl' : 'ANTs B', 'ANTs Yang' : 'ANTs C',
        'CMTK COG' : 'CMTK A', 'CMTK Hideo' : 'CMTK B', 'CMTK Cow' : 'CMTK C',
        'elastixSFA' : 'Elastix A', 'elastixBigfootA' : 'Elastix B' }

template_name_map = { 'JFRC2013_lo':'JFRC2013', 'JFRC2013_lof':'JFRC2013', 
                     'JFRCtemplate2010' : 'JFRC2010', 
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
    
template_list_mapped = [template_name(t) for t in template_list ]

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

def clean_table_hdr( df ):

    for col in [ x for x in df.columns if x.startswith('Unnamed') ]:
        df = df.drop( col, axis=1 )

    rename_dict = {}
    for a,b in df.loc[ 0 ].iteritems():
        if( b == 'count'):
            rename_dict[a] = b
        elif( a.startswith('DISTANCE')):
            rename_dict[a] = '_'.join(['DISTANCE', b])

    df= df.rename(columns=rename_dict)
    df = df.drop([0])
    return df

def clean_jac_table( df ):

    for col in [ x for x in df.columns if x.startswith('Unnamed') ]:
        df = df.drop( col, axis=1 )

    
    rename_dict = {}
    for a,b in df.loc[ 0 ].iteritems():
        rename_dict[a] = '_'.join(['JAC', b])

    df= df.rename(columns=rename_dict)
    df = df.drop([0])
    return df

def clean_names( df ):
    df['TEMPLATE'] = df.apply(lambda x: template_name(x['TEMPLATE']), axis=1)
    df['ALG'] = df.apply(lambda x: alg_name(x['ALG']), axis=1)
    df['TA'] = df.apply(lambda x: ''.join([x['TEMPLATE'],':',x['ALG']]), axis=1)
    return df

def addNormColumn( f, df ):
    if re.search( "Affine", f ) is not None:
        normalizationType = 'affine'
    else:
        normalizationType = 'warp'
    
    normtype = df.apply( lambda x: 'na' if x['TEMPLATE'] in jrc18_template_name_list else normalizationType, axis=1)
    df['normalization'] = normtype
    
    return df

def genStdDevs( df ):
    df['DISTANCE_std'] = df.apply( lambda x: math.sqrt(float(x['DISTANCE_var'])), axis=1)
    df['DISTANCE_gam_std'] = df.apply( lambda x: math.sqrt(float(x['DISTANCE_gam_var'])), axis=1)
    df['DISTANCE_ray_std'] = df.apply( lambda x: math.sqrt(float(x['DISTANCE_ray_var'])), axis=1)

def meanStdStringCol( df, resultcol, meancol, stdcol, formatstring='%0.2f (%0.2f)' ):
    df[resultcol] = df.apply(
        lambda x: formatstring%(x[meancol], x[stdcol]), axis=1)

    
def readStatTables( stat_dir='/nrs/saalfeld/john/projects/flyChemStainAtlas/all_evals/stats_2019Jul'):
    dist_df = None
    for f in glob.glob( ''.join([stat_dir,'/*dist*.csv']) ):
        tmp_df = pd.read_csv( f )
        tmp_df_clean = clean_table_hdr( tmp_df ).reset_index( drop=True )
        tmp_df_clean_norm = addNormColumn( f, tmp_df_clean )
#         print( tmp_df_clean_norm[ tmp_df_clean_norm.normalization == 'na'].shape )
        if dist_df is None:
            dist_df = tmp_df_clean_norm
        else:
            dist_df = dist_df.append( tmp_df_clean_norm )

    genStdDevs( dist_df )

    jac_df = None
    for f in glob.glob( ''.join([stat_dir,'/*jacStats.csv']) ):
    #     print( f )
        tmp_df = pd.read_csv( f )
        if jac_df is None:
            jac_df = tmp_df
        else:
            jac_df = jac_df.append( tmp_df )

    jac_df.rename( columns={'count':'JAC_count','mean':'JAC_mean','variance':'JAC_var','stddev':'JAC_std'}, inplace=True)

    hess_df = None
    for f in glob.glob( ''.join([stat_dir,'/*hessStats.csv']) ):
    #     print( f )
        tmp_df = pd.read_csv( f )
        if hess_df is None:
            hess_df = tmp_df
        else:
            hess_df = hess_df.append( tmp_df )

    hess_df.rename( columns={'count':'HES_count','mean':'HES_mean','variance':'HES_var','stddev':'HES_std'}, inplace=True)


    timeMem_df = None
    for f in glob.glob( ''.join([stat_dir,'/*timeStats.csv']) ):
    #     print( f )
        tmp_df = pd.read_csv( f, names=['TEMPLATE','ALG','numThreads','runtime','avgmem', 'maxmem'] )
        tmp_df = tmp_df.reset_index( drop=True )
        if timeMem_df is None:
            timeMem_df = tmp_df
        else:
            timeMem_df = timeMem_df.append( clean_table_hdr( tmp_df ))
        
#         timeMem_df = timeMem_df.reset_index( drop=True )
    
    return dist_df, jac_df, hess_df, timeMem_df


def groupTables( dist_df, jac_df, hess_df, timeMem_df, the_template_list, normalization_type='warp' ):

    [alg_name(x) for x in dist_df['ALG'].unique() ]

    # filter templates
    tmask = dist_df.apply( lambda x: (x['TEMPLATE'] in the_template_list ), axis=1)
    df = dist_df.loc[tmask]

    # Filter out appropriate rows and columns
    dist_table = df.loc[ (df.LABEL == -1) & (df.ALG != 'ALL') & \
                        ((df.normalization == normalization_type) | (df.normalization == 'na'))][['ALG','TEMPLATE','DISTANCE_mean','DISTANCE_std']]

    dist_table = clean_names( dist_table )

    # Tabulate times
    timeMem_df['CPUTIME'] = timeMem_df.apply(lambda x: float(x['runtime'])*float(x['numThreads']), axis=1) 
    timeMem_df['CPUTIME_hr'] = timeMem_df.apply(lambda x: float( x['CPUTIME']) / 3600., axis=1) 
    # timeMem_df['CPUTIME'] = timeMem_df.apply(lambda x: float(x['runtime']), axis=1) 

    times_stats = timeMem_df.groupby(['ALG','TEMPLATE']).agg({'CPUTIME_hr' : ['mean','median','var']})
    times_stats_flat = pd.DataFrame(times_stats.to_records())

    newcols = [ re.sub('[\'(),]','', c ).replace( ' ', '_') for c in times_stats_flat.columns ]
    times_stats_flat.columns = newcols
    times_stats_flat['ALG'] = times_stats_flat.apply(lambda x: alg_name(x['ALG']), axis=1)
    times_stats_flat['CPUTIME_hr_sd'] = times_stats_flat.apply(lambda x: float(math.sqrt(x['CPUTIME_hr_var'])), axis=1)


    times_stats_flat['TEMPLATE'] = times_stats_flat.apply(lambda x: template_name(x['TEMPLATE']), axis=1)
    times_stats_flat['TA'] = times_stats_flat.apply(lambda x: ''.join([x['TEMPLATE'],':',x['ALG']]), axis=1)

    time_table = times_stats_flat

    
    # Tabulate jacobians and hessians
    jac_table = jac_df.loc[ jac_df.label == -1 ]
    jac_table = clean_names( jac_table )

    hess_table = hess_df.loc[ hess_df.label == -1 ]
    hess_table = clean_names( hess_table )

    # combine the tables
    tmp_tbl = dist_table.set_index('TA').join( time_table.set_index('TA'), lsuffix='_dist')
    # tmp_tbl
    total_table = tmp_tbl.join( jac_table.set_index('TA'), lsuffix='_j' )
    total_table = total_table.join( hess_table.set_index('TA'), lsuffix='_j' )
    # total_table

    # total_table.query( 'label == -1')
    # total_table.query( 'TEMPLATE == "JFRC2013" & ALG == "elastixSFA"' )
    # total_table.query( 'ALG == "elastixSFA" & label == -1' )


    grouped_label_table = total_table.reset_index()[['ALG','TEMPLATE','CPUTIME_hr_mean','CPUTIME_hr_sd','DISTANCE_mean','DISTANCE_std','JAC_std','HES_mean']]
    grouped_label_table['DISTANCE_mean'] = grouped_label_table.apply(lambda x: float(x['DISTANCE_mean']), axis=1)

    return grouped_label_table
    

def make_scatter_plot( table, x_label, y_label, color_map=template_color_map ):
    ax = plt.gca()
    for i,row in table.iterrows():

        template = alg_name(row['TEMPLATE'].lstrip(' ').rstrip(' '))
        alg = alg_name(row['ALG'].lstrip(' ').rstrip(' '))
        s = "  " + alg

        c = color_map[row['TEMPLATE']]
        ax.annotate( s, (row[x_label], row[y_label] ), color=c, size=13 )

        # The below lines only add labels for one algorithm (per template)
        # so that duplicates don't appear in the legend
        # Use ANTs A becuse it shows up below
        if( alg == 'ANTs A' ):
            p = plt.scatter( row[x_label], row[y_label], color=c, label=template )
        else:
            p = plt.scatter( row[x_label], row[y_label], color=c )

    plt.xticks(fontsize=16)
    plt.yticks(fontsize=16)
    plt.legend()

    return ax