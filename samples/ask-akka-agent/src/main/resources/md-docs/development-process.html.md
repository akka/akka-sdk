<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Development process](development-process.html)

<!-- </nav> -->

# Development process

The main steps in developing a service with Akka are:

1. [Create a project](about:blank#_create_a_project)
2. [Specify service interface and domain model](about:blank#_specify_service_interface_and_domain_model)
3. [Implementing components](about:blank#_implement_components)
4. [Exposing components through Endpoints and Consumers](about:blank#_endpoints)
5. [Testing your application](about:blank#_create_unit_tests)
6. [Running locally](about:blank#_run_locally)
7. [Package service](about:blank#_package_service)
8. [Deploy](about:blank#_deploy)

## <a href="about:blank#_create_a_project"></a> Create a project

All services and applications start as a Java project. Akka has a getting started sample that makes this easier. You will code your service in this project. See [Author your first agentic service](../getting-started/author-your-first-service.html) for more details.

## <a href="about:blank#_specify_service_interface_and_domain_model"></a> Specify service interface and domain model

Creating services in Akka follows the architecture outlined in [Akka Architecture](architecture-model.html#_architecture). You start with your domain model, which models your business domain in plain old Java objects. Then you will create Akka components to coordinate them.

The main components of an Akka service are:

- Stateful [Entities](../reference/glossary.html#entity)
- Stateful [Workflows](../reference/glossary.html#workflow)
- [Views](../reference/glossary.html#view)
- [Timed Actions](../reference/glossary.html#timed_action)
- [Consumers](../reference/glossary.html#consumer)
We recommend that you separate the service API and Entity domain data model. Separating the service interface and data model in different classes allows you to evolve them independently.

|  | Kickstart a project using the [getting started guide](../getting-started/author-your-first-service.html). |

## <a href="about:blank#_implement_components"></a> Implementing components

In Akka, services can be stateful or stateless, and the components you implement depend on the service type.

Stateful services utilize components like [Event Sourced Entities](../java/event-sourced-entities.html), [Key Value Entities](../java/key-value-entities.html), [Workflows](../java/workflows.html), and [Views](../java/views.html), while stateless services focus on exposing functionality via [HTTP Endpoints](../java/http-endpoints.html). Typically, a stateful service is centered around one Entity type but may also include Endpoints and Views to expose or retrieve data.

### <a href="about:blank#_entities"></a> Entities

Stateful services encapsulate business logic in Key Value Entities or Event Sourced Entities. At runtime, command messages invoke operations on Entities. A command may only act on one Entity at a time.

|  | To learn more about Akka entities see [Implementing Event Sourced Entities](../java/event-sourced-entities.html) and [Implementing key value entities](../java/key-value-entities.html). |
If you would like to update multiple Entities from a single request, you can compose that in the Endpoint, Consumer or Workflow.

Services can interact asynchronously with other services and with external systems. Event Sourced Entities emit events to a journal, which other services can consume. By defining your Consumer components, any service can expose their own events and consume events produced by other services or external systems.

### <a href="about:blank#_workflows"></a> Workflows

Akka Workflows are high-level descriptions to easily align business requirements with their implementation in code. Orchestration across multiple services including failure scenarios and compensating actions is simple with [Workflows](../java/workflows.html).

### <a href="about:blank#_views"></a> Views

A View provides a way to retrieve state from multiple Entities based on a query. You can create views from Key Value Entity state, Event Sourced Entity events, and by subscribing to topics. For more information about writing views see [Implementing Views](../java/views.html).

### <a href="about:blank#_timed_actions"></a> Timed Actions

Timed Actions allow scheduling future calls, such as verifying process completion. These timers are persisted by the Akka Runtime and guarantee execution at least once.

For more details and examples take a look at the following topics:

- [Event Sourced Entities](../java/event-sourced-entities.html)
- [Key Value Entities](../java/key-value-entities.html)
- [Workflows](../java/workflows.html)
- [Views](../java/views.html)
- [Timed Actions](../java/timed-actions.html)

## <a href="about:blank#_endpoints"></a> Exposing components through Endpoints and Consumers

Endpoints are the primary means of exposing your service to external clients. You can use HTTP or gRPC Endpoints to handle incoming requests and return responses to users or other services. Endpoints are stateless.

To handle event-driven communication, Akka uses Consumers. Consumers listen for and process events or messages from various sources, such as Event Sourced Entities, Key Value Entities, or external messaging systems. They play a key role in enabling asynchronous, event-driven architectures by subscribing to event streams and reacting to changes in state or incoming data.

In addition to consuming messages, Consumers can also produce messages to topics, facilitating communication and data
flow between different services. This makes them essential for coordinating actions across distributed services and ensuring smooth interaction within your application ecosystem.

For more information, refer to:

- [Designing HTTP endpoints](../java/http-endpoints.html)
- [Designing gRPC Endpoints](../java/grpc-endpoints.html)
- [Consuming and producing](../java/consuming-producing.html)

## <a href="about:blank#_create_unit_tests"></a> Testing your application

Writing automated tests for your application is a good practice. Automated testing helps catch bugs early in the development process, reduces the likelihood of regressions, enables confident refactoring, and ensures your application behaves as expected. There are three main types of tests to consider: unit tests, integration tests, and end-to-end tests.

### <a href="about:blank#_unit_tests"></a> Unit Tests

Unit tests focus on testing individual components in isolation to ensure they work as intended. The Akka SDK provides a test kit for unit testing your components.

### <a href="about:blank#_integration_tests"></a> Integration Tests

Integration tests validate the interactions between multiple components or services within your application, ensuring that different parts of your system work together as intended.

### <a href="about:blank#_end_to_end_tests"></a> End-to-End Tests

End-to-end tests validate the entire application by simulating real-world user scenarios. These tests span multiple services or modules to ensure the system functions correctly as a whole, whether within the same project or across different projects. For example, you might test the data flow between two Akka services in the same project using service-to-service eventing. Akka also offers flexible configuration options to accommodate various environments.

## <a href="about:blank#_run_locally"></a> Running locally

You can test and debug your services by [running them locally](../java/running-locally.html) before deploying your *Service*. This gives you a local debug experience that is convenient and easy.

## <a href="about:blank#_package_service"></a> Package service

You use Docker to package your service and any of its dependencies for deployment. Distributing services as docker images makes Akka more cloud friendly and works well with containerization tools.

See [container registries](../operations/projects/container-registries.html) for more information.

## <a href="about:blank#_deploy"></a> Deploy

After testing locally, deploy your service to Akka Automated Operations (akka.io) using the CLI or the Console.

The following pages provide information about deployment:

- [Akka projects](../operations/projects/index.html)
- [Deploying a packaged service](../operations/services/deploy-service.html#_deploying_a_service)

## <a href="about:blank#_next_steps"></a> Next steps

Now that you have a project and have deployed it you should familiarize yourself with operating an Akka project. See [Operating Akka](../operations/index.html) for more information about operating Akka services.

The following topics may also be of interest.

- [Developer best practices](../java/dev-best-practices.html)

<!-- <footer> -->
<!-- <nav> -->
[Deployment model](deployment-model.html) [Declarative effects](declarative-effects.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->