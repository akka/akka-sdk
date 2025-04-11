# Run a service locally

Running a service locally is helpful to test and debug. The following sections provide commands for starting and stopping a single service locally.

## Prerequisites

In order to run your service locally, you’ll need to have the following prerequisites:

## Starting your service

As an example, we will use the [Shopping Cart](shopping-cart-quickstart.adoc) sample.

To start your service locally, run the following command from the root of your project:

```command line
mvn compile exec:java
```

## Invoking your service

After you start the service it will accept invocations on `localhost:9000`. You can use [cURL, window="new"](https://curl.se) in another shell to invoke your service.

### Using cURL

Add an item to the shopping cart:

```command window
curl -i -XPUT -H "Content-Type: application/json" localhost:9000/carts/123/item -d '
{"productId":"akka-tshirt", "name":"Akka Tshirt", "quantity": 10}'
```

Get cart state:

```command window
curl localhost:9000/carts/123
```

## Shutting down the service

Use `Ctrl+c` to shut down the service.

## Run from IntelliJ

The samples and archetype include a run configuration for IntelliJ. In the toolbar you should see:

![run-intellij](run-intellij.png)

This is a Maven run configuration for `mvn compile exec:java`. You can also run this with the debugger and set breakpoints in the components.

## Local console

The local console gives you insights of the services that you are running locally.

To run the console you need to install:

* [Docker Engine, window="new"](https://docs.docker.com/get-started/get-docker/) {minimum_docker_version} or later
* [Akka CLI](reference:cli/installation.adoc)

Start the console with the following command from a separate terminal window:

```command window
akka local console
```

Open [window="new"](http://localhost:3000/)

Start one or more services as described in [Starting your service](#starting-your-service) and they will show up in the console. You can restart the services without restarting the console.

![local-console](local-console.png)

## Running a service with persistence enabled

By default, when running locally, persistence is disabled. This means the Akka Runtime will use an in-memory data store for the state of your services. This is useful for local development since it allows you to quickly start and stop your service without having to worry about cleaning the database.

However, if you want to run your service with persistence enabled to keep the data when restarting, you can configure
the service in `application.conf` with `akka.javasdk.dev-mode.persistence.enabled=true` or as a system property when starting the service locally.

```command line
mvn compile exec:java -Dakka.javasdk.dev-mode.persistence.enabled=true
```

To clean the local database look for `db.mv.db` file in the root of your project and delete it.

## Running a service with broker support

By default, when running locally, broker support is disabled. When running a service that declares consumers or producers locally, you need to configure the broker with property `akka.javasdk.dev-mode.eventing.support=kafka` in `application.conf` or as a system property when starting the service.

```command line
mvn compile exec:java -Dakka.javasdk.dev-mode.eventing.support=kafka
```

For Google PubSub Emulator, use `akka.javasdk.dev-mode.eventing.support=google-pubsub-emulator`.

**📌 NOTE**\
For Kafka, the local Kafka broker is expected to be available on `localhost:9092`. For Google PubSub, the emulator is expected to be available on `localhost:8085`.

## Running multiple services locally

A typical application is composed of one or more services deployed to the same Akka project. When deployed under the same project, two different services can make [calls to each other](component-and-service-calls.adoc) or [subscribe to each other’s event streams](consuming-producing.adoc) by simply using their logical names.

The same can be done on your local machine by configuring the services to run on different ports. The services
will discover each other by name and will be able to interact.

The default port is 9000, and only one of the services can run on the default port. The other service must be configured with another port.

This port is configured in `akka.javasdk.dev-mode.http-port` property in the
`src/main/resources/application.conf` file.

```xml
```

With both services configured, we can start them independently by running `mvn compile exec:java` in two separate terminals.
