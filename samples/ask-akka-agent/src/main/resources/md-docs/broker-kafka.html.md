<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Configure message brokers](message-brokers.html)
- [Self-hosted Kafka](broker-kafka.html)

<!-- </nav> -->

# Using a self-hosted or arbitrary Kafka cluster

In addition to the managed Kafka services with dedicated guides ([Confluent Cloud](broker-confluent.html), [Amazon MSK](broker-aws-msk.html), [Aiven for Apache Kafka](broker-aiven.html)), Akka can connect to any Kafka cluster that is reachable from the Akka platform and offers a supported authentication mechanism — including self-hosted Kafka, Strimzi/Kafka on Kubernetes, Redpanda, or another managed provider.

This page describes the general configuration steps and lists the connectivity and authentication requirements your cluster must meet.

## <a href="about:blank#_requirements"></a> Requirements

The Kafka cluster must satisfy the following:

- **Network reachability** — The cluster’s bootstrap servers must be reachable from Akka. For clusters running in a private network, this typically means exposing public endpoints with a firewall allow-list, or arranging a peering/VPN solution.
- **Broker port** — Akka restricts outbound traffic to a fixed allow-list of ports. The standard Kafka port `9092` is open by default, as are the ports used by the managed providers covered by the dedicated guides (for example `9093` for Azure Event Hubs, `9096` and `9196` for AWS MSK, `12976` for Aiven). If your cluster listens on a port that is not on this list, contact [support@akka.io](mailto:support@akka.io) to have it added before configuring the broker.
- **TLS** — All authenticated connections from Akka use TLS. The broker must present a certificate trusted by either a public CA or a CA certificate that you provide as an Akka secret.
- **SASL authentication** — Akka authenticates using one of the supported SASL mechanisms (see [Supported authentication](about:blank#_supported_authentication)). Anonymous/plaintext access is not supported for production brokers.

## <a href="about:blank#_supported_authentication"></a> Supported authentication

Akka supports the following Kafka authentication mechanisms when configured via `akka projects config set broker`:

| Mechanism | Notes |
| --- | --- |
| `plain` | SASL/PLAIN over TLS. Username and password are sent in plaintext inside the TLS-protected channel. |
| `scram-sha-256` | SASL/SCRAM with SHA-256. Username and password are negotiated through a challenge–response handshake. |
| `scram-sha-512` | SASL/SCRAM with SHA-512. Same as above with a stronger hash. |
The following mechanisms are **not** supported:

- mTLS / SSL client-certificate authentication
- Kerberos (GSSAPI)
- OAUTHBEARER (token-based SASL)
- Plaintext connections without TLS (except in [local development](../../sdk/running-with-broker.html#_kafka))
If your cluster only supports an unsupported mechanism, enable SASL/SCRAM or SASL/PLAIN for the Akka service user before continuing.

## <a href="about:blank#_steps"></a> Steps to connect

1. Prepare the broker

On your Kafka cluster, create a user dedicated to your Akka service and grant it the ACLs required for the topics your service consumes from and produces to. Make sure the broker listener used by Akka is configured for `SASL_SSL` with the chosen mechanism.
2. Ensure you are on the correct Akka project

```command
akka config get-project
```
3. (Optional) Create an Akka TLS CA secret

If the broker’s TLS certificate is **not** signed by a public CA already trusted by the Akka platform, store the CA certificate as a secret.

```command
akka secret create tls-ca kafka-ca-cert --cert ./ca.pem
```
Skip this step if the broker certificate is signed by a public CA.
4. Store the user’s password in an Akka secret

```command
akka secret create generic kafka-secret --literal pwd=<the password>
```
5. Configure the broker for the project

```command
akka projects config set broker \
  --broker-service kafka \
  --broker-auth scram-sha-512 \
  --broker-user <username> \
  --broker-password-secret kafka-secret/pwd \
  --broker-bootstrap-servers <host1:port1,host2:port2> \
  --broker-ca-cert-secret kafka-ca-cert
```
Use the auth mechanism matching your broker setup: `plain`, `scram-sha-256` or `scram-sha-512`. Omit `--broker-ca-cert-secret` if the broker uses a publicly trusted CA. Multiple bootstrap servers can be provided as a comma-separated list.

The `--broker-password-secret` and `--broker-ca-cert-secret` flags refer to the **names** of the Akka secrets created earlier rather than the actual secret values.

An optional description can be added with the parameter `--description` to provide additional notes about the broker.
6. Open the network path

If the broker port is not in the Akka outbound allow-list (see [Requirements](about:blank#_requirements)), contact [support@akka.io](mailto:support@akka.io) to have it added. Clusters that are not reachable on a public, generally-routable endpoint also need a peering or VPN solution arranged with support.
The broker config can be inspected using:

```command
akka projects config get broker
```

## <a href="about:blank#_create_topics"></a> Create topics

Create the topics used by your service ahead of time, using the Kafka tooling that ships with your cluster (for example `kafka-topics.sh`, the Strimzi `KafkaTopic` custom resource, or the management UI of your provider). Akka does not create topics automatically.

When partitioning, follow the guidance in [Delivery characteristics](about:blank#_delivery_characteristics) below: use the cloud event `ce-subject` (typically the entity id) as the partition key, so messages for the same subject are processed in order.

## <a href="about:blank#_delivery_characteristics"></a> Delivery characteristics

When your application consumes messages from Kafka, it will try to deliver messages to your service in 'at-least-once' fashion while preserving order.

Kafka partitions are consumed independently. When passing messages to a certain entity or using them to update a view row by specifying the id as the Cloud Event `ce-subject` attribute on the message, the same id must be used to partition the topic to guarantee that the messages are processed in order in the entity or view. Ordering is not guaranteed for messages arriving on different Kafka partitions.

|  | Correct partitioning is especially important for topics that stream directly into views and transform the updates: when messages for the same subject id are spread over different transactions, they may read stale data and lose updates. |
At-least-once delivery means a message may be delivered more than once. Within a single partition, redeliveries preserve the original order — there is no scenario where a newer message lands at the consumer before an older message’s redelivery completes. Two cases lead to redelivery:

- A message whose handler failed is replayed from the last committed offset, followed by the messages after it.
- A message that was processed successfully but whose offset commit did not make it before a restart or partition rebalance is replayed on the next read. Offset commits are batched, so this gap is normal rather than exceptional.
Consumer handlers must therefore be idempotent or deduplicate explicitly. See [Message deduplication](../../sdk/dev-best-practices.html#message-deduplication) for guidance.

When publishing messages to Kafka from Akka, the `ce-subject` attribute, if present, is used as the Kafka partition key for the message.

## <a href="about:blank#_testing_akka_eventing"></a> Testing Akka eventing

See [Testing Akka eventing](message-brokers.html#_testing)

## <a href="about:blank#_see_also"></a> See also

- [Configure message brokers](message-brokers.html)
- [Running locally with a message broker](../../sdk/running-with-broker.html)
- <a href="../../reference/cli/akka-cli/akka_projects_config.html#_see_also">`akka projects config` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Aiven for Kafka](broker-aiven.html) [Azure Event Hubs](broker-azure-eventhubs.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->