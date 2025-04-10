# Welcome to Akka

Akka is a platform designed for building and running responsive applications. It adheres to the **Reactive Principles**, which promote creating services and systems that are elastic, agile, and resilient.

## Key Principles
* **Elastic**: Akka applications automatically scale to varying workloads and position active-active replicas close to users.
* **Agile**: Akka allows for updates, rebalancing, and repartitioning of workloads, enabling no-downtime maintenance.
* **Resilient**: Akka applications function as self-managed in-memory databases, ensuring recoverability and replicability to handle potential failures.

## Development and Operations
Akka simplifies the development and operation of responsive applications by providing libraries, components, sandboxes, buildpacks, and a cloud runtime. The platform self-manages infrastructure to ensure elasticity, agility, and resilience, allowing applications to achieve their Service Level Agreement (SLA) goals. This includes managing database persistence, networking, clustering, and orchestration.

Akka applications are inherently event-driven, allowing developers to focus on building durable, stateful services without needing to navigate complex messaging or asynchronous programming issues. A new developer can design and implement stateful services within a day, achieving low latency and burstable performance of up to 1 million input/output operations per second (IOPS). This is made possible by integrating microservices best practices into the application and runtime transparently.

## Building Akka Applications
There are two ways to build applications with Akka:

### Akka SDK
The Akka software development kit (SDK) includes components with a local console, debugger, and runtime. The components let you build durable, transactional, real-time services. It enforces separation of domain logic from infrastructure concerns.

* **Operations**: SDK applications are DevOps-ready and can be deployed in any of the following environments:
  * Akka’s Serverless environment
  * Any hyperscaler (Bring Your Own Cloud, or BYOC)
  * A Kubernetes environment with an Akka orchestrator (self-hosted)

### Akka Libraries
* **Overview**: The libraries are open-source modules that are foundational for creating distributed systems including actors, networking, streams, persistence, and clustering.
* **Operations**: Services built with these libraries are self-managed with DevOps packaging them into microservices runtime bundles. They do not operate within Akka’s Serverless, BYOC, or Self-Hosted environments.

## Developer Experience
Developers need only Java 21 and Maven as dependencies. The Akka SDK build process automatically pulls the necessary modules, enabling inter-service communication without complex orchestration. Local services include an embedded persistence store for creating distributed entities.

Developers can access a local console that replicates runtime observability, tracing, and debugging capabilities. Applications can be pushed directly to a cloud runtime or deployed via a Continuous Integration/Continuous Deployment (CI/CD) pipeline.

## DevOps Operations
Operators can configure application elasticity without deep architectural knowledge. Akka automates instance management to meet traffic needs while preserving performance SLAs. Services can be replicated [across multiple regions](concepts:multi-region.adoc), spanning different geographies and environments.
Stateful services can be read-replicated or write-replicated with conflict resolution options. Services can migrate between locations without downtime and can be restarted from specific points in time.
Developers have access to a component library for creating various application types, including transactional, durable, Artificial Intelligence (AI) Retrieval-Augmented Generation (RAG), analytics, edge, event-sourced, and streaming applications.

## Key Components
* **Entities**: Act as in-memory databases, either event-sourced or key-value.
* **Endpoints**: Expose your services to the outside world, functioning similarly to Cloudflare Workers when deployed [across multiple regions](concepts:multi-region.adoc).
* **Timed Actions**: Scheduled executions that are reliable and guaranteed to run at least once.
* **Views**: Streaming projections that implement the Command Query Responsibility Segregation (CQRS) pattern, separating reads from writes across multiple services.
* **Workflows**: Durable, long-running processes orchestrated through Saga patterns.

## Interoperability and Integration
Akka applications seamlessly integrate into larger service ecosystems. Components communicate via a typed _Component_ _Client_, and services can advertise state changes through events, ensuring reliable data replication and decoupled messaging.

## Streaming
Akka’s event-sourced model is fundamentally streaming-based, providing a robust framework for handling events. Akka integrates with many messaging brokers to both interface with the outside world and to bring the benefits of Akka to non-Akka platforms and software. 

## Getting Started with Akka
There are multiple ways to get started with Akka.

* Hands-on
  * **Deploy, scale, and replicate a pre-built service.** Take the 5 minute walk-through by creating a free account at [akka.io](https://console.akka.io/register).
  * **Implement a "Hello World" service.** [java:author-your-first-service.adoc](java:author-your-first-service.adoc).
  * **Review a pre-built service implementation.** See some Akka components in the [java:shopping-cart-quickstart.adoc](java:shopping-cart-quickstart.adoc).
* Understand
  * ... the Akka [concepts:architecture-model.adoc](concepts:architecture-model.adoc) for architects and developers.
  * ... the Akka [concepts:deployment-model.adoc](concepts:deployment-model.adoc) for operators.
  * ... the Akka [concepts:development-process.adoc](concepts:development-process.adoc).
* **Start a local development environment** Getting started with Integrated Development Environment (IDE) integration, local debugging, and packing services into images. See [java:running-locally.adoc](java:running-locally.adoc).
* **Setup** CI/CD pipelines, external Docker repositories, external messaging brokers, or 3rd party observability.
See [Operating Services](operations:index.adoc) for more information.
