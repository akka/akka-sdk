<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Multi-region operations](multi-region.html)

<!-- </nav> -->

# Multi-region operations

Akka applications run in multiple regions with their data transparently and continuously replicated even across multiple cloud providers.
Akka applications do not require code modifications to run within multiple regions. Operators define controls to determine which regions
an application will operate within and whether that application’s data is pinned to one region or replicated across many.

Akka ensures regardless of which region receives a request, the request can be serviced. Multiple replication strategies can be configured, with each offering varying features for different use cases.

Multi-region operations are ideal for:

- Applications that require 99.9999% availability
- Geographic failover
- Geo-homing of data for low latency access
- Low latency global reads
- Low latency global writes
Akka has two replication modes: replicated reads and replicated writes.

## <a href="about:blank#_replicated_reads"></a> Replicated reads

Akka’s replicated reads offers full data replication across regions and even cloud providers, without any changes to the service implementation: an entity has its "home" in one *primary region*, while being replicated to multiple other regions.

Read requests are always handled locally within the region where they occur.

An entity can only be updated within a single region, known as its primary region.

Primary region selection for entities is configurable. There are two modes for primary selection: **pinned-region** and **request-region**.

Pinned-region primary selection mode (default) All entities use the same primary region, which is selected statically as part of the deployment. Write requests to the primary region of the entity are handled locally. Write requests to other regions are forwarded to the primary region. The primary region stays the same until there is an operational change of the primary region.

This is useful for scenarios where you want one primary region, with the ability to fail over to another region in the case of a regional outage.

Request-region primary selection mode The primary region changes when another region receives a write request. Upon a write request to an entity in a region that is not the primary it will move its primary. The new primary ensures that all preceding events from the previous primary have been fully replicated and applied (i.e. persisted) before writing the new event, and thereby guarantees strong consistency when switching from one region to another. Subsequent write requests to the primary region of the entity are handled locally without any further coordination. Write requests to other regions will trigger the same switch-over process. All other entity instances operate unimpeded during the switch-over process.

This is useful for scenarios where you want to have the primary region for your data close to the users who use the data. A user, Alice, in the USA, will have her data in the USA, while a user Bob, in the UK, will have his data, in the UK. If Alice travels to Asia the data will follow her.

The Operating section explains more details about [configuring the primary selection mode](../operations/regions/index.html#selecting-primary).

### <a href="about:blank#_illustrating_entities_with_pinned_region_selection"></a> Illustrating entities with pinned region selection

![Geo data replication](_images/geo-a.svg)


In the image above, the entity representing Alice has its primary region in Los Angeles. When a user A in the primary region performs a read request
![steps 1](_images/steps-1.svg)
, the request is handled locally, and the response sent straight back
![steps 2](_images/steps-2.svg)
.

When the user in the primary region performs a write request
![steps 1](_images/steps-1.svg)
, that request is also handled locally, and a response sent directly back
![steps 2](_images/steps-2.svg)
. After that write request completes, that write is replicated to other regions
![steps 3](_images/steps-3.svg)
, such as in London (UK).

A user B in London, when they perform a read
![steps 4](_images/steps-4.svg)
, that read operation will happen locally, and a response sent immediately back
![steps 5](_images/steps-5.svg)
.

A user can also perform write operations on entities in non-primary regions.

![Geo data replication](_images/geo-b.svg)


In this scenario, the user B in London (UK) is performing a write operation on the Alice entity
![steps 1](_images/steps-1.svg)
. Since London is not the primary region for the Alice entity, Akka will automatically forward that request to the primary region
![steps 2](_images/steps-2.svg)
, in this case, Los Angeles (USA). That request will be handled in the USA, and a response sent directly back to the user
![steps 3](_images/steps-3.svg)
.

![Geo data replication](_images/geo-c.svg)


When Bob makes a request in the UK on his data
![steps 1](_images/steps-1.svg)
, that request is handled locally
![steps 2](_images/steps-2.svg)
, and replicated to the US
![steps 3](_images/steps-3.svg)
. Exactly the same as Alice’s requests in the USA with her data are handled locally in the USA, and replicated to the UK.

The data however is still available in all regions. If Bob travels to the USA, he can access his data in the Los Angeles region.

![Geo data replication](_images/geo-d.svg)


When Bob travels to the USA, read requests that Bob makes on his data are handled locally
![steps 1](_images/steps-1.svg)
and getting an immediate reply
![steps 3](_images/steps-3.svg)
. Write requests, on the other hand, are forwarded to the UK
![steps 2](_images/steps-2.svg)
, before the reply is sent
![steps 3](_images/steps-3.svg)
.

![Geo data replication](_images/geo-e.svg)


Meanwhile, all requests made by Alice on her data are handled locally
![steps 1](_images/steps-1.svg)
and get an immediate reply
![steps 2](_images/steps-2.svg)
. The write operations are being replicated to the UK
![steps 3](_images/steps-3.svg)
.

## <a href="about:blank#_replicated_writes"></a> Replicated writes

The replicated write replication strategy allows every region to be capable of handling writes for all entities. This is done through the use of CRDTs, which can be modified concurrently in different regions, and their changes safely merged without conflict.

## <a href="about:blank#_replication_guarantees"></a> Replication Guarantees

Akka guarantees that all events created within one region are eventually replicated to all other regions in the project.

Each entity’s state is a series of events that are persisted in a local event journal, which acts as the source of events that must be replicated from one region to another. Having a durable, local event journal is the foundation for how Akka can recover an entity’s state in the event of failure.

Each event has a sequence number that is validated on the receiving side to guarantee correct ordering and exactly-once processing of the events. A replicated event is processed by entities in other regions by having the event added to the local event journal of each entity. Once added to the local event journal, the replicated event can be used to update the entity’s state and handle read requests in those regions.

Events are delivered to other regions over a brokerless, streaming gRPC transport. The entity instance that needs to receive replicated events is a consumer and the entity that generated the events is the producer. Events flow from the producing region to the consuming region. An offset of the replication stream is stored on the consumer side, which will start from the previously stored offset when it initiates the replication stream. The producer side will publish events onto the replication stream directly while writing an entity or from reading the event journal after a failure. Duplicate events are detected and filtered out by the sequence numbers of the events. These replication streams can be sharded over many nodes to support high throughput.

<!-- <footer> -->
<!-- <nav> -->
[Entity state models](state-model.html) [Saga patterns](saga-patterns.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->