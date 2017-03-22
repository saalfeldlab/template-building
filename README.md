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

## Examples

```bash
varianceLR <output image of distance> \
  <template> <flipped-template> <affine-that-flips-the-template-LR> \
  <subject-affine> <subject-deformation> \
  <flipped-subject-affine> <flipped-subject-deformation> \
  <affine-that-flips-the-subject>
```
