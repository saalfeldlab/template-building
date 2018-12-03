#!/bin/bash

# Grab dependencies not managed by maven yet
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
git clone https://github.com/saalfeldlab/template-building.git
cd template-building
mvn clean compile install
