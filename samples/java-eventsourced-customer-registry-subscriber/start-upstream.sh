#!/bin/bash

# Run the Maven command in the background
mvn -f ../java-eventsourced-customer-registry/pom.xml compile exec:java --no-transfer-progress &