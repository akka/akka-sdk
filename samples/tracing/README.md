# Tracing with OpenTelemetry in Java

## Designing

To understand the Akka SDK concepts that are the basis for this example, see [Designing services](https://docs.kalix.io/java/development-process.html) in the documentation.

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

To deploy your service, install the `akka` CLI as documented in
[Install Kalix](https://docs.kalix.io/kalix/install-kalix.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://doc.akka.io/operations/container-registries.html)
for more information on how to make your docker image available to Akka.

Finally, you can use the [Akka Console](https://console.kalix.io)
to create a project and then deploy your service into the project either by using `mvn deploy akka:deploy` which
will conveniently package, publish your docker image, and deploy your service to Akka SDK, or by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `akka` CLI.
