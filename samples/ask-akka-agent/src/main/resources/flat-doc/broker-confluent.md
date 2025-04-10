# Using Confluent Cloud as Kafka service

Akka connects to [Confluent Cloud, window="new"](https://confluent.cloud) Kafka services via TLS, authenticating using SASL (Simple Authentication and Security Layer) PLAIN.

## Steps to connect to a Confluent Cloud Kafka broker

Take the following steps to configure access to your Confluent Cloud Kafka broker for your Akka project.

1. Log in to [Confluent Cloud, window="new"](https://confluent.cloud/) and select the cluster Akka should connect to. Create a new cluster if you don’t have one already.
2. Create an API key for authentication
   1. Select "API Keys"

      ![New API key](operations:confluent-api-key-new.png)
   2. Choose the API key scope for development use, or proper setup with ACLs. The API key’s "Key" is the username, the "Secret" acts as password.
   3. When the API key was created, your browser downloads an `api-key-... .txt` file with the API key details.

      ![API key details](operations:confluent-api-key-details.png)
3. Ensure you are on the correct Akka project

   ```command window
   akka config get-project
   ```
4. Copy the API secret and store it in an Akka secret (e.g. called `confluent-api-secret`)

   ```command window
   akka secret create generic confluent-api-secret --literal secret=<the API key secret>
   ```
5. Select "Cluster Settings" and copy the bootstrap server address shown in the "Endpoints" box.
6. Use `akka projects config` to set the broker details. Set the username using the provided API key’s "Key" and service URI according to the connection information.

   ```command window
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
```command window
akka projects config get broker
```

## Create a topic

To create a topic, you can either use the Confluent Cloud user interface, or the [Confluent CLI, window="new"](https://docs.confluent.io/confluent-cli/current/overview.html).

* **Browser**

  1. Open [Confluent Cloud, window="new"](https://confluent.cloud/).
  2. Go to your cluster
  3. Go to the Topics page
  4. Use the Add Topic button
  5. Fill in the topic name, select the number of partitions, and use the Create with defaults button

  You can now use the topic to connect with Akka.
* **Confluent Cloud CLI**

  ```command line
  confluent kafka topic create \
    <topic name> \
    --partitions 3 \
    --replication 2
  ```

  You can now use the topic to connect with Akka.

## See also

* [`akka projects config` commands](reference:cli/akka-cli/akka_projects_config.adoc#_see_also)
