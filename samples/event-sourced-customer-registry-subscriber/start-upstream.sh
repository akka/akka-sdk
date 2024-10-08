#!/bin/env bash

# Run the Maven command in the background
mvn -f ../event-sourced-customer-registry/pom.xml compile exec:java --no-transfer-progress &

sleep 7
echo "Waiting for service to be listed for local discovery"
while [ ! -f ~/.akka/local/customer-registry.conf ]; do sleep 1; done
echo "Listing file present in ~/.akka/local/customer-registry.conf (until JVM process shuts down)"
