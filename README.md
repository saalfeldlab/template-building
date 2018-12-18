# template-building
Scripts and code relating to building anatomical templates, outlined in the paper [Bogovic et al. "An unbiased template of the Drosophila brain and ventral nerve cord"](https://www.biorxiv.org/content/early/2018/07/25/376384).

Associated template images and transformations can be downloaded [here](https://www.janelia.org/open-science/jrc-2018-brain-templates).  We also provide fiji scripts for automatically [downloading multiple templates](https://raw.githubusercontent.com/saalfeldlab/template-building/master/fiji/download_templates.py) or [downloading multiple transformations at once](https://raw.githubusercontent.com/saalfeldlab/template-building/master/fiji/download_bridges.py).

## Installation
* Check out this repo: `git clone https://github.com/saalfeldlab/template-building.git`
* Run the build script: `./template-building/build.sh`


## Notes
### Dependencies
* Currently as of `2018-Dec-14` depends on [this branch](https://github.com/saalfeldlab/n5-imglib2/pull/6/commits/3c915776891c6175cd1af959c5c02bb5b6c4901c) of n5-imglib2
