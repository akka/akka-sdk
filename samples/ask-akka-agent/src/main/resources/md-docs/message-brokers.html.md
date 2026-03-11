<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Integrations](integrations/index.html)
- [Message broker integrations](message-brokers.html)

<!-- </nav> -->

# Message broker integrations

Akka offers built-in message broker integrations for use with the Akka Consumer and Producer component. These built-in integrations are available for Google Cloud Pub/Sub, Azure Event Hubs and hosted Kafka services. For other broker technologies, Java client libraries can be used directly to implement publishing of messages.

## <a href="about:blank#_using_built_in_integrations"></a> Using built-in integrations

For the built-in technologies, Akka decouples the broker configuration from the implementation of the consumer or producer. The topic name is referenced independently of the broker technology, as demonstrated in [Consume from a message broker Topic](consuming-producing.html#consume_topic) and [Producing to a message broker Topic](consuming-producing.html#topic_producing).

All connection details are managed at the Akka project level. For configuration instructions, refer to [Configure message brokers](../operations/projects/message-brokers.html).

The Akka SDK testkit has built-in support for simulating message brokers. See [Testing the Integration](consuming-producing.html#testing) for more details. For running locally with a broker, refer to [running a service with broker support](running-locally.html#_local_broker_support).

## <a href="about:blank#_producing_to_other_broker_technologies"></a> Producing to other broker technologies

Other message broker technologies can be integrated into an Akka service by utilizing their respective client libraries. Additionally, the [Akka libraries Alpakka project](https://doc.akka.io/libraries/alpakka/current) provides Akka-native solutions for integrating various services.

We continuously evaluate additional integrations for potential built-in support in Akka. If you have specific requirements, please contact us at [support@akka.io](mailto:support@akka.io).

## <a href="about:blank#_see_also"></a> See also

- [Configure message brokers](../operations/projects/message-brokers.html)
- <a href="../reference/cli/akka-cli/akka_projects_config.html#_see_also">`akka projects config` commands</a>
- [Akka integrations through Alpakka](https://doc.akka.io/libraries/alpakka/current)

<!-- <footer> -->
<!-- <nav> -->
[Component and service calls](component-and-service-calls.html) [Streaming](streaming.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->