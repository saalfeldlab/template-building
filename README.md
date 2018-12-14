# template-building
Scripts and code relating to building anatomical templates, outlined in the paper [Bogovic et al. "An unbiased template of the Drosophila brain and ventral nerve cord"](https://www.biorxiv.org/content/early/2018/07/25/376384).

Associated template images and transformations can be downloaded [here](https://www.janelia.org/open-science/jrc-2018-brain-templates).

## Installation
* Check out this repo: `git clone https://github.com/saalfeldlab/template-building.git`
* Run the build script: `./template-building/build.sh`


## Notes
### Dependencies
* Currently as of `2017-June-14` depends on [this branch](https://github.com/bogovicj/imglib2-realtransform/tree/deformationField) of imglib2-realtransform
* Nifti convertion depends on [mipav](https://mipav.cit.nih.gov/) and [this script](https://gist.github.com/bogovicj/4b22195fde421652518be230f1180037)

