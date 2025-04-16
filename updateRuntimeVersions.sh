#!/usr/bin/env bash

# USAGE:
# > ./updateRuntimeVersions.sh 1.0.31
# or 
# > RUNTIME_VERSION=1.0.31 ./updateRuntimeVersions.sh

# this script is meant to be used after a new Runtime version is out
# to facilitate the update of all the places where we usually depend on the latest version

# provide the new Runtime version you want the project to be updated to

if [[ $1 ]];then 
    RUNTIME_VERSION=$1
fi    

if [[ -z "$RUNTIME_VERSION" ]]; then    
    echo "Must provide RUNTIME_VERSION in environment or pass it as an argument" 1>&2
    exit 1
fi

echo ">>> Updating Dependencies.scala"
sed -i.bak "s/sys.props.getOrElse(\"akka-runtime.version\", \"\(.*\)\")/sys.props.getOrElse(\"akka-runtime.version\", \"$RUNTIME_VERSION\")/" ./project/Dependencies.scala
rm ./project/Dependencies.scala.bak

echo ">>> Updating akka-javasdk-maven/akka-javasdk-parent/pom.xml"
sed -i.bak "s/<akka-runtime\.version>[^<]*/<akka-runtime.version>$RUNTIME_VERSION/" ./akka-javasdk-maven/akka-javasdk-parent/pom.xml
rm ./akka-javasdk-maven/akka-javasdk-parent/pom.xml.bak
