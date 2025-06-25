<!-- <nav> -->
- [Akka](../index.html)
- [AI coding assistant guidelines](ai-coding-assistant-guidelines.html)

<!-- </nav> -->

# AI coding assistant guidelines

Don’t generate any code yet, but remember the following guidelines and use them when writing code in this project.

## <a href="about:blank#_general"></a> General

- Use the Akka SDK version 3.3 or later. Don’t use Akka libraries (actors), or Kalix SDK.
- Use the Onion Architecture and follow `com.[domain-module].[api|application|domain]` package structure.

## <a href="about:blank#_domain_model"></a> Domain Model

- Use Java records for the domain model, events, commands, requests and responses, because those are more concise and are serializable by default.
- Prefer Java `Optional` for nullable values.

## <a href="about:blank#_entities"></a> Entities

- Commands can be defined as records inside the entity and they don’t have to have a common interface.
- Command handlers should be implemented in the entity and not in the domain object. The domain object should have methods for business logic validations but it should not handle commands directly or be concerned with entity effects.
- Command handlers that make updates without returning any information should return `akka.Done` for successful responses and `effects().error()` for validation errors. Use static import for `akka.Done.done()` method.
- Events should be defined in the domain package and the events for an entity should have a common sealed interface, and define `@TypeName` for serialization.
- `applyEvent` method should never return null, return the current state or throw an exception.
- Key Value Entities, Event Sourced Entities, Workflows can accept only single method parameter, wrap multiple parameters in a record class.

## <a href="about:blank#_workflows"></a> Workflows

- State of a workflow can be defined as a record inside the workflow.

## <a href="about:blank#_views"></a> Views

- Views queries that potentially select many rows should return a record that contains a single field with a list of results.

## <a href="about:blank#_endpoints"></a> Endpoints

- Implement HTTP endpoints with `@HttpEndpoint` and path annotations.
- Request and response endpoints should be defined as records inside the endpoint. Domain objects should not be exposed to the outside by the endpoint but instead be converted to the request and response objects of the endpoint. Include a `fromDomain` conversion method in the response record if there are many fields or nested records that needs to be converted from domain to endpoint records.
- Endpoints can return the response directly, without `CompletionStage`, since we prefer to use non-async code. For endpoint methods that create or update something it can return `HttpResponse` and return for example `HttpResponses.created()` or `HttpResponses.ok()`.
- Use `ComponentClient` for inter-component communication. When using the `componentClient` you should use the `method` followed by `invoke`. Don’t use `invokeAsync` since we prefer to use non-async code, without composition of `CompletionStages`.

## <a href="about:blank#_testing"></a> Testing

- Extend `TestKitSupport` for integration tests.
- Use `EventSourcedTestKit` for unit tests of Event Sourced Entity. Create a new instance of the testkit for each test method.
- Use JUnit 5 annotations (@Test, etc.)
- Use `componentClient` for testing components.

<!-- <footer> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->