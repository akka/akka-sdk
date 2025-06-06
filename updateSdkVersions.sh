#!/usr/bin/env bash

# this script is meant to be used after a new SDK version is out
# to facilitate the update of all the places where we usually depend on the latest version

# provide the new sdk version you want the project to be updated to
if [[ -z "$SDK_VERSION" ]]; then
    echo "Must provide SDK_VERSION in environment" 1>&2
    exit 1
fi

updateJavaSamples() {
  echo ">>> Updating pom versions to $SDK_VERSION"
  PROJS=$(find $1 -type f -name "pom.xml")
  for i in ${PROJS[@]}
  do
    echo "Updating pom for: $i"
    # we only want to update the first occurrence of <version>, the one belonging the parent-pom
    awk '/<version>[^<]*<\/version>/ && !subyet {sub("<version>[^<]*<\/version>", "<version>"ENVIRON["SDK_VERSION"]"</version>"); subyet=1} 1' $i > temp && mv temp $i
  done
}


updateMavenPlugin() {
  echo ">>> Updating maven plugin to $SDK_VERSION"
  (
    cd akka-javasdk-maven &&
    ../.github/patch-maven-versions.sh
  )
}

DEFAULT_SAMPLES="./samples"
option="${1}"
sample="${2:-$DEFAULT_SAMPLES}"
case ${option} in
   java) updateJavaSamples $sample
      ;;
   plugin) updateMavenPlugin
      ;;
   all)
     updateJavaSamples $sample
     updateMavenPlugin
      ;;
   *)
      echo "`basename ${0}`:usage: java|plugin|all [project-folder]"
      echo "e.g.: `basename ${0}` java ./samples/customer-registry-kafka-quickstart/"
      exit 1 # Command to come out of the program with status 1
      ;;
esac