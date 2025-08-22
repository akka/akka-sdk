#!/usr/bin/env bash
# publish current snapshot locally and updates passed in samples to use the snapshot.
# this is mainly used for local tests. Watch out to no commit modified files.

if [ $# -eq 0 ]; then 
  echo "No arguments provided!"
  echo "This scripted is used to update sample versions to the latest snapshot."
  echo "You should call it by passing one or more samples as arguments."
  echo "eg: ./updateSampleVersions.sh samples/key-value-counter samples/event-sourced-counter-brokers"
  echo "or simply using bash expansion..."
  echo "eg: ./updateSampleVersions.sh samples/*"
else 
  export SDK_VERSION=$(sbt "print akka-javasdk/version" | tail -1)
  echo "SDK version: $SDK_VERSION"
  sbt 'publishM2; +publishLocal'

  echo "------------------------------------------------------------------------"
  for i in "$@"
  do
    echo
    if [ -f $i/pom.xml ]; then
      echo "Updating pom.xml file in: $i"
      sh ./updateSdkVersions.sh java $i
    fi
  done
  echo "------------------------------------------------------------------------"
fi
