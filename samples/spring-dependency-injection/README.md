# Implementing an Akka service with dependency injection based on Spring Framework

This project provides an example for how to take advantage of Spring Dependency Injection in an Akka service.

## Designing

To understand the Akka concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

To start your Akka service locally, run:

```shell
mvn compile exec:java
```

## Exercise the service

This sample does not expose any Endpoints, since the main goal is to show how to configure a `DependencyProvider` in the `CounterSetup` class.

## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and then deploy your service from the UI.

Build container image:

```shell
mvn install -DskipTests
```

You can also deploy your service from the CLI. Install the `akka` CLI as documented in
[Install Akka CLI](https://doc.akka.io/akka-cli/index.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy spring-dependency-injection spring-dependency-injection:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.
