# Tracing with OpenTelemetry in Java

## Designing

To understand the Akka SDK concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

## Developing

This project demonstrates how to use tracing in an Akka Service using Akka SDK.
By calling from an Akka endpoint to an external service, and manually creating a trace. 


## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally

When running an Akka service locally.
To start your service locally, run:

```shell
TRACING_ENABLED=true COLLECTOR_ENDPOINT="http://localhost:4317" mvn compile exec:java
```

This command will start your Akka service, with tracing enabled and exporting the generated 
traces to the Jaeger container referred below.

To start Jaeger locally, run:

```shell
docker compose up
```

## Exercising the service

With your Akka service running, any defined endpoints should be available at `http://localhost:9000`.

- Add a new user

```shell
 curl -i -XPOST -H "Content-Type: application/json" localhost:9000/tracing -d '{"id":"2454cb46-1b16-408a-b7f8-bd2d5c376969"}'
```


- Now you can see the trace in Jaeger UI at http://localhost:16686
  - select "runtime" and "Find all traces" to explore the trace

## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/akka-cli/index.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy tracing tracing:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.
