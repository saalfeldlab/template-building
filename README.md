# template-building
Scripts and code relating to building anatomical templates, outlined in the paper [Bogovic et al. "An unbiased template of the Drosophila brain and ventral nerve cord"](https://www.biorxiv.org/content/early/2018/07/25/376384).

Associated template images and transformations can be downloaded [here](https://www.janelia.org/open-science/jrc-2018-brain-templates).  We also provide fiji scripts for automatically [downloading multiple templates](https://raw.githubusercontent.com/saalfeldlab/template-building/master/fiji/download_templates.py) or [downloading multiple transformations at once](https://raw.githubusercontent.com/saalfeldlab/template-building/master/fiji/download_bridges.py).  [See here](https://imagej.net/Scripting#Using_the_script_editor) for help on running Fiji scripts.

## Installation
#### Prerequisites
* [Apache maven](https://maven.apache.org/index.html)
#### Instructions (Unix-based: Linux/Mac)
* Check out this repo: `git clone https://github.com/saalfeldlab/template-building.git`
* Run the build script: 
  * Linux: `./template-building/build.sh`
  * Mac: `./template-building/build_macosx.sh`
* To install into your Fiji: `mvn -Dimagej.app.directory=<path to your Fiji> -Denforcer.skip=true`

## Documentation

* [Usage and examples](https://github.com/saalfeldlab/template-building/wiki/Usage-examples)
* [Support for transformations](https://github.com/saalfeldlab/template-building/wiki/Transformations)
* [Registration algorithms and parameter settings](https://github.com/saalfeldlab/template-building/wiki/Registration-algorithms-and-parameter-settings)
* [Software for evaluating registration](https://github.com/saalfeldlab/template-building/wiki/Evaluation-Documentation)

## Notes
### Dependencies
* Currently as of `2018-Dec-14` depends on [this branch](https://github.com/saalfeldlab/n5-imglib2/pull/6/commits/3c915776891c6175cd1af959c5c02bb5b6c4901c) of n5-imglib2
* As of `2019-Jan-21` it also depends on [this branch](https://github.com/bogovicj/imglib2-realtransform/tree/explicitInv) of imglib2-realtransform
