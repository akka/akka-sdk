# Tracing with OpenTelemetry in Java

## Designing

To understand the Akka SDK concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

## Developing

This project demonstrates how to use tracing in an Akka Service using Akka SDK.
By calling from an Akka endpoint to an external service, and manually creating a trace. 


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

First start a local Jaeger in docker using the prepared docker compose file from the sample project directory: 

```shell
docker compose up
```

Then start your service locally, with tracing enabled and reporting to the local Jaeger instance:

```shell
TRACING_ENABLED=true COLLECTOR_ENDPOINT="http://localhost:4317" mvn compile exec:java
```

## Exercising the service

With your Akka service running, any defined endpoints should be available at `http://localhost:9000`.

Report a custom span around an async task inside an endpoint:

```shell
curl -i -XPOST localhost:9000/tracing/custom/5
```

Schedule a timed action which reports a custom span when executing an async call to an external service:

```shell
curl -i -XPOST -H "Content-Type: application/json" localhost:9000/tracing -d '{"id":"2454cb46-1b16-408a-b7f8-bd2d5c376969"}'
```

Now you can see the trace in Jaeger UI at http://localhost:16686 

Select "runtime" and "Find all traces" to explore the traces, you should see "POST /tracing/custom/{id}" and "POST /tracing/"
for the respective two calls above.

## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/operations/cli/installation.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy tracing tracing:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.
