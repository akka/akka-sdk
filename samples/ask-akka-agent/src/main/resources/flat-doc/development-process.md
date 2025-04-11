# Development process

The main steps in developing a service with Akka are:

1. [Create a project](#create-a-project)
2. [Specify service interface and domain model](#specify-service-interface-and-domain-model)
3. [Implementing components](#implementing-components)
4. [Exposing components through Endpoints and Consumers](#exposing-components-through-endpoints-and-consumers)
5. [Testing your application](#testing-your-application)
6. [Running locally](#running-locally)
7. [Package service](#package-service)
8. [Deploy to akka.io](#deploy-to-akka.io)

## Create a project
All services and applications start as a Java project. Akka has a Maven archetype that makes this easier. You will code your service in this project. See [java:author-your-first-service.adoc](java:author-your-first-service.adoc) for more details.

## Specify service interface and domain model

Creating services in Akka follows the architecture outlined in [Akka Architecture](concepts:architecture-model.adoc#_architecture). You start with your domain model, which models your business domain in plain old Java objects. Then you will create Akka components to coordinate them.

The main components of an Akka service are:

* Stateful [Entities](reference:glossary.adoc#entity)
* Stateful [Workflows](reference:glossary.adoc#workflow)
* [Views](reference:glossary.adoc#view)
* [Timed Actions](reference:glossary.adoc#timed_action)
* [Consumers](reference:glossary.adoc#consumer)

We recommend that you separate the service API and Entity domain data model. Separating the service interface and data model in different classes allows you to evolve them independently.

**💡 TIP**\
Kickstart a project using the [getting started guide](java:author-your-first-service.adoc).

## Implementing components

In Akka, services can be stateful or stateless, and the components you implement depend on the service type.

Stateful services utilize components like [Event Sourced Entities](java:event-sourced-entities.adoc), [Key Value Entities](java:key-value-entities.adoc), [Workflows](java:workflows.adoc), and [Views](java:views.adoc), while stateless services focus on exposing functionality via [HTTP Endpoints](java:http-endpoints.adoc). Typically, a stateful service is centered around one Entity type but may also include Endpoints and Views to expose or retrieve data.

### Entities

Stateful services encapsulate business logic in Key Value Entities or Event Sourced Entities. At runtime, command messages invoke operations on Entities. A command may only act on one Entity at a time.

**💡 TIP**\
To learn more about Akka entities see [java:event-sourced-entities.adoc](java:event-sourced-entities.adoc) and [java:key-value-entities.adoc](java:key-value-entities.adoc).

If you would like to update multiple Entities from a single request, you can compose that in the Endpoint, Consumer or Workflow.

Services can interact asynchronously with other services and with external systems. Event Sourced Entities emit events to a journal, which other services can consume. By defining your Consumer components, any service can expose their own events and consume events produced by other services or external systems.

### Workflows

Akka Workflows are high-level descriptions to easily align business requirements with their implementation in code. Orchestration across multiple services including failure scenarios and compensating actions is simple with [Workflows](java:workflows.adoc).

### Views

A View provides a way to retrieve state from multiple Entities based on a query. You can create views from Key Value Entity state, Event Sourced Entity events, and by subscribing to topics. For more information about writing views see [java:views.adoc](java:views.adoc).

### Timed Actions

Timed Actions allow scheduling future calls, such as verifying process completion. These timers are persisted by the Akka Runtime and guarantee execution at least once.

For more details and examples take a look at the following topics:

* [Event Sourced Entities](java:event-sourced-entities.adoc)
* [Key Value Entities](java:key-value-entities.adoc)
* [Workflows](java:workflows.adoc)
* [Views](java:views.adoc)
* [Timed Actions](java:timed-actions.adoc)

## Exposing components through Endpoints and Consumers
Endpoints are the primary means of exposing your service to external clients. You can use HTTP Endpoints to handle incoming requests and return responses to users or other services. Endpoints are stateless.

To handle event-driven communication, Akka uses Consumers. Consumers listen for and process events or messages from various sources, such as Event Sourced Entities, Key Value Entities, or external messaging systems. They play a key role in enabling asynchronous, event-driven architectures by subscribing to event streams and reacting to changes in state or incoming data.

In addition to consuming messages, Consumers can also produce messages to topics, facilitating communication and data
flow between different services. This makes them essential for coordinating actions across distributed services and ensuring smooth interaction within your application ecosystem.

For more information, refer to:

* [java:http-endpoints.adoc](java:http-endpoints.adoc)
* [java:consuming-producing.adoc](java:consuming-producing.adoc)

## Testing your application

Writing automated tests for your application is a good practice. Automated testing helps catch bugs early in the development process, reduces the likelihood of regressions, enables confident refactoring, and ensures your application behaves as expected. There are three main types of tests to consider: unit tests, integration tests, and end-to-end tests.

### Unit Tests

Unit tests focus on testing individual components in isolation to ensure they work as intended. The Akka SDK provides a test kit for unit testing your components.

### Integration Tests

Integration tests validate the interactions between multiple components or services within your application, ensuring that different parts of your system work together as intended.

### End-to-End Tests

End-to-end tests validate the entire application by simulating real-world user scenarios. These tests span multiple services or modules to ensure the system functions correctly as a whole, whether within the same project or across different projects. For example, you might test the data flow between two Akka services in the same project using service-to-service eventing. Akka also offers flexible configuration options to accommodate various environments.

## Running locally

You can test and debug your services by [running them locally](java:running-locally.adoc) before deploying your _Service_. This gives you a local debug experience that is convenient and easy. 

## Package service

You use Docker to package your service and any of its dependencies for deployment. Distributing services as docker images makes Akka more cloud friendly and works well with containerization tools.

See [container registries](operations:projects/container-registries.adoc) for more information.

## Deploy to akka.io

After testing locally, deploy your service to akka.io using the CLI or the Console. The following pages provide information about deployment:

* [Akka projects](operations:projects/index.adoc)
* [Deploying a packaged service](operations:services/deploy-service.adoc#_deploying_a_service)

## Next steps

Now that you have a project and have deployed it you should familiarize yourself with operating an Akka project. See [operations:index.adoc](operations:index.adoc) for more information about operating Akka services.

The following topics may also be of interest.

* [java:dev-best-practices.adoc](java:dev-best-practices.adoc)
