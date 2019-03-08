import os
import sys
import math
import argparse
import itertools

import numpy as np

import scipy
from scipy.stats import ttest_ind

import pandas as pd



# The labels in 'JFRC2', used for label-wise evaluation
labels = [ 16, 64, 8, 32, 2, 4, 65, 66, 33, 67, 34, 17, 69, 70, 35, 71, 9, 18, 72, 36, 73, 74, 37, 75, 19, 76, 38, 77,
           39, 78, 79, 20, 5, 40, 80, 10, 81, 82, 83, 84, 85, 86, 11, 22, 23, 24, 12, 3, 6, 49, 50, 25, 51, 13, 52, 26,
           53, 27, 54, 55, 56, 28, 7, 14, 57, 58, 29, 59, 30, 60, 15, 61, 31, 62, 63 ]
label_names_file = '/groups/saalfeld/home/bogovicj/vfb/DrosAdultBRAINdomains/refData/Original_Index.tsv'

label_names = pd.read_csv( label_names_file, delimiter='\t', header=0 )
def get_label_name( label_id ):
    return label_names[ label_names[ 'Stack id' ] == label_id ][ 'JFRCtempate2010.mask130819' ].iloc[ 0 ]


# Functions
def perc_fun( perc ):
    def f( series ):
        return np.percentile( series, perc * 100 )
    return f


def p10( series ):
    return np.percentile( series, 10 )


def p90( series ):
    return np.percentile( series, 90 )


def ray_params_fl( series, eps=0.001 ):
    return scipy.stats.rayleigh.fit( series + eps, floc=0. )


def gam_params_fl( series, eps=0.001 ):
    return scipy.stats.gamma.fit( series + eps, floc=0. )


def gam_mode( series, eps=0.001 ):
    gam_params = scipy.stats.gamma.fit( series + eps, floc=0. )
    return (gam_params[ 0 ] - 1) * gam_params[ 2 ]


def gam_stats( series, eps=0.001 ):
    gam_params = scipy.stats.gamma.fit( series + eps, floc=0. )
    mean = gam_params[ 0 ] * gam_params[ 2 ]
    var = gam_params[ 0 ] * gam_params[ 2 ] * gam_params[ 2 ]
    mode = (gam_params[ 0 ] - 1) * gam_params[ 2 ]
    return mean, var, mode


def ray_stats( series, eps=0.001 ):
    meanMult = math.sqrt( math.pi / 2 )
    varMult = math.sqrt( 2 * math.log( 2 ) )
    ray_params = scipy.stats.rayleigh.fit( series + eps, floc=0. )
    mean = meanMult * ray_params[ 1 ]
    var = varMult * ray_params[ 1 ] * ray_params[ 1 ]
    mode = ray_params[ 0 ]
    return mean, var, mode


def mean_from_gamma( gamma_params ):
    return gamma_params[ 0 ] * gamma_params[ 2 ]


def var_from_gamma( gamma_params ):
    return gamma_params[ 0 ] * gamma_params[ 2 ] * gamma_params[ 2 ]


def mode_from_gamma( gamma_params ):
    return (gamma_params[ 0 ] - 1) * gamma_params[ 2 ]


def mean_from_ray( ray_params ):
    return math.sqrt( math.pi / 2 ) * ray_params[ 1 ]


def var_from_ray( ray_params ):
    return math.sqrt( 2 * math.log( 2 ) ) * ray_params[ 1 ] * ray_params[ 1 ]


def mode_from_ray( ray_params ):
    return ray_params[ 1 ]


def process_gamma_params( df_in, params_col=('DISTANCE', 'gam_params_fl') ):
    df_in[ ('DISTANCE', 'gam_mean') ] = df_in.apply( lambda x: mean_from_gamma( x[ params_col ] ), axis=1 )
    df_in[ ('DISTANCE', 'gam_var') ] = df_in.apply( lambda x: var_from_gamma( x[ params_col ] ), axis=1 )
    df_in[ ('DISTANCE', 'gam_mode') ] = df_in.apply( lambda x: mode_from_gamma( x[ params_col ] ), axis=1 )


def process_ray_params( df_in, params_col=('DISTANCE', 'ray_params_fl') ):
    df_in[ ('DISTANCE', 'ray_mean') ] = df_in.apply( lambda x: mean_from_ray( x[ params_col ] ), axis=1 )
    df_in[ ('DISTANCE', 'ray_var') ] = df_in.apply( lambda x: var_from_ray( x[ params_col ] ), axis=1 )
    df_in[ ('DISTANCE', 'ray_mode') ] = df_in.apply( lambda x: mode_from_ray( x[ params_col ] ), axis=1 )


def main( dest_file, base_dir, template_list, alg_list, eval_arg, line_list, datapattern ):

    df_tot = pd.DataFrame( columns=['TEMPLATE','ALG','LINE','LABEL','DISTANCE'])

    # Read the data
    for template, alg, line in itertools.product( template_list, alg_list, line_list ):
        # Read label stats
        datFile = os.path.join( base_dir, template, alg, eval_arg, datapattern.format(line) )
        print( 'loading ', datFile )

        df_line = pd.read_csv( datFile, header=None, names=['LABEL','DISTANCE'] )
        df_line['LINE'] = line
        df_line['TEMPLATE'] = template
        df_line['ALG'] = alg

        df_tot = df_tot.append( df_line )

    print( len( df_tot ))


    # ## ORGANIZE
    # drop the LINE column
    df = df_tot.drop(['LINE'], axis=1)
    df_atl = df.groupby(['ALG','TEMPLATE','LABEL'],as_index=False)
    df_at = df.groupby(['ALG','TEMPLATE'],as_index=False)
    df_l = df.groupby(['TEMPLATE','LABEL'],as_index=False)
    df_t = df.groupby(['TEMPLATE'],as_index=False)

    agg_dict = { 'DISTANCE' : ['count', 'median', 'mean', 'var', gam_params_fl, ray_params_fl, p10, p90]}
    print( agg_dict )

    # Compute statistics over labels
    # Split algorithms, split labels
    print( 'stats by label' )
    df_atl_stats = df_atl.agg( agg_dict )

    # Split algorithms, group labels
    print( 'stats over all labels' )
    df_at_stats = df_at.agg( agg_dict )
    df_at_stats['LABEL'] = -1
    df_all = df_atl_stats.append( df_at_stats ) # append

    # Group algorithms, split labels
    print( 'stats over all algorithms, split by labels' )
    df_lstats = df_l.agg( agg_dict )
    df_lstats['ALG'] = 'ALL'
    df_all = df_all.append( df_lstats ) # append

    # Group algorithms, group labels
    print( 'stats over all algorithms, all labels' )
    df_grandstats = df_t.agg( agg_dict )
    df_grandstats['LABEL'] = -1
    df_grandstats['ALG'] = 'ALL'

    df_all = df_all.append( df_grandstats ) # append

    process_gamma_params( df_all )
    process_ray_params( df_all )

    # Label-wise statistics
    print( 'ouput table len: ', len(df_all ))

    print( 'writing to ', dest_file )
    df_all.to_csv( dest_file )
    print( 'done' )

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description='Tabulates statistics of skeleton distances.',
        epilog='')
    parser.add_argument('-d', '--dir', help="Base directory holding results")
    parser.add_argument('-o', '--output', help="Output file name", default=None)
    parser.add_argument('-t', '--template', action='append',
                        help="Template name(s). Can pass multiple template arguments.", default=None )
    parser.add_argument('-a', '--alg', action='append',
                        help="Algorithm name(s). Can pass multiple alg arguments.", default=None )
    parser.add_argument('-e', '--evaldir', help="Directory holding evaluation results",
                        default='evalCompWarpNorm' )
    parser.add_argument('--datapattern', help='The pattern used to match data files.',
                        default='combined_labelData_line{}.csv')
    parser.add_argument('--lines', help='The drosophila cell lines', default=[0, 1, 2, 3])

    args = parser.parse_args()

    if len(sys.argv) == 1:
        parser.print_help(sys.stderr)
        sys.exit(1)

    main(args.output, args.dir, args.template, args.alg, args.evaldir, args.lines, args.datapattern)