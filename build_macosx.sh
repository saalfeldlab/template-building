#!/bin/bash


# remember current directory
startdir=$(pwd)

BASEDIRREL=$(dirname "$0")
BASEDIR=`pwd`"/${BASEDIRREL}"

cd $BASEDIR
cd ..

depdir=$(pwd)
echo "building dependencies in: $depdir"

echo "clone"
git clone https://github.com/bogovicj/n5-imglib2.git
cd n5-imglib2
echo "build n5-imglib2 repo"
mvn -Denforcer.skip=true clean compile install
cd $BASEDIR

echo "building template repo"
## Checkout and build the main repo
cd $BASEDIR # back to template building repo
mvn -Denforcer.skip=true clean compile install

# back to starting directory
cd $startdir
