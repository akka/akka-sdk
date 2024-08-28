#!/usr/bin/env bash
set -e

echo "updating sdk version to '$SDK_VERSION'"
# update poms with the version extracted from sbt dynver
mvn --quiet --batch-mode --activate-profiles patch-version versions:set -DnewVersion=${SDK_VERSION}

( # also needs to change akka-javasdk.version in parent pom
    cd akka-javasdk-parent
    sed -i.bak "s/<akka-javasdk.version>\(.*\)<\/akka-javasdk.version>/<akka-javasdk.version>$SDK_VERSION<\/akka-javasdk.version>/" pom.xml
    rm pom.xml.bak
)