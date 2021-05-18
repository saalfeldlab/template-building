#!/bin/bash
mvn package -P fatjar,spark-provided
cp target/saalfeldlab-template-building-0.1.2-n5spark-SNAPSHOT.jar jars/saalfeldlab-template-building-0.1.2-n5spark-SNAPSHOT-provided.jar
