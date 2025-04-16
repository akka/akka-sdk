# Multi-region operations

Akka applications run in multiple regions with their data transparently and continuously replicated even across multiple cloud providers.
Akka applications do not require code modifications to run within multiple regions. Operators define controls to determine which regions
an application will operate within and whether that application’s data is pinned to one region or replicated across many.

Akka ensures regardless of which region receives a request, the request can be serviced. Multiple replication strategies can be configured, with each offering varying features for different use cases.

Multi-region operations are ideal for:

* Applications that require 99.9999% availability
* Geographic failover
* Geo-homing of data for low latency access
* Low latency global reads
* Low latency global writes

## Replicated reads

Akka’s replicated reads offers full data replication across regions and even cloud providers, without any changes to the service implementation: an entity has its "home" in one _primary region_, while being replicated to multiple other regions.

![Geo data replication](geo-a.svg)

In the image above, the entity representing Alice has its primary region in Los Angeles. When a user A in the primary region performs a read request ![width=20](steps-1.svg), the request is handled locally, and the response sent straight back ![width=20](steps-2.svg).

When the user in the primary region performs a write request ![width=20](steps-1.svg), that request is also handled locally, and a response sent directly back ![width=20](steps-2.svg). After that write request completes, that write is replicated to other regions ![width=20](steps-3.svg), such as in London (UK).

A user B in London, when they perform a read ![width=20](steps-4.svg), that read operation will happen locally, and a response sent immediately back ![width=20](steps-5.svg).

A user can also perform write operations on entities in non-primary regions.

![Geo data replication](geo-b.svg)

In this scenario, the user B in London (UK) is performing a write operation on the Alice entity ![width=20](steps-1.svg). Since London is not the primary region for the Alice entity, Akka will automatically forward that request to the primary region ![width=20](steps-2.svg), in this case, Los Angeles (USA). That request will be handled in the USA, and a response sent directly back to the user ![width=20](steps-3.svg).

![Geo data replication](geo-c.svg)

When Bob makes a request in the UK on his data ![width=20](steps-1.svg), that request is handled locally ![width=20](steps-2.svg), and replicated to the US ![width=20](steps-3.svg). Exactly the same as Alice’s requests in the USA with her data are handled locally in the USA, and replicated to the UK.

The data however is still available in all regions. If Bob travels to the USA, he can access his data in the Los Angeles region.

![Geo data replication](geo-d.svg)

When Bob travels to the USA, read requests that Bob makes on his data are handled locally ![width=20](steps-1.svg) and getting an immediate reply ![width=20](steps-3.svg). Write requests, on the other hand, are forwarded to the UK ![width=20](steps-2.svg), before the reply is sent ![width=20](steps-3.svg).

![Geo data replication](geo-e.svg)

Meanwhile, all requests made by Alice on her data are handled locally ![width=20](steps-1.svg) and get an immediate reply ![width=20](steps-2.svg). The write operations are being replicated to the UK ![width=20](steps-3.svg).

### Primary selection

How Akka assigns the primary region to an entity is configurable. The two main modes are ***static****, and **dynamic***.

In the ***static primary selection*** mode (which is the default), the primary region for an entity is selected statically as part of the deployment, so all entities have the same primary region. This is useful for scenarios where you want one primary region, with the ability to fail over to another region in the case of a regional outage.

In the ***dynamic primary selection*** mode, each entity can have a different region that is considered its primary region. This is selected based on whichever region the entity was first written in. This is useful for scenarios where you want to have the primary region for you data close to the users who use the data. A user, Alice, in the USA, will have her data in the USA, while a user Bob, in the UK, will have his data, in the UK.

The Operating section explains more details about [configuring the primary selection mode](operations:regions/index.adoc#selecting-primary).

## Replicated writes

The replicated write replication strategy allows every region to be capable of handling writes for all entities. This is done through the use of CRDTs, which can be modified concurrently in different regions, and their changes safely merged without conflict.
