#!/usr/bin/env bash

clojure -Spom
clojure -A:jar
mvn deploy:deploy-file -Dfile=s3-mvn-upload.jar -DpomFile=pom.xml -DrepositoryId=clojars -Durl=https://clojars.org/repo/