#!/bin/bash 

mkdir third-party
cd third-party
wget http://www.rforge.net/Rserve/files/REngine.jar
wget http://www.rforge.net/Rserve/files/RserveEngine.jar

mvn deploy:deploy-file -DgroupId=local -DartifactId=RserveEngine \
  -Dversion=1.7.0 -Dpackaging=jar -Dfile=RserveEngine.jar \
  -Durl=file:repo

mvn deploy:deploy-file -DgroupId=local -DartifactId=REngine \
  -Dversion=1.7.0 -Dpackaging=jar -Dfile=REngine.jar \
  -Durl=file:repo

ln -s /tmp/Rserv /resources/generated