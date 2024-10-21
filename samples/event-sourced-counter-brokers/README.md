# Event Sourced Counter with Broker integration

## Designing

To understand the Akka concepts that are the basis for this example, see [Development Process](https://doc.akka.io/concepts/development-process.html) in the documentation.

## Developing

This project demonstrates the use of an Event Sourced Entity and the different ways to consume and produce from/to a broker. This sample provides configuration to run with either Kafka or Google PubSub emulator.
Note that, currently, only one broker can be configured per service. To understand more about these consumers and producers, see [Consuming and Producing](https://doc.akka.io/java/consuming-producing.html).

## Building

Use Maven to build your project:

```shell
mvn compile
```

## Running Locally with Kafka

Start by running Kafka:
```shell
docker-compose up -d kafka
```

Then, to start your service locally using Kafka support, run:

```shell
mvn compile exec:java -Dakka.javasdk.dev-mode.eventing.support=kafka
```

## Running Locally with Google PubSub Emulator

Start by running the Google PubSub Emulator:
```shell
docker-compose up -d gcloud-pubsub-emulator
```

Then, to start your service locally with Google PubSub Emulator support, run:

```shell
mvn compile exec:java -Dakka.javasdk.dev-mode.eventing.support=google-pubsub-emulator
```


## Exercising the services

With your Akka service running, once you have defined endpoints they should be available at `http://localhost:9000`.

### Examples

- increase (or create) a counter named `hello` with value `10`

```shell
curl -XPOST localhost:9000/counter/hello/increase/10
```

- retrieve the value of a counter named `hello`

```shell
curl -XGET localhost:9000/counter/hello
```

- multiply existing counter named `hello` by value `5`

```shell
curl -XPOST localhost:9000/counter/hello/multiply/5
```

### Deploy

To deploy your service, install the `akka` CLI as documented in
[Install Akka CLI](https://doc.akka.io/akka-cli/index.html)
and configure a Docker Registry to upload your docker image to.

You will need to update the `dockerImage` property in the `pom.xml` and refer to
[Configuring registries](https://doc.akka.io/operations/container-registries.html)
for more information on how to make your docker image available to Akka.

Finally, you can use the [Akka Console](https://console.akka.io)
to create a project and then deploy your service into the project by first packaging and
publishing the docker image through `mvn deploy` and then deploying the image
through the `akka` CLI.

Since this project depends on a broker, you will need also to configure that broker access through the Akka Console or Akka CLI. See [Configure message brokers](https://doc.akka.io/operations/projects/message-brokers.html) for guidance.

## Integration Tests

This sample showcases how to have integration tests with and without a real broker. Since the test suite includes one test with each broker, you need to have both running.

First run:
```shell
docker-compose up
```

Then run:
```shell
mvn verify -Pit
```
