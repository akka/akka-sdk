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
docker compose up -d kafka
```

Then, to start your service locally using Kafka support, run:

```shell
mvn compile exec:java -Dakka.javasdk.dev-mode.eventing.support=kafka
```

## Running Locally with Google PubSub Emulator

Start by running the Google PubSub Emulator:
```shell
docker compose up -d gcloud-pubsub-emulator
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
curl localhost:9000/counter/hello
```

- multiply existing counter named `hello` by value `5`

```shell
curl -XPOST localhost:9000/counter/hello/multiply/5
```

- list all counters from a view

```shell
curl localhost:9000/counter/all
```

- list all counters larger than five from a view

```shell
curl localhost:9000/counter/greater-than/5
```
- list all counters larger than five from a view consuming a topic that the `CounterJournalToTopicWithMetaConsumer`
  consumer writes events to

```shell
curl localhost:9000/counter/greater-than-via-topic/5
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
akka service deploy event-sourced-counter-brokers event-sourced-counter-brokers:tag-name --push
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html)
for more information.

Since this project depends on a broker, you will need also to configure that broker access through the Akka Console or Akka CLI. See [Configure message brokers](https://doc.akka.io/operations/projects/message-brokers.html) for guidance.

## Integration Tests

This sample showcases how to have integration tests with and without a real broker. Since the test suite includes one test with each broker, you need to have both running.

First run:
```shell
docker compose up
```

Then run:
```shell
mvn verify
```
