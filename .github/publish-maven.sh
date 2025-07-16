#!/usr/bin/env bash
set -e

if [ -z "${SDK_VERSION}" ];
then
  echo "expected SDK_VERSION to be set"
  exit 1
fi
if [ -z "${SONATYPE_USERNAME}" ];
then
  echo "expected SONATYPE_USERNAME to be set"
  exit 1
fi
if [ -z "${SONATYPE_PASSWORD}" ];
then
  echo "expected SONATYPE_PASSWORD to be set"
  exit 1
fi
if [ -z "${PGP_PASSPHRASE}" ];
then
  echo "expected PGP_PASSPHRASE to be set"
  exit 1
fi
if [ -z "${PGP_SECRET}" ];
then
  echo "expected PGP_SECRET to be set"
  exit 1
fi

if [[ $SDK_VERSION == *SNAPSHOT* ]]
then
  echo "This version ["$SDK_VERSION"] is a snapshot. Not publishing to Maven Central Portal"
  exit 0
fi

cd akka-javasdk-maven
../.github/patch-maven-versions.sh

# create Maven settings.xml with credentials for repository publishing
# info
mkdir -p ~/.m2
cat <<EOF >~/.m2/settings.xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0 http://maven.apache.org/xsd/settings-1.1.0.xsd">
  <servers>
      <server>
        <id>central</id>
        <username>${SONATYPE_USERNAME}</username>
        <password>${SONATYPE_PASSWORD}</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>central</id>
      <repositories>
        <repository>
            <id>akka-repository</id>
            <name>Akka library repository</name>
            <!-- This only works for Akka's internal CI/CD -->
            <url>https://repo.akka.io/maven/github_actions</url>
        </repository>
      </repositories>
      <pluginRepositories>
          <pluginRepository>
              <id>akka-repository</id>
              <name>Akka library repository</name>
              <!-- This only works for Akka's internal CI/CD -->
              <url>https://repo.akka.io/maven/github_actions</url>
          </pluginRepository>
      </pluginRepositories>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.passphrase><![CDATA[${PGP_PASSPHRASE}]]></gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
EOF

# import the artefact signing key
echo "${PGP_SECRET}" | base64 -d | gpg --import --batch

# Maven deploy with profile `release`
# mvn --quiet --batch-mode --activate-profiles release deploy
mvn --batch-mode --activate-profiles release -Dskip.docker=true -Dskip.deploy=false deploy
