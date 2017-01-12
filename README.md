# template-building
Scripts and code relating to building anatomical templates.

## Pipeline outline
* Decide the resolution at which to do the registration ( downsampleGauss )
* Decide whether to include mirror images of the subjects ( flipDownsample )
* Build an initial starting point, and pad if desired ( paddedAverage )
* Run the groupwise registration ( antsBuildTemplateGroupwise )
* Render at the original resolution ( runRenderHiRes )
* Average ( averageTally )
