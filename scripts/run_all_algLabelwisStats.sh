#!/bin/bash

source /groups/saalfeld/home/bogovicj/envbin/addMiniconda3
source activate py36

line="$1"
merge="$2"
conf="$3"

N="-1"
algList="cmtkCow cmtkCOG cmtkHideo antsRegOwl antsRegDog antsRegYang"
template="F-antsFlip_lo"
pyscript="/groups/saalfeld/home/bogovicj/dev/template/template-building/python/labelwise_stats_for_algPair.py"

python $pyscript "$line" "$algList" "$template" "$N" "$merge" "$conf"

#for a1 in $algList;
#do
#    for a2 in $algList;
#    do
#        if [[ $a1 == $a2 ]];
#        then
#            echo "same"
#            continue
#        fi
#        echo "$a1 $a2"
#
#        python $pyscript "$line" "$a1" "$a2" "$N"
#    done
#done


