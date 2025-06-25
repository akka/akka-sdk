<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Configure message brokers](message-brokers.html)

<!-- </nav> -->

# Configure message brokers

Akka eventing integrates with *Google Cloud Pub/Sub* and managed Kafka services such as *Confluent Cloud*, *Amazon Managed Streaming for Apache Kafka (Amazon MSK)*, and *Aiven for Apache Kafka* to enable asynchronous messaging and integrations with other systems.

Message brokers are configured at the Akka project level. A project can have one broker configuration. Akka eventing is independent of the broker technology.

## <a href="about:blank#_broker_services"></a> Broker services

Follow the detailed steps to configure the desired message broker service for use with your Akka project:

- [Google Pub/Sub](broker-google-pubsub.html)
- [Confluent Cloud](broker-confluent.html)
- [Amazon MSK](broker-aws-msk.html)
- [Aiven for Apache Kafka](broker-aiven.html)
We continuously evaluate additional integrations for potential built-in support in Akka. If you have specific requirements, please contact us at [support@akka.io](mailto:support@akka.io).

For running Akka services that integrate with a message broker locally, see [running a service with broker support](../../java/running-locally.html#_local_broker_support).

## <a href="about:blank#_see_also"></a> See also

- <a href="../../reference/cli/akka-cli/akka_projects_config.html#_see_also">`akka projects config` commands</a>
- [Google Cloud Pub/Sub](https://cloud.google.com/pubsub/docs/overview)
- [Confluent Cloud](https://www.confluent.io/confluent-cloud)
- [Amazon MSK](https://aws.amazon.com/msk/)
- [Aiven for Apache Kafka](https://aiven.io/kafka)

<!-- <footer> -->
<!-- <nav> -->
[Configure an external container registry](external-container-registries.html) [Google Pub/Sub](broker-google-pubsub.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->