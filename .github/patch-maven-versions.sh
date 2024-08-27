#!/usr/bin/env bash
set -e

echo "updating sdk version to '$SDK_VERSION'"
# update poms with the version extracted from sbt dynver
mvn --quiet --batch-mode --activate-profiles patch-version versions:set -DnewVersion=${SDK_VERSION}

( # also needs to change akka-platform-sdk.version in parent pom
    cd akka-platform-parent
    sed -i.bak "s/<akka-platform-sdk.version>\(.*\)<\/akka-platform-sdk.version>/<akka-platform-sdk.version>$SDK_VERSION<\/akka-platform-sdk.version>/" pom.xml
    rm pom.xml.bak
)