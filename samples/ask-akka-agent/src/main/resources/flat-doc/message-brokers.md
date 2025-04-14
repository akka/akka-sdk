# Configure message brokers

Akka eventing integrates with [Google Cloud Pub/Sub, window="new"](https://cloud.google.com/pubsub/docs/overview) and managed Kafka services such as [Confluent Cloud, window="new"](https://www.confluent.io/confluent-cloud) and [Aiven for Apache Kafka, window="new"](https://aiven.io/kafka) to enable asynchronous messaging.

Message brokers are configured on Akka Project level. A project can have one broker configuration, Akka eventing is independent of the broker technology.

## Broker services

* [Aiven for Apache Kafka](operations:projects/broker-aiven.adoc)
* [AWS MSK Kafka](operations:projects/broker-aws-msk.adoc)
* [Confluent Cloud](operations:projects/broker-confluent.adoc)
* [Google Pub/Sub](operations:projects/broker-google-pubsub.adoc)

## Testing Akka eventing

The Java SDK testkit has built-in support to simulate message brokers. See [Testing the Integration](java:consuming-producing.adoc#testing).

For running locally with a broker, see [running a service with broker support](java:running-locally.adoc#_local_broker_support).

## See also

* [`akka projects config` commands](reference:cli/akka-cli/akka_projects_config.adoc#_see_also)
