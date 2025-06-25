<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Integrations](integrations/index.html)
- [Component and service calls](component-and-service-calls.html)

<!-- </nav> -->

# Component and service calls

An Akka service comprises many components. Such components might depend on one another, on other Akka services or even external services. This section describes how to call other components and services from within an Akka service.

## <a href="about:blank#_akka_components"></a> Akka components

Since Akka is an auto-scaling solution, components can be distributed across many nodes within the same service. That’s why calls between Akka components is done via a client rather than through normal method calls, the receiving component instance may be on the same node, but it may also be on a different node.

Requests and responses are always serialized to JSON between the client and the component.

### <a href="about:blank#_component_client"></a> Component Client

The `akka.javasdk.client.ComponentClient` is a utility for making calls between components in a type-safe way. To use the `ComponentClient` you need to inject it into your component via the constructor:

[CounterEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/api/CounterEndpoint.java)
```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/counter")
public class CounterEndpoint {

  private final ComponentClient componentClient;

  public CounterEndpoint(ComponentClient componentClient) { // (1)
    this.componentClient = componentClient;
  }

  @Get("/{counterId}")
  public Integer get(String counterId) {
    return componentClient.forEventSourcedEntity(counterId) // (2)
      .method(CounterEntity::get)
      .invoke(); // (3)
  }

  @Post("/{counterId}/increase/{value}")
  public HttpResponse increase(String counterId, Integer value) {
    componentClient.forEventSourcedEntity(counterId)
      .method(CounterEntity::increase)
      .invoke(value);

    return ok(); // (4)
  }
}
```

| **1** | Accept the `ComponentClient` as a constructor argument, keep it in a field. |
| **2** | Use a specific request builder for the component you want to call |
| **3** | Invoking the method returns the `T` that the component eventually returns. |
| **4** | Adapt the response rather than returning it as is. In this case discarding the response value, and respond OK without a response body. |
The component client can call command handlers on Event Sourced Entities, Key Value Entities, Workflows, Timed Actions, and query methods on Views.

The component client is available for injection only in Service Setup, Agents, Endpoints, Consumers, Timed Actions, and Workflows. For more information, see [dependency injection](setup-and-dependency-injection.html#_dependency_injection).

It’s also possible to make calls without waiting for them to complete by using `ComponentClient.invokeAsync`, which returns a `CompletionStage<T>`. This allows you to trigger multiple calls concurrently, enabling parallel processing.

[CounterEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/api/CounterEndpoint.java)
```java
public record IncreaseAllThese(List<String> counterIds, Integer value) {}
@Post("/increase-multiple")
public HttpResponse increaseMultiple(IncreaseAllThese increaseAllThese) throws Exception {
  var triggeredTasks = increaseAllThese.counterIds().stream().map(counterId ->
      componentClient.forEventSourcedEntity(counterId)
          .method(CounterEntity::increase)
          .invokeAsync(increaseAllThese.value) // (1)
      ).toList();

  for (var task : triggeredTasks) {
    task.toCompletableFuture().get(); // (2)
  }
  return ok(); // (3)
}
```

| **1** | Call `invokeAsync()` and collect each `CompletionStage<T>` |
| **2** | When all tasks has been started, wait for all tasks to complete |
| **3** | When all tasks responses has successfully completed we can respond |

## <a href="about:blank#_akka_services"></a> Akka services

Calling other Akka services in the same project is done by invoking them using an HTTP or a GRPC client depending on what type
of endpoints the service provides.

### <a href="about:blank#_over_http"></a> Over HTTP

The service is identified by the name it has been deployed. Akka takes care of routing requests to the service and keeping the data safe by encrypting the connection and handling authentication for you.

In the follow snippet, we have an endpoint component that calls another service named `"counter"`. It makes use of SDK-provided `akka.javasdk.http.HttpClientProvider` which return HTTP client instances for calling other Akka services.

In our delegating service implementation:

[DelegatingServiceEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/callanotherservice/DelegatingServiceEndpoint.java)
```java
@Acl(allow = @Acl.Matcher(service = "*"))
@HttpEndpoint
public class DelegatingServiceEndpoint {

  private final HttpClient httpClient;

  public DelegatingServiceEndpoint(HttpClientProvider httpClient) { // (1)
    this.httpClient = httpClient.httpClientFor("counter"); // (2)
  }

  // model for the JSON we accept
  record IncreaseRequest(int increaseBy) {}

  // model for the JSON the upstream service responds with
  record Counter(int value) {}

  @Post("/delegate/counter/{counterId}/increase")
  public String addAndReturn(String counterId, IncreaseRequest request) {
    var response = httpClient.POST("/counter/" + counterId + "/increase") // (3)
    .withRequestBody(request)
    .responseBodyAs(Counter.class)
    .invoke(); // (4)

    if (response.status().isSuccess()) { // (5)
      return "New counter vaue: " + response.body().value;
    } else {
      throw new RuntimeException("Counter returned unexpected status: " + response.status());
    }
  }
}
```

| **1** | Accept a `HttpClientProvider` parameter for the constructor |
| **2** | Use it to look up a client for the `counter` service |
| **3** | Use the `HttpClient` to prepare a REST call to the **counter** service endpoint |
| **4** | Invoking the call will return a `StrictResponse<T>` with details about the result as well as the deserialized response body. |
| **5** | Handle the response, which may be successful, or an error |

|  | The HTTP client provider is only available for injection in the following types of components: HTTP Endpoints, gRPC Endpoints, Workflows, Consumers and Timed Actions. |

### <a href="about:blank#_external_http_services"></a> External HTTP services

Calling HTTP services deployed on **different** Akka projects or any other external HTTP server is also done with the `HttpClientProvider`. Instead of a service name, the protocol and full server name is used when calling `httpClientFor`. For example `https://example.com` or `http://example.com`.

[CallExternalServiceEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/callanotherservice/CallExternalServiceEndpoint.java)
```java
package com.example.callanotherservice;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.StrictResponse;

import java.util.List;
import java.util.stream.Collectors;

@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class CallExternalServiceEndpoint {

  private final HttpClient httpClient;

  public record PeopleInSpace(List<Astronaut> people, int number, String message) {}
  public record Astronaut(String craft, String name) {}

  public record AstronautsResponse(List<String> astronautNames) {}

  public CallExternalServiceEndpoint(HttpClientProvider httpClient) { // (1)
    this.httpClient = httpClient.httpClientFor("http://api.open-notify.org"); // (2)
  }

  @Get("/iss-astronauts")
  public AstronautsResponse issAstronauts() {
    StrictResponse<PeopleInSpace> peopleInSpaceResponse =
      httpClient.GET("/astros.json")// (3)
        .responseBodyAs(PeopleInSpace.class) // (4)
        .invoke();

    var astronautNames = peopleInSpaceResponse.body().people.stream()  // (5)
        .filter(astronaut -> astronaut.craft.equals("ISS"))
        .map(astronaut -> astronaut.name)
        .collect(Collectors.toList());
    return new AstronautsResponse(astronautNames); // (6)
  }

}
```

| **1** | Accept a `HttpClientProvider` parameter for the constructor |
| **2** | Look up a `HttpClient` for a service using `http` protocol and server name. |
| **3** | Issue a GET call to the path `/astros.json` on the server |
| **4** | Specify a class to parse the response body into |
| **5** | Once the call completes, handle the response. |
| **6** | Return an adapted result object which will be turned into a JSON response. |

### <a href="about:blank#_over_grpc"></a> Over gRPC

The service is identified by the name it has been deployed. Akka takes care of routing requests to the service and keeping the data safe by encrypting the connection and handling authentication for you.

In this sample we will make an endpoint that does a call to the [gRPC endpoints](grpc-endpoints.html) customer registry service, deployed with the service name `customer-registry`.

The SDK provides `akka.javasdk.grpc.GrpcClientProvider` which provides gRPC client instances for calling other services.

To consume a gRPC service, the service protobuf descriptor must be added in the `src/proto` directory of the project, this
triggers generation of a client interface and Java classes for all the message types used as requests and responses for
methods in the service.

|  | Since the service protobuf descriptors need to be shared between the provider service and the consuming service, one simple option could be to copy the service descriptions to each service that needs them. It is also possible to use a shared library with the protobuf descriptors. |
In our delegating service implementation:

[DelegateCustomerGrpcEndpointImpl.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry-subscriber/src/main/java/customer/api/DelegateCustomerGrpcEndpointImpl.java)
```java
@GrpcEndpoint
public class DelegateCustomerGrpcEndpointImpl implements DelegateCustomerGrpcEndpoint {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private CustomerGrpcEndpointClient customerService;

  public DelegateCustomerGrpcEndpointImpl(GrpcClientProvider clientProvider) { // (1)
    customerService = clientProvider.grpcClientFor(CustomerGrpcEndpointClient.class, "customer-registry"); // (2)
  }

  @Override
  public CreateCustomerResponse createCustomer(CreateCustomerRequest in) {
    log.info("Delegating customer creation to upstream gRPC service: {}", in);
    if (in.getCustomerId().isEmpty())
      throw new GrpcServiceException(Status.INVALID_ARGUMENT.augmentDescription("No id specified"));

    try {
      return customerService
          .createCustomer(in); // (3)

    } catch (Exception ex) {
      throw new RuntimeException("Delegate call to create upstream customer failed", ex);
    }
  }
}
```

| **1** | Accept a `GrpcClientProvider` parameter for the constructor |
| **2** | Use the generated gRPC client interface for the service `CustomerGrpcEndpointClient.class` and the service name `customer-registry` to look up a client. |
| **3** | Use the client to call the other service and return a `CompletionStage<CreateCustomerResponse>` |
Since the called service and the `DelegateCustomerGrpcEndpoint` share request and response protocol, no further transformation
of the request or response is needed here.

For dev mode and in tests, providing a config override in `application.conf` like for external calls is possible, however
when deployed such configuration is ignored.

|  | The gRPC client provider is only available for injection in the following types of components: HTTP Endpoints, gRPC endpoints, Workflows, Consumers and Timed Actions. |

### <a href="about:blank#_external_grpc_services"></a> External gRPC services

Calling gRPC services deployed on **different** Akka projects or any other external gRPC server is also done with the `GrpcClientProvider`. Instead of a service name, the protocol and fully qualified DNS name of the service is used when calling `grpcClientFor`. For example `hellogrpc.example.com`.

[CallExternalGrpcEndpointImpl.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/callanotherservice/CallExternalGrpcEndpointImpl.java)
```java
@GrpcEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class CallExternalGrpcEndpointImpl implements CallExternalGrpcEndpoint {
  private final ExampleGrpcEndpointClient external;

  public CallExternalGrpcEndpointImpl(GrpcClientProvider clientProvider) { // (1)
    external = clientProvider.grpcClientFor(ExampleGrpcEndpointClient.class, "hellogrpc.example.com"); // (2)
  }

  @Override
  public HelloReply callExternalService(HelloRequest in) {
    return external.sayHello(in); // (3)
  }
}
```

| **1** | Accept a `GrpcClientProvider` parameter for the constructor |
| **2** | Use the generated gRPC client interface for the service `ExampleGrpcEndpointClient.class` and the service name `doc-snippets` to look up a client. `ExampleGrpcEndpointClient.class`. |
| **3** | Use the client to call the other service and return a `CompletionStage<HelloReply>` |
Since the called service and the `DelegatingGrpcEndpoint` share request and response protocol, no further transformation
of the request or response is needed here.

The service is expected to accept HTTPS connections and run on the standard HTTPS port // (443). For calling a service on a nonstandard
port, or served unencrypted (not recommended) it is possible to define configuration overrides in `application.conf` (or `application-test.conf` specifically for tests):

[application.conf](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/resources/application.conf)
```json
akka.javasdk.grpc.client."hellogrpc.example.com" {
  # configure external call, to call back to self
  host = "localhost"
  port = 9000
  use-tls = false
}
```

<!-- <footer> -->
<!-- <nav> -->
[Integrations](integrations/index.html) [Message broker integrations](message-brokers.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->