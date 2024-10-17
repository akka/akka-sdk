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

To deploy your service, install the `akka` CLI as documented in
[Install Akka CLI](https://doc.akka.io/akka-cli/index.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://doc.akka.io/operations/container-registries.html)
for more information on how to make your docker image available to Akka.

Finally, you can use the [Akka Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy kalix:deploy` which
will conveniently package, publish your docker image, and deploy your service to Akka, or by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `akka` CLI.
