#!/bin/bash
# Estimates shape updates for female and male subjects seperately, then 
# Usage:
# shapeUpdateByPartition.sh <dim> <template> <templatename> <outputname> <gradientstep> <pattern1> <pattern2>


# local declaration of values
dim=$1
template=$2
templatename=$3
outputname=$4
gradientstep=$5
pattern1=$6
pattern2=$7


afftype=".txt"

function shapeupdatesingle {

    # debug only
    echo $dim
    echo ${template}
    echo ${templatename}
    echo ${outputname}
    echo ${outputname}*formed.nii*
    echo ${gradientstep}
    echo ${PATTERN}

    # We find the average warp to the template and apply its inverse to the template image
    # This keeps the template shape stable over multiple iterations of template building

    echo
    echo "--------------------------------------------------------------------------------------"
    echo " shapeupdatetotemplate 1"
    echo "--------------------------------------------------------------------------------------"
    ${ANTSPATH}/AverageImages $dim AVGPAT-${PATTERN}${template} 1 *${PATTERN}*formed.nii*

    echo
    echo "--------------------------------------------------------------------------------------"
    echo " shapeupdatetotemplate 2"
    echo "--------------------------------------------------------------------------------------"


    ${ANTSPATH}/AverageImages $dim PAT-${PATTERN}${templatename}warp.nii.gz 0 `ls *${PATTERN}*Warp.nii* | grep -v "InverseWarp"`


    echo
    echo "--------------------------------------------------------------------------------------"
    echo " shapeupdatetotemplate 4"
    echo "--------------------------------------------------------------------------------------"

    rm -f PAT-${PATTERN}${templatename}Affine${afftype}

    echo
    echo "--------------------------------------------------------------------------------------"
    echo " shapeupdatetotemplate 5"
    echo "--------------------------------------------------------------------------------------"

    # Average Affine transform for the transforms matching this pattern
    ${ANTSPATH}/AverageAffineTransform ${dim} PAT-${PATTERN}${templatename}Affine${afftype} ${outputname}*Affine${afftype}



}

PATTERN=${pattern1}
shapeupdatesingle $dim $template $templatename $outputname $gradientstep $PATTERN

echo "######################################################################################"
echo "######################################################################################"
echo "######################################################################################"

PATTERN=${pattern2}
shapeupdatesingle $dim $template $templatename $outputname $gradientstep $PATTERN

# Here we should have two average Affines, two average warps, and two average templates

#########################
# Average the templates
#########################
${ANTSPATH}/AverageImages $dim ${template} 1 AVGPAT*.nii*

#######################
# Average the affines
#######################
${ANTSPATH}/AverageAffineTransform ${dim} ${templatename}Affine${afftype} PAT-*Affine${afftype}


###############################################################################
# Replace both warp fields with appropriately affinely transformed warp fields
###############################################################################
# pattern 1
${ANTSPATH}/WarpImageMultiTransform ${dim} PAT-${pattern1}${templatename}warp.nii.gz PAT-${pattern1}${templatename}warp.nii.gz -i  ${templatename}Affine${afftype} -R ${template}

# pattern 2
${ANTSPATH}/WarpImageMultiTransform ${dim} PAT-${pattern2}${templatename}warp.nii.gz PAT-${pattern2}${templatename}warp.nii.gz -i  ${templatename}Affine${afftype} -R ${template}



##############################
# Average the two warp fields
##############################
${ANTSPATH}/AverageImages $dim ${templatename}warp.nii.gz 0 PAT*warp.nii.gz


###################################
# Multiply result by gradient step
###################################
echo
echo "--------------------------------------------------------------------------------------"
echo " shapeupdatetotemplate 3"
echo "--------------------------------------------------------------------------------------"

${ANTSPATH}/MultiplyImages $dim ${templatename}warp.nii.gz ${gradientstep} ${templatename}warp.nii.gz


####################
# Move the template
####################
#TODO why is ${templatename}warp.nii.gz repeated 4 times!?!?
# This is also the case in the original buildTemplateParallel.sh script
${ANTSPATH}/WarpImageMultiTransform ${dim} ${template} ${template} -i ${templatename}Affine${afftype} ${templatename}warp.nii.gz ${templatename}warp.nii.gz ${templatename}warp.nii.gz ${templatename}warp.nii.gz -R ${template}


echo
echo "--------------------------------------------------------------------------------------"
echo " shapeupdatetotemplate 6"
echo "--------------------------------------------------------------------------------------"
echo
${ANTSPATH}/MeasureMinMaxMean ${dim} ${templatename}warp.nii.gz ${templatename}warplog.txt 1
