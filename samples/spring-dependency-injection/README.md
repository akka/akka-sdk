# Implementing an Akka service with dependency injection based on Spring Framework

This project provides an example for how to take advantage of Spring Dependency Injection in an Akka service.

## Designing

To understand the Akka concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

## Building

---

### Secure Repository Token

Building requires a secure repository token, which is set up as part of [Akka CLI](https://doc.akka.io/getting-started/quick-install-cli.html)'s `akka code init` command.

If you still need to configure your system with the token there are two additional ways:

1. Use the Akka CLI's `akka code token` command and follow the instructions.
2. Set up the token manually as described [here](https://account.akka.io/token).

---

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

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/operations/cli/installation.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy spring-dependency-injection spring-dependency-injection:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.
