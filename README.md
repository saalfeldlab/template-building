# template-building
Scripts and code relating to building anatomical templates.

## Pipeline outline
* Decide the resolution and orientation at which to do the registration 
  * To keep the original image orientation ( downsampleGauss )
  * To rotate by 45 degrees to bring into "canonical orientation" ( canonicalDownsample )
* Decide whether to include mirror images of the subjects ( flipDownsample )
* Build an initial starting point, and pad if desired ( paddedAverage )
* Run the groupwise registration ( antsBuildTemplateGroupwise )
* Render at the original resolution ( runRenderHiRes )
* Average ( averageTally )

### Methods for template building
* ANTs groupwise (antsBuildTemplateGroupwise)
* ANTs single    (singleSubjectTemplateParallel.sh)
* CMTK groupwise (cmtkGroupwiseTemplate)
* CMTK single (cmtkSingleTemplate)

## Analysis
* Estimate registration variance (accuracy) by comparing flips ( varianceLR )
* Estimate a "symmetrizing" transformation ( runSymmetry )
* Get a tranformation to "canonical" orientation - where the y-axis is the axis of symmetry ( sym2Canonical )
* Fit a Rayleigh distribution pixel- (or local window)-wise across subjects (perWindowRayleigh)
### pipeline script
The `templateAnalysis` script runs many of the above analysis steps in sequence:
```
Usage:
  templateAnalysis [OPTIONS]
  -p [PREFIX] Specify the prefix for output of template building (Default='ALLF')
  -t [TEMPLATE] Specifies the template to use for analyis (Default='$PREFIX-template.nii.gz')
  -s Compute a symmetrizing transformation
  -v Compute flip-variance
  -r Estimate pixelwise Rayleigh distribution of flip-Variance
  -m Estimate pixelwise mean and variance of flip-Variance
  -c Estimate pixelwise percentiles distribution of flip-Variance (for percentiles = {0, 10, 50, 90, 100})
```

## Examples
### Finding a "symmetrizing" transformation
`runSymmetry` takes one argument - the image to be symmetrized.
Outputs a flipped image (about the line y=x :this makes sense for the 63x drosophila image data )
as well as two transformations : one that produces the flip, and one that registers the flip to the original.
The concatenation of these two transforms is the "symmetrizing" transformation
```bash
runSymmetry <the-image>
```
### Estimating registration accuracy via flips
```bash
varianceLR <output image of distance> \
  <template> <flipped-template> <affine-that-flips-the-template-LR> \
  <subject-affine> <subject-deformation> \
  <flipped-subject-affine> <flipped-subject-deformation> \
  <affine-that-flips-the-subject>
```

## Notes
### Dependencies
* Currently as of `2017-June-14` depends on [this branch](https://github.com/bogovicj/imglib2-realtransform/tree/deformationField) of imglib2-realtransform
* Nifti convertion depends on [mipav](https://mipav.cit.nih.gov/) and [this script](https://gist.github.com/bogovicj/4b22195fde421652518be230f1180037)

