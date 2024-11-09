# Key Value Entity Counter

## Designing

To understand the Akka concepts behind this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

## Developing

This project demonstrates the use of Key Value Entity and View components.
To understand more about these components, see [Developing services](https://doc.akka.io/java/index.html).

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

With your Akka service running, any defined endpoints should be available at `http://localhost:9000`

```shell
curl -XPOST -H "Content-Type: application/json" localhost:9000/counter/foo/increase -d '10'
```

```shell
curl localhost:9000/counter/foo
```

```shell
curl -XPOST -H "Content-Type: application/json" localhost:9000/counter/foo/plus-one
```

## Deploying

You can use the [Akka Console](https://console.akka.io) to create a project and see the status of your service.

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/reference/cli/index.html).

Deploy the service using the image tag from above `mvn install`:

```shell
akka service deploy key-value-counter key-value-counter:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.
