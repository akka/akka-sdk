<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Configure message brokers](message-brokers.html)
- [Confluent Cloud](broker-confluent.html)

<!-- </nav> -->

# Using Confluent Cloud as Kafka service

Akka connects to [Confluent Cloud](https://confluent.cloud/) Kafka services via TLS, authenticating using SASL (Simple Authentication and Security Layer) PLAIN.

## <a href="about:blank#_steps_to_connect_to_a_confluent_cloud_kafka_broker"></a> Steps to connect to a Confluent Cloud Kafka broker

Take the following steps to configure access to your Confluent Cloud Kafka broker for your Akka project.

1. Log in to [Confluent Cloud](https://confluent.cloud/) and select the cluster Akka should connect to. Create a new cluster if you don’t have one already.
2. Create an API key for authentication

  1. Select "API Keys"

![New API key](../_images/confluent-api-key-new.png)
  2. Choose the API key scope for development use, or proper setup with ACLs. The API key’s "Key" is the username, the "Secret" acts as password.
  3. When the API key was created, your browser downloads an `api-key-…​ .txt` file with the API key details.

![API key details](../_images/confluent-api-key-details.png)
3. Ensure you are on the correct Akka project

```command
akka config get-project
```
4. Copy the API secret and store it in an Akka secret (e.g. called `confluent-api-secret`)

```command
akka secret create generic confluent-api-secret --literal secret=<the API key secret>
```
5. Select "Cluster Settings" and copy the bootstrap server address shown in the "Endpoints" box.
6. Use `akka projects config` to set the broker details. Set the username using the provided API key’s "Key" and service URI according to the connection information.

```command
akka projects config set broker  \
  --broker-service kafka \
  --broker-auth plain \
  --broker-user <API_KEY> \
  --broker-password-secret confluent-api-secret/secret \
  --broker-bootstrap-servers <bootstrap server address> \
```
The `broker-password-secret` refer to the name of the Akka secret created earlier rather than the actual API key secret.

An optional description can be added with the parameter `--description` to provide additional notes about the broker.

The broker config can be inspected using:

```command
akka projects config get broker
```

## <a href="about:blank#_create_a_topic"></a> Create a topic

To create a topic, you can either use the Confluent Cloud user interface, or the [Confluent CLI](https://docs.confluent.io/confluent-cli/current/overview.html).

Browser
1. Open [Confluent Cloud](https://confluent.cloud/).
2. Go to your cluster
3. Go to the Topics page
4. Use the Add Topic button
5. Fill in the topic name, select the number of partitions, and use the Create with defaults button
You can now use the topic to connect with Akka.

Confluent Cloud CLI
```command
confluent kafka topic create \
  <topic name> \
  --partitions 3 \
  --replication 2
```
You can now use the topic to connect with Akka.

## <a href="about:blank#_delivery_characteristics"></a> Delivery characteristics

When your application consumes messages from Kafka, it will try to deliver messages to your service in 'at-least-once' fashion while preserving order.

Kafka partitions are consumed independently. When passing messages to a certain entity or using them to update a view row by specifying the id as the Cloud Event `ce-subject` attribute on the message, the same id must be used to partition the topic to guarantee that the messages are processed in order in the entity or view. Ordering is not guaranteed for messages arriving on different Kafka partitions.

|  | Correct partitioning is especially important for topics that stream directly into views and transform the updates: when messages for the same subject id are spread over different transactions, they may read stale data and lose updates. |
To achieve at-least-once delivery, messages that are not acknowledged will be redelivered. This means redeliveries of 'older' messages may arrive behind fresh deliveries of 'newer' messages. The *first* delivery of each message is always in-order, though.

When publishing messages to Kafka from Akka, the `ce-subject` attribute, if present, is used as the Kafka partition key for the message.

## <a href="about:blank#_testing_akka_eventing"></a> Testing Akka eventing

See [Testing Akka eventing](message-brokers.html#_testing)

## <a href="about:blank#_see_also"></a> See also

- <a href="../../reference/cli/akka-cli/akka_projects_config.html#_see_also">`akka projects config` commands</a>

<!-- <footer> -->
<!-- <nav> -->
[Google Pub/Sub](broker-google-pubsub.html) [AWS MSK Kafka](broker-aws-msk.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->