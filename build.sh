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

git clone https://github.com/bogovicj/imglib2-realtransform.git
cd imglib2-realtransform
mvn clean compile install
cd ..

git clone https://github.com/bogovicj/n5-imglib2.git
cd n5-imglib2
mvn -Denforcer.skip=true clean compile install
cd ..


## Checkout and build the main repo
cd $BASEDIR # back to template building repo
mvn -Denforcer.skip=true clean compile install

# back to starting directory
cd $startdir
