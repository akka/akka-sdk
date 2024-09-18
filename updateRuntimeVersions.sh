#!/usr/bin/env bash

# USAGE:
# > RUNTIME_VERSION=1.0.31 ./updateRuntimeVersions.sh

# this script is meant to be used after a new Runtime version is out
# to facilitate the update of all the places where we usually depend on the latest version

# provide the new Runtime version you want the project to be updated to
if [[ -z "$RUNTIME_VERSION" ]]; then
    echo "Must provide RUNTIME_VERSION in environment" 1>&2
    exit 1
fi

echo ">>> Updating Dependencies.scala"
sed -i.bak "s/sys.props.getOrElse(\"kalix-runtime.version\", \"\(.*\)\")/sys.props.getOrElse(\"kalix-runtime.version\", \"$RUNTIME_VERSION\")/" ./project/Dependencies.scala
rm ./project/Dependencies.scala.bak

echo ">>> Updating akka-javasdk-maven/akka-javasdk-parent/pom.xml"
sed -i.bak "s/<kalix-runtime\.version>[^<]*/<kalix-runtime.version>$RUNTIME_VERSION/" ./akka-javasdk-maven/akka-javasdk-parent/pom.xml
rm ./akka-javasdk-maven/akka-javasdk-parent/pom.xml.bak