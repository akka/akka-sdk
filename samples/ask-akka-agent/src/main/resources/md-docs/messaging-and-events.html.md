<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Integrations](index.html)
- [Messaging & events](messaging-and-events.html)

<!-- </nav> -->

# Messaging & events

Akka has built-in messaging capabilities that should be your primary path. These are durable, integrated with the runtime, and require no external infrastructure.

## <a href="about:blank#_akka_native_messaging"></a> Akka-native messaging

Akka provides several built-in patterns for event-driven communication:

- **Service-to-service eventing** — Components within and across Akka services communicate through durable event streams. No broker required.
- **Brokerless pub/sub** — Event Sourced Entities emit events to a journal that other components subscribe to directly.
- **Event-driven Consumers** — Subscribe to Entity events, key-value state changes, or Workflow transitions and react to them in real time.
Akka handles end-to-end backpressure automatically and uses the event journal as a durable buffer to decouple producers and consumers. You implement the handler function; Akka manages the stream lifecycle.

Examples of built-in streaming:

- Views are updated asynchronously from a stream of events. You implement the update handler.
- Views can stream query results, with the receiver controlling the pace.
- Consumers process event streams from Entities, other services, or message broker topics.
- Consumers can produce events to other services or publish to a message broker topic.
For more advanced streaming needs, you can use Akka Streams directly. See the [Akka libraries documentation](https://doc.akka.io/libraries/akka-core/current/stream/stream-introduction.html) and [Alpakka](https://doc.akka.io/libraries/alpakka/current/) for streaming connectors.

- [Component and service calls](../component-and-service-calls.html)
- [Consumers](../consuming-producing.html)
- [Streaming](../streaming.html)

## <a href="about:blank#external"></a> External message brokers

When you need to integrate with systems outside Akka, use external message brokers. Akka offers built-in integrations for:

- **Kafka** — [Confluent Cloud](../../operations/projects/broker-confluent.html), [AWS MSK](../../operations/projects/broker-aws-msk.html), [Aiven for Apache Kafka](../../operations/projects/broker-aiven.html), or [a self-hosted or other Kafka cluster](../../operations/projects/broker-kafka.html)
- [Google Cloud Pub/Sub](../../operations/projects/broker-google-pubsub.html)
- [Azure Event Hubs](../../operations/projects/broker-azure-eventhubs.html)
For these built-in technologies, Akka decouples the broker configuration from the implementation of the Consumer or Producer. The topic name is referenced independently of the broker technology, as demonstrated in [Consume from a message broker Topic](../consuming-producing.html#consume_topic) and [Producing to a message broker Topic](../consuming-producing.html#topic_producing). All connection details are managed at the Akka project level. For configuration instructions, refer to [Configure message brokers](../../operations/projects/message-brokers.html).

### <a href="about:blank#_testing_with_message_brokers"></a> Testing with message brokers

The Akka SDK testkit has built-in support for simulating message brokers. See [Testing the integration](../consuming-producing.html#testing) for details. For running locally against a real broker (Kafka or the Google Pub/Sub emulator), see [Running locally with a message broker](../running-with-broker.html).

### <a href="about:blank#_other_broker_technologies"></a> Other broker technologies

Other message broker technologies can be integrated by using their Java client libraries directly. The [Alpakka project](https://doc.akka.io/libraries/alpakka/current) also provides Akka-native connectors for many external services.

## <a href="about:blank#_webhooks"></a> Webhooks

Implement webhooks for external system callbacks using [HTTP Endpoints](../http-endpoints.html). Define a route that accepts the webhook payload and routes it to the appropriate component.

## <a href="about:blank#_see_also"></a> See also

- [Consumers](../consuming-producing.html)
- [Streaming](../streaming.html)
- [Configure message brokers](../../operations/projects/message-brokers.html)
- <a href="../../reference/cli/akka-cli/akka_projects_config.html#_see_also">`akka projects config` commands</a>
- [Alpakka connectors](https://doc.akka.io/libraries/alpakka/current)

<!-- <footer> -->
<!-- <nav> -->
[Object storage](object-storage.html) [APIs & protocols](apis-and-protocols.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->