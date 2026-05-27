<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Setup and configuration](setup-and-configuration/index.html)
- [Running locally with a message broker](running-with-broker.html)

<!-- </nav> -->

# Running locally with a message broker

When a service uses [topic consumers](consuming-producing.html#consume_topic) or [topic producers](consuming-producing.html#topic_producing), one option during local development is to run the service against a real message broker. This page describes how to set that up for the broker technologies that Akka supports.

For most day-to-day development and automated tests, the testkit’s [mocked topic](consuming-producing.html#testing) is simpler — no broker process needed. Running against a real broker (or its emulator) is useful when you want end-to-end behavior with another system, want to inspect messages with the broker’s own tooling, or are validating the broker integration itself.

## <a href="about:blank#_kafka"></a> Kafka

Akka can connect to any Kafka-compatible broker, including a single-node broker running locally in Docker.

### <a href="about:blank#_starting_a_local_kafka_broker"></a> Starting a local Kafka broker

The following `docker-compose.yml` starts Kafka on `localhost:9092` without authentication:

```yaml
services:
  kafka:
    image: confluentinc/cp-kafka:7.2.6
    depends_on:
      - zookeeper
    ports:
      - 9092:9092
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  zookeeper:
    image: zookeeper:3.9
    ports:
      - "2181:2181"
```
Start it with:

```command
docker compose up -d kafka
```

### <a href="about:blank#_configuring_the_service_for_kafka"></a> Configuring the service for Kafka

Broker support in dev mode is disabled by default. Enable Kafka by setting `akka.javasdk.dev-mode.eventing.support=kafka`, either as a system property when starting the service or in `src/main/resources/application.conf`.

The runtime will look for a broker on `localhost:9092` and connect without authentication, which matches the broker started above.

```command
mvn compile exec:java -Dakka.javasdk.dev-mode.eventing.support=kafka
```
Or, equivalently, in `src/main/resources/application.conf`:

```hocon
akka.javasdk.dev-mode.eventing.support = "kafka"
```

### <a href="about:blank#_connecting_to_a_different_kafka_broker"></a> Connecting to a different Kafka broker

To point the service at a Kafka broker running somewhere other than `localhost:9092`, override the bootstrap servers. A comma-separated list of `host:port` entries is supported.

```hocon
akka.javasdk.dev-mode.eventing.support = "kafka"
akka.javasdk.dev-mode.eventing.kafka.bootstrap-servers = "kafka1.example.com:9092,kafka2.example.com:9092"
```
The same values can also be set as system properties when starting the service.

### <a href="about:blank#_kafka_authentication"></a> Kafka authentication

For a local broker, leaving the defaults (`auth-mechanism = "NONE"`, no username/password, no CA certificate) is the right choice — the connection is plaintext and unauthenticated.

When connecting to a Kafka broker that requires authentication, the same SASL mechanisms supported in production are available in dev mode: `PLAIN`, `SCRAM-SHA-256` and `SCRAM-SHA-512`. Any mechanism other than `NONE` requires TLS, and the broker certificate must be trusted by the JVM. If the broker uses a private CA, point `broker-ca-pem-file` at a PEM-encoded CA certificate readable from the local filesystem.

```hocon
akka.javasdk.dev-mode.eventing.support = "kafka"
akka.javasdk.dev-mode.eventing.kafka {
  bootstrap-servers = "kafka.example.com:9092"
  auth-mechanism = "SCRAM-SHA-512"
  auth-username = "my-user"
  auth-password = "my-password"
  # Optional, only needed when the broker's CA is not in the default JVM trust store
  broker-ca-pem-file = "/path/to/ca.pem"
}
```

|  | Storing passwords in `application.conf` is fine for local experimentation, but avoid committing secrets to source control. For local development against a shared broker, prefer reading the password from an environment variable, for example `auth-password = ${?KAFKA_PASSWORD}`. |
Other Kafka authentication mechanisms (mTLS, Kerberos/GSSAPI, OAUTHBEARER) are not supported.

### <a href="about:blank#_topic_creation"></a> Topic creation

Akka does not create Kafka topics. If a topic referenced by a consumer or producer does not exist when the service starts, the runtime logs an error but does not block startup. The topic may still be created on first use if the broker has `auto.create.topics.enable` turned on (the default in many local Kafka distributions), though that leaves the partition count and replication factor up to the broker. For predictable behavior — including a partition count that matches how you key messages — create the topics ahead of time, using the Kafka CLI tools bundled with the broker image.

## <a href="about:blank#_pubsub"></a> Google Cloud Pub/Sub emulator

For Google Cloud Pub/Sub, Google ships an emulator as part of the Cloud SDK. Akka has a dedicated dev-mode option that targets the emulator and bypasses credential checks.

### <a href="about:blank#_starting_the_pubsub_emulator"></a> Starting the Pub/Sub emulator

The following `docker-compose.yml` starts the emulator on `localhost:8085`:

```yaml
services:
  gcloud-pubsub-emulator:
    image: gcr.io/google.com/cloudsdktool/cloud-sdk:432.0.0-emulators
    command: gcloud beta emulators pubsub start --project=test --host-port=0.0.0.0:8085
    ports:
      - 8085:8085
```
Start it with:

```command
docker compose up -d gcloud-pubsub-emulator
```

### <a href="about:blank#_configuring_the_service_for_pubsub"></a> Configuring the service for Pub/Sub

Enable the emulator with `akka.javasdk.dev-mode.eventing.support=google-pubsub-emulator`, either as a system property or in `application.conf`.

```command
mvn compile exec:java -Dakka.javasdk.dev-mode.eventing.support=google-pubsub-emulator
```
Defaults match the docker-compose snippet above: host `localhost`, port `8085`, project id `test`, no TLS, no credentials. The `PUBSUB_EMULATOR_HOST` and `PUBSUB_EMULATOR_PORT` environment variables — the same ones the Google client libraries honor — can be set to point at an emulator running elsewhere.

Topic and subscription creation is controlled by `akka.javasdk.eventing.google-pubsub.mode`:

- `automatic-subscription` (default) — the runtime creates subscriptions if they do not exist; against the emulator, topics typically get created as well, so no out-of-band setup is needed for local runs.
- `automatic` — the runtime creates both topics and subscriptions explicitly.
- `manual` — neither is created by the runtime; create them with `gcloud pubsub` or the emulator’s REST API.
The default mode is enough for most local runs against the emulator. In production against real Pub/Sub, topics must exist before the service starts under `automatic-subscription`.

## <a href="about:blank#_event_hubs"></a> Azure Event Hubs

Azure Event Hubs does not have a built-in dev-mode emulator path. For local development and testing, prefer the testkit’s [mocked topic](consuming-producing.html#testing), which works identically regardless of which broker the deployed service uses.

If you need to validate the Event Hubs integration itself, you can connect a local service to a real Event Hubs namespace by setting `akka.javasdk.dev-mode.eventing.support=eventhubs` and providing the `AZURE_EVENT_HUBS_CONNECTION_STRING` environment variable (plus the Azure Blob Storage settings used for partition checkpointing). See [Using Azure Event Hubs](../operations/projects/broker-azure-eventhubs.html) for the configuration keys involved.

## <a href="about:blank#_using_the_local_console"></a> Local console

The [local console](running-locally.html#local_console) continues to work as usual. Messages received from or produced to the broker show up in the service logs and traces.

## <a href="about:blank#_see_also"></a> See also

- [Run a service locally](running-locally.html)
- [Consuming and producing](consuming-producing.html)
- [Configure message brokers](../operations/projects/message-brokers.html)

<!-- <footer> -->
<!-- <nav> -->
[Run a service locally](running-locally.html) [AI model provider configuration](model-provider-details.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->