<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Configure message brokers](message-brokers.html)
- [Aiven for Kafka](broker-aiven.html)

<!-- </nav> -->

# Using Aiven for Apache Kafka

Akka connects to [Aiven](https://aiven.io/) 's Kafka service via TLS, using a CA certificate provided by Aiven for the service, authenticating using SASL (Simple Authentication and Security Layer) SCRAM.

|  | In this guide we use the default `avnadmin` account, but you may want to create a specific service user to use for your Akka service connection. |

## <a href="about:blank#_steps_to_connect_to_an_aiven_kafka_service"></a> Steps to connect to an Aiven Kafka service

1. Log in to the [Aiven web console](https://console.aiven.io/) and select the Aiven Kafka service Akka should connect to.
2. Enable SASL for your Aiven Kafka (See Aiven’s [Use SASL Authentication with Apache Kafka](https://docs.aiven.io/docs/products/kafka/howto/kafka-sasl-auth))

  1. Scroll down the Service overview page to the **Advanced configuration** section.
  2. Turn on the setting labelled `kafka_authentication_methods.sasl`, and click **Save advanced configuration**.

![Aiven Kafka advanced configuration](../_images/aiven-advanced-configuration.png)
  3. The connection information at the top of the Service overview page will now offer the ability to connect via SASL or via client certificate. Select SASL in "Authentication Method" to show the right connection details:

![Aiven Kafka connection information](../_images/aiven-connection-information.png)
  4. Download the CA Certificate via the link in the connection information.
3. Ensure you are on the correct Akka project

```command
akka config get-project
```
4. Create an Akka TLS CA secret with the CA certificate for the service (e.g. called `kafka-ca-cert`)

```command
akka secret create tls-ca kafka-ca-cert --cert ./ca.pem
```
5. Copy the CA password from the "Connection Information" and store it in an Akka secret (e.g. called `kafka-secret`)

```command
akka secret create generic kafka-secret --literal pwd=<the password>
```
6. Use `akka projects config` to set the broker details. Set the Aiven username and service URI according to the Aiven connection information page.

```command
akka projects config set broker \
  --broker-service kafka \
  --broker-auth scram-sha-256  \
  --broker-user avnadmin \
  --broker-password-secret kafka-secret/pwd \
  --broker-bootstrap-servers <kafka...aivencloud.com:12976> \
  --broker-ca-cert-secret kafka-ca-cert
```
The `broker-password-secret` and `broker-ca-cert-secret` refer to the names of the Akka secrets created earlier rather than the actual secret values.

An optional description can be added with the parameter `--description` to provide additional notes about the broker.
7. Contact [support@akka.io](mailto:support@akka.io) to open a port in Akka to reach your Aiven port configured above.
The broker config can be inspected using:

```command
akka projects config get broker
```

## <a href="about:blank#_create_a_topic"></a> Create a topic

To create a topic, you can either use the Aiven console, or the Aiven CLI.

Browser Instructions from Aiven’s [Creating an Apache Kafka topic](https://docs.aiven.io/docs/products/kafka/howto/create-topic)

1. Open the [Aiven Console](https://console.aiven.io/).
2. In the Services page, click on the Aiven for Apache Kafka® service where you want to crate the topic.
3. Select the Topics tab:

  1. In the Add new topic section, enter a name for your topic.
  2. In the Advanced configuration you can set the replication factor, number of partitions and other advanced settings. These can be modified later.
4. Click Add Topic on the right hand side of the console.
You can now use the topic to connect with Akka.

Aiven CLI See Aiven’s [Manage Aiven for Apache Kafka topics](https://docs.aiven.io/docs/tools/cli/service/topic#avn-cli-service-topic-create)

```command
avn service topic-create \
  <service name> \
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
[AWS MSK Kafka](broker-aws-msk.html) [Manage secrets](secrets.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->