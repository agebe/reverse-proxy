#!/bin/bash
set -e
TOMCAT=apache-tomcat-10.1.23
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
cd $SCRIPT_DIR
gradle clean war
tar -xzf local/${TOMCAT}.tar.gz -C build/
rm -rf build/${TOMCAT}/webapps/*
cp -v build/libs/*.war build/${TOMCAT}/webapps/ROOT.war
cp -v tomcat/server.xml build/${TOMCAT}/conf/
cp -v tomcat/logging.properties build/${TOMCAT}/conf/
build/${TOMCAT}/bin/catalina.sh run

