# This script will publish the current snapshot of all artifacts. 
# Including the maven parent pom.

set -e
export SDK_VERSION=$(sbt "print akka-javasdk/version" | tail -1)

echo
echo "------------------------------------------------------------------------"
echo "Publishing version $SDK_VERSION"
echo "------------------------------------------------------------------------"

sbt 'publishM2; +publishLocal'
(
  cd akka-javasdk-maven
  ../.github/patch-maven-versions.sh
  mvn clean install -Dskip.docker=true

  # cleanup
  rm -f pom.xml.versionsBackup
  rm -f */pom.xml.versionsBackup

  # revert, but only we didn't request to keep the modified files
  if [ "$1" != "--keep" ]; then
    git checkout pom.xml
    git checkout */pom.xml
  fi
)


echo $SDK_VERSION
