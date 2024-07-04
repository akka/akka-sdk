#!/usr/bin/env bash
echo "Extracting version from sbt build"
# deliberately not using `--client`
sbt --no-colors "print java-sdk/version" | tail -n 1 | tr -d '\n' > ~/akka-platform-sdk-version.txt
