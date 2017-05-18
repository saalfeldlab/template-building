#!/bin/bash
# Usage:
# shapeUpdateOriginal.sh <dim> <template> <templatename> <outputname> <gradientstep>


# local declaration of values
dim=$1
template=$2
templatename=$3
outputname=$4
gradientstep=$5


afftype=".txt"

# debug only
echo $dim
echo ${template}
echo ${templatename}
echo ${outputname}
echo ${outputname}*formed.nii*
echo ${gradientstep}

# We find the average warp to the template and apply its inverse to the template image
# This keeps the template shape stable over multiple iterations of template building

echo
echo "--------------------------------------------------------------------------------------"
echo " shapeupdatetotemplate 1"
echo "--------------------------------------------------------------------------------------"
${ANTSPATH}/AverageImages $dim ${template} 1 ${outputname}*formed.nii*

echo
echo "--------------------------------------------------------------------------------------"
echo " shapeupdatetotemplate 2"
echo "--------------------------------------------------------------------------------------"

${ANTSPATH}/AverageImages $dim ${templatename}warp.nii.gz 0 `ls ${outputname}*Warp.nii* | grep -v "InverseWarp"`

echo
echo "--------------------------------------------------------------------------------------"
echo " shapeupdatetotemplate 3"
echo "--------------------------------------------------------------------------------------"

${ANTSPATH}/MultiplyImages $dim ${templatename}warp.nii.gz ${gradientstep} ${templatename}warp.nii.gz


#echo
#echo "--------------------------------------------------------------------------------------"
#echo " shapeupdatetotemplate 4"
#echo "--------------------------------------------------------------------------------------"
#
#rm -f ${templatename}Affine${afftype}

echo
echo "--------------------------------------------------------------------------------------"
echo " shapeupdatetotemplate 5"
echo "--------------------------------------------------------------------------------------"

# Averaging and inversion code --- both are 1st order estimates.
#    if [ ${dim} -eq 2   ] ; then
#      ANTSAverage2DAffine ${templatename}Affine${afftype} ${outputname}*Affine${afftype}
#    elif [ ${dim} -eq 3  ] ; then
#      ANTSAverage3DAffine ${templatename}Affine${afftype} ${outputname}*Affine${afftype}
#    fi

# Average Affine transform
${ANTSPATH}/AverageAffineTransform ${dim} ${templatename}Affine${afftype} ${outputname}*Affine${afftype}

# Replace warp field with an appropriately affine-ly transformed warp field
${ANTSPATH}/WarpImageMultiTransform ${dim} ${templatename}warp.nii.gz ${templatename}warp.nii.gz -i  ${templatename}Affine${afftype} -R ${template}

# Apply the new transformation to the average template
${ANTSPATH}/WarpImageMultiTransform ${dim} ${template} ${template} -i ${templatename}Affine${afftype} ${templatename}warp.nii.gz ${templatename}warp.nii.gz ${templatename}warp.nii.gz ${templatename}warp.nii.gz -R ${template}

echo
echo "--------------------------------------------------------------------------------------"
echo " shapeupdatetotemplate 6"
echo "--------------------------------------------------------------------------------------"
echo
${ANTSPATH}/MeasureMinMaxMean ${dim} ${templatename}warp.nii.gz ${templatename}warplog.txt 1
