# Akka SDK tests

This module contains tests that tests a running service, using the testkit. 

Unit tests for the SDK lives directly in the SDK module.

Note that the tests has a different root package on purpose to not be in akka namespace and able to accidentally access internals.

Test components are found under `akkajavasdk.components` and are manually registered in `META-INF/akka-javasdk-components.conf`