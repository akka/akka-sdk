#!/usr/bin/env bash
set -e

if [ -z "${SDK_VERSION}" ];
then
  echo "expected SDK_VERSION to be set."
  exit 1
fi
if [ -z "${CLOUDSMITH_MACHINE_USER}" ];
then
  echo "expected CLOUDSMITH_MACHINE_USER to be set."
  exit 1
fi
if [ -z "${CLOUDSMITH_MACHINE_PASSWORD}" ];
then
  echo "expected CLOUDSMITH_MACHINE_PASSWORD to be set."
  exit 1
fi

cd akka-javasdk-maven
../.github/patch-maven-versions.sh

# create Maven settings.xml with credentials for repository publishing
mkdir -p ~/.m2
cat <<EOF >~/.m2/settings.xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0 http://maven.apache.org/xsd/settings-1.1.0.xsd">
  <servers>
    <server>
        <id>akka-repo</id>
        <username>${CLOUDSMITH_MACHINE_USER}</username>
        <password>${CLOUDSMITH_MACHINE_PASSWORD}</password>
    </server>
     <server>
         <id>akka-snapshot-repo</id>
         <username>${CLOUDSMITH_MACHINE_USER}</username>
         <password>${CLOUDSMITH_MACHINE_PASSWORD}</password>
     </server>
  </servers>
</settings>
EOF

mvn --activate-profiles release -Dskip.docker=true -Dskip.deploy=false deploy
