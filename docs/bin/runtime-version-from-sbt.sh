#!/usr/bin/env bash
# deliberately not using `--client`
sbt --no-colors "print akka-javasdk/akkaRuntimeVersion" | tail -n 1 | tr -d '[:space:]'
