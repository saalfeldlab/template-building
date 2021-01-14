#!/bin/bash
mvn package -P fatjar,spark-local
cp target/saalfeldlab-template-building-0.1.2-n5spark-SNAPSHOT.jar jars/
