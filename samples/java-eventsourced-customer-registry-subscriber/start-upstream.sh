#!/bin/bash

# make sure we have a directory for local service lookup
mkdir -p ~/.kalix/local

# Run the Maven command in the background
mvn -f ../java-eventsourced-customer-registry/pom.xml compile exec:java --no-transfer-progress &
echo "Waiting for service to be listed for local discovery"
while [ ! -f ~/.kalix/local/customer-registry.conf ]; do sleep 1; done
echo "Listing file present in ~/.kalix/local/customer-registry.conf (until JVM process shuts down)"