<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Deployment model](deployment-model.html)

<!-- </nav> -->

# Deployment model

Akka applications are distributed by design. Although you can run many complex services on a single machine, they are meant to be distributed. This guide explains deployment and distribution in Akka.

## <a href="about:blank#_deployment_alternatives"></a> Deployment alternatives

Akka provides three ways to run services:

- Fully-automated regions with Akka Automated Operations (at [akka.io](https://console.akka.io/), optionally running in your cloud infrastructure)
- Self-managed operations (in your infrastructure, or any cloud service)
- Akka SDK development and testing (on the developer’s machine)
Akka services run in all three modes **without** code modifications and leverage the fundamental underpinnings of Akka such as clustering. While standalone deployments enable running Akka on any infrastructure, some environments limit network access thus prohibit Akka clustering.

### <a href="about:blank#_fully_automated_regions_with_akka_automated_operations"></a> Fully-automated regions with Akka Automated Operations

![Deployment model: Akka Automated Operations](_images/deployment-model_akka-platform.png)


Full automation across multiple cloud provider regions and multiple cloud providers is available through Akka’s fully-managed platform. You choose to run your Akka services in

- multi-tenant clusters (for simplicity),
- private regions in your VPC (managed by Akka), or
- self-hosted regions (managed by you).
Akka Automated Operations manages the fully-automated regions running your Akka services and takes responsibility for SLAs tied to elasticity, agility, and resilience of your Akka services.

### <a href="about:blank#_self_managed_nodes"></a> Self-managed nodes

![Deployment model: self-managed operations](_images/deployment-model_self-managed.png)


For organizations that want control over how Akka services are installed, updated, and maintained. Akka services are packaged into standalone binaries with Akka clustering for scaling and deploy to self-managed nodes. You are responsible for separately managing secure connectivity, routes, installation, deployment, and persistence.

### <a href="about:blank#_akka_sdk_development_and_testing"></a> Akka SDK development and testing

![Deployment model: development and testing](_images/deployment-model_akka-sdk.png)


During development, Akka services run directly on the developers' machines, enabling fast iteration cycles without the hassle of shared infrastructure.

## <a href="about:blank#_logical_deployment"></a> Logical deployment

In Akka the logical unit of deployment is a Service. A service contains your components, and it deploys into a project. The hierarchy is described below.

### <a href="about:blank#_project"></a> Project

A project is the root of one or more services that are meant to be deployed and run together. The project is a logical container for these services and provides some common management capabilities. Projects contain a list of [regions](about:blank#_region) that they deploy to. One of the regions will always be the **primary region** which acts as the source of resources to replicate between regions. This will be the first region that deployments roll out to. By default, the primary region is the first region specified when [creating a project](../operations/projects/create-project.html). More detail on regions is covered below in [physical deployment](about:blank#_physical_deployment).

### <a href="about:blank#_services"></a> Service(s)

[Services](../operations/services/index.html) are the core unit of deployment in Akka. They equate to the concept of a microservice and contain all your components and objects as described in the [Akka architecture model](architecture-model.html). A project contains one or more services. Services can be started, stopped, and paused independently. They can also be scaled independently making them also the unit of scale.

## <a href="about:blank#_physical_deployment"></a> Physical deployment

Akka services run in a cluster. A cluster is an instance of the Akka runtime which is spread over multiple machines in a given geographical place, also known as an Akka cluster. We call that place a [region](about:blank#_region). In our cloud service a region corresponds to a region from a cloud infrastructure provider such as AWS, Azure, or Google Cloud. "US East" is an AWS region that contains multiple Availability Zones: for Akka this is just a region. By default, Akka spans availability zones in our cloud.

### <a href="about:blank#_region"></a> Region

[Regions](../operations/organizations/regions.html) are specific clusters running Akka in a specific place. Regions are designed to run independently for greater availability. They are also designed to replicate data asynchronously between each other. Every Akka [project](about:blank#_project) specifies a list of one or more regions in which it runs.

In addition to your services from a project, a region also has endpoints for your API. You define endpoints within the [API layer](architecture-model.html#_architecture) and Akka will expose them. They will be unique to each region. That is they will have unique DNS, much like AWS services S3 and SQS do.

[Container registries](../operations/projects/container-registries.html) exist in all Akka.io regions so that the images that are your packaged services are in close proximity to the compute they will run on.

A given cloud provider region *may* have many Akka regions within it. In this way Akka can scale past any upper bound limits of the infrastructure on which it runs.

### <a href="about:blank#_multiple_region_by_design"></a> Multiple region by design

Stateful components in Akka have a concept of location, that is region, and are designed to span regions and replicate their data across regions. This is outlined in [state model](state-model.html) and [multi-region operations](multi-region.html). These components call the region where they can be updated their **primary region** and keep track of it throughout their lifetime. This allows Akka to simplify some aspects of distributed computing.

In Akka projects, regional choices are made at deployment time, not development time, allowing you to change your regions over time and even change your primary region. You can also control if your service is in **pinned-region** or **request-region** mode of regionalization, which impacts where the primary copy of component data resides. For more information see [multi-region operations](multi-region.html).

By default, most entities will only allow their primary region to change their state, to make this easier Akka will also automatically route state changing operations to the primary region. This gives you a read anywhere model out of the box that automatically routes writes appropriately.

|  | Primary region is also a concept in the context of a project. All projects have a primary region. |
If you use more sophisticated state models you can have a write anywhere model. See [state model](state-model.html) for more details.

## <a href="about:blank#_managing_physical_deployment"></a> Managing physical deployment

You manage a project as a single unit and Akka does all the work to manage each specific region that it spans. When you deploy a service to a project with multiple regions Akka will deploy the service to all of the regions on your behalf. You execute one command, but many are taking place behind the scenes.

### <a href="about:blank#_operating_regions_within_a_project"></a> Operating regions within a project

Regions can be added to or removed from an Akka project while it is running. There is no need to stop the services within the project to do this.

#### <a href="about:blank#_adding_regions"></a> Adding regions

You can add regions to an Akka project and Akka will deploy the services in that project to the new region and expose endpoints. It will also begin replication of all data. This replication time will depend on the size of the dataset (state) being replicated and the distance between the regions. Adding regions is a graceful behavior. The new region will be available to serve traffic after being added, but it may take some time until the initial replication is complete. You can monitor replication by monitoring Consumer Lag.

#### <a href="about:blank#_removing_regions"></a> Removing regions

There are two ways to remove regions from a project: **graceful** and **emergency**. Their names imply their function. Graceful remove takes time, it marks the region as read-only, and starts to move the origin for all the entities out of the region. This is the preferred way to remove regions as there is no risk of data corruption, even for entities that haven’t implemented conflict resolution strategies.

Emergency region removal is for emergency scenarios such as the rare times when an infrastructure provider’s region becomes unavailable. Executing an emergency region removal fast ejects the region and reassigns the origin for all entities to the remaining regions. If there are writes in progress for non-CRDT types (i.e. entities that do not implement conflict resolutions) they *could* be lost or overwritten.

## <a href="about:blank#_about_akka_clustering"></a> About Akka clustering

Akka clustering is the heart of how Akka systems take responsibility for their own outcomes. Akka services cluster from within, forming and managing a cluster from within the Akka runtime that is running alongside your Akka service within the JVM. When Akka services boot, they discover one another and automatically form an Akka cluster. An Akka service instance only needs to connect to one other node in order to join an existing cluster.

Akka clustering provides numerous services to facilitate elastic scaling, failover, traffic steering, communications and security. The Akka runtime has its own discovery, consensus, split brain, zero trust,
request routing, scheduling, conflict-free replication, fan out/in compute, and point-to-point brokerless messaging between services within a cluster.

Akka leverages these capabilities to enable large scale resilience for stateful systems which includes failover, data sharding, data rebalancing, and routing end user traffic to the right data. This effectively allows Akka services to act as their own orchestrator and in-memory cache, that also happen to be durable and resilient.

## <a href="about:blank#_next_steps"></a> Next steps

Now that you understand the overall architecture and deployment model of Akka you are ready to learn more about the [Development process](development-process.html).

The following topics may also be of interest.

- [State model](state-model.html)
- [Developer best practices](../java/dev-best-practices.html)
- [Architecture model](architecture-model.html)

<!-- <footer> -->
<!-- <nav> -->
[Architecture model](architecture-model.html) [Development process](development-process.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->