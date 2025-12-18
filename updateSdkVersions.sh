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
    # only update the <version> inside <parent> block where <artifactId> is akka-javasdk-parent
    awk '
      /<parent>/ { in_parent=1 }
      in_parent && /<artifactId>akka-javasdk-parent<\/artifactId>/ { found_akka_parent=1 }
      in_parent && found_akka_parent && /<version>[^<]*<\/version>/ && !subyet {
        sub("<version>[^<]*<\/version>", "<version>"ENVIRON["SDK_VERSION"]"</version>")
        subyet=1
        updated=1
      }
      /<\/parent>/ { in_parent=0; found_akka_parent=0 }
      { print }
      END { exit !updated }
    ' $i > temp && mv temp $i || rm temp
  done
}

DEFAULT_SAMPLES="./samples"
option="${1}"
sample="${2:-$DEFAULT_SAMPLES}"
case ${option} in
   java) updateJavaSamples $sample
      ;;
   all)
     updateJavaSamples $sample
      ;;
   *)
      echo "`basename ${0}`:usage: java|all [project-folder]"
      echo "e.g.: `basename ${0}` java ./samples/customer-registry-kafka-quickstart/"
      exit 1 # Command to come out of the program with status 1
      ;;
esac
