#!/bin/bash


# remember current directory
startdir=$(pwd)

BASEDIRREL=$(dirname "$0")
# where this script lives (i.e. template-building repo directory)
BASEDIR=$(readlink -f $BASEDIRREL)


cd $BASEDIR
cd ..

depdir=$(pwd)
echo "building dependencies in: $depdir"

## Grab dependencies not managed by maven yet
git clone https://github.com/saalfeldlab/janelia-util.git
cd janelia-util
mvn clean compile install
cd ..

git clone https://github.com/saalfeldlab/n5-utils.git
cd n5-utils
mvn clean compile install
cd ..

git clone https://github.com/saalfeldlab/imglib2-transform-import.git
cd imglib2-transform-import
mvn -Denforcer.skip=true clean compile install
cd ..

git clone https://github.com/saalfeldlab/imglib2-transform-import.git
cd imglib2-transform-import
mvn -Denforcer.skip=true clean compile install
cd ..

git clone https://github.com/bogovicj/n5-imglib2.git
cd n5-imglib2
mvn -Denforcer.skip=true clean compile install
cd ..

git clone https://github.com/bogovicj/skeleton2swc.git
cd skeleton2swc
mvn -Denforcer.skip=true clean compile install
cd ..


## Checkout and build the main repo
cd $BASEDIR # back to template building repo
mvn -Denforcer.skip=true clean compile install

# back to starting directory
cd $startdir
