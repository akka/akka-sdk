<!-- <nav> -->
- [Akka](../index.html)
- [Operating](index.html)
- [Operator best practices](operator-best-practices.html)

<!-- </nav> -->

# Operator best practices

## <a href="about:blank#_regionalization_precautions"></a> Regionalization precautions

### <a href="about:blank#_primary_selection_mode"></a> Primary selection mode

Akka services have two different modes, **request-region** or **pinned-region**, which control how they perform replication for stateful components. This is outlined in [Selecting primary for stateful components](regions/index.html#selecting-primary). It is important to note that setting this mode has the following implications for your project.

#### <a href="about:blank#_event_sourced_entities"></a> Event Sourced Entities

If the primary selection mode is request-region then each entity instance will use the region where the write requests occur, after synchronizing events from the previous primary region.

If the service is set to pinned-region primary selection mode Event Sourced Entities will use the primary project region as their primary data region. They will still replicate events, and hence state, to all regions in the project, but will only be writeable in the primary. Akka will route update requests to this region from any endpoint.

#### <a href="about:blank#_key_value_entities"></a> Key Value Entities

Key Value Entities handle writes, reads and forwarding of requests in the same way as Event Sourced Entities.

#### <a href="about:blank#_workflows"></a> Workflows

Workflows handle writes, reads and forwarding of requests in a similar way as Event Sourced Entities, but they keep the primary in the region where the workflow was created. Akka will route update requests to this region from any endpoint. Workflow actions are only performed by the primary Workflow instance.

### <a href="about:blank#_primary_region"></a> Primary region

Changing primary regions is a serious operation and should be thought out carefully. Ideally you plan this ahead of time and synchronize the regions by allowing the replication lag to drop to zero. You can put the project into a read only mode that will stop any writes from happening if you want to be sure that there will be zero data collisions when you change the primary.

### <a href="about:blank#_container_registries"></a> Container registries

Container registries are regional in Akka. If you decide to use [Configure an external container registry](projects/external-container-registries.html) be aware that you should have container registries in or near each of the regions in your project. If you only have your container images in one place and that place becomes unavailable your services will not be able to start new instances.

<!-- <footer> -->
<!-- <nav> -->
[System Configuration](cli/system-config.html) [Resources](../resources.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->