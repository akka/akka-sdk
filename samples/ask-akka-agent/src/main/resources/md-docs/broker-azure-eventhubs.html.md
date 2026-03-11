<!-- <nav> -->
- [Akka](../../index.html)
- [Operating](../index.html)
- [Akka Automated Operations](../akka-platform.html)
- [Projects](index.html)
- [Configure message brokers](message-brokers.html)
- [Azure Event Hubs](broker-azure-eventhubs.html)

<!-- </nav> -->

# Using Azure Event Hubs as message broker

Akka can connect to [Azure Event Hubs](https://learn.microsoft.com/en-us/azure/event-hubs/) for consuming and emitting events.

## <a href="about:blank#_setting_up_the_event_hub"></a> Setting up the Event Hub

Follow the Azure documentation to create a resource group, a namespace and a hub: [Azure Event Hubs Quickstart](https://learn.microsoft.com/en-us/azure/event-hubs/event-hubs-create)

Make sure to define a number of partitions of at least the highest number of service instances you expect your Akka
service to scale out to. For example the default upper limit with Akka is 10 instances.

### <a href="about:blank#_shared_access_policy_for_the_hub"></a> Shared Access Policy for the hub

Create a shared access policy (SAS) for the "namespace" or the individual hub, if the individual hub is used that is the only hub that can be accessed from the services. With the namespace all hubs created in it can be accessed.

Note the connection string `primary key`, the string should start with `Endpoint`:

![Azure Event Hub SAS primary key](../_images/azure-eventhubs-sas.png)

### <a href="about:blank#_set_up_the_check_point_store"></a> Set up the check point store

Azure Blob Storage is used for checkpoint storage of consumers so needs to be set up as well as the event hub itself.

Create a storage account, a blob container and set up a "connection string" as described in [the Azure Event Hub Docs about checkpoint store](https://learn.microsoft.com/en-us/azure/event-hubs/event-hubs-java-get-started-send?tabs=passwordless%252Croles-azure-portal#create-an-azure-storage-and-a-blob-container)

Note the account name and the container name you created.

Under "Shared access tokens" for the created container, generate and note the Blob SAS Token:

![Azure Event Hub Checkpoint Store SAS token](../_images/azure-checkpoint-store-sas-token.png)

## <a href="about:blank#_set_up_akka_access"></a> Set up Akka access

Store the event hub connection string and the checkpoint store SAS token as Akka secrets:

akka secret create generic azure-eventhub-secret \
--literal connection-string="<hub connection string>" \
--literal store-sas-token="<store SAS token>" Configure the broker:

akka projects config set broker \
--broker-service eventhubs \
--event-hubs-connection-string-secret azure-eventhub-secret/connection-string \
--event-hubs-checkpoint-store-sas-token-secret azure-eventhub-secret/store-sas-token \
--event-hubs-checkpoint-store-endpoint "https://<your-storage-account-name>.blob.core.windows.net" \
--event-hubs-checkpoint-store-container-name <your-container-name>
## <a href="about:blank#_special_caveats_when_using_event_hubs_as_broker"></a> Special caveats when using event hubs as broker

### <a href="about:blank#_topic"></a> Topic

In Akka terminology, the event hub name is the topic name. A component subscribing or publishing to a topic is equivalent with it subscribing or publishing to an event hub.

### <a href="about:blank#_retention"></a> Retention

Events always have retention, if they for some reason are not consumed within the retention time they are lost. This could be for example if pausing a consuming service.

### <a href="about:blank#_consumer_groups"></a> Consumer groups

Consumer groups in Azure Event Hubs must be created up front before Akka can use them except for the default consumer group (`$Default` which is automatically created when an event hub has is created)

For actions consuming an event hub the default consumer group is used.

This means that if more than one action in the same Akka service are consuming events from the same event hub, different consumer groups must be specified on each of the actions, or each will only see a subset of the events.

For views, the component name of the view is used as consumer group, and must be created up front in Azure.

## <a href="about:blank#_delivery_characteristics"></a> Delivery characteristics

When your application consumes messages from Azure Event Hubs, it will try to deliver messages to your service in 'at-least-once' fashion while preserving order.

The ordering guarantee, that consumers see events in the order they were produced is per partition.

Cloud event subject metadata entry, if present, is used as partition key for events produced by an Akka service.
If no subject metadata is available the events are spread over the partitions in a round-robin fashion.

|  | Correct ordering is especially important for topics that stream directly into views using the `transform_update` option: when messages for the same subject id are spread over different partitions (or do not have partition keys), they may read stale data and lose updates. |

<!-- <footer> -->
<!-- <nav> -->
[Aiven for Kafka](broker-aiven.html) [Manage secrets](secrets.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->