# template-building
Scripts and code relating to building anatomical templates.

## Pipeline outline
* Decide the resolution at which to do the registration ( downsampleGauss )
* Decide whether to include mirror images of the subjects ( flipDownsample )
* Build an initial starting point, and pad if desired ( paddedAverage )
* Run the groupwise registration ( antsBuildTemplateGroupwise )
* Render at the original resolution ( runRenderHiRes )
* Average ( averageTally )

## Analysis
* Estimate registration variance (accuracy) by comparing flips ( varianceLR )
* Estimate a "symmetrizing" transformation ( runSymmetry )
* Get a tranformation to "canonical" orientation - where the y-axis is the axis of symmetry ( sym2Canonical )

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
