<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Components](components/index.html)
- [gRPC Endpoints](grpc-endpoints.html)

<!-- </nav> -->

# Designing gRPC Endpoints

![Endpoint](../_images/endpoint.png)
An Endpoint is a component that creates an externally accessible API. Endpoints are how you expose your services to the outside world. Two different types of endpoints are available: HTTP and gRPC endpoints. In this page, we will focus on gRPC endpoints.

gRPC was designed to support service evolution and decoupling by enforcing a protocol-first design through `.proto` files. This ensures that service contracts are explicitly defined, providing a clear structure for communication. Protobuf, the underlying serialization format, supports backward and forward compatibility, avoiding tight coupling by making it easier to evolve services without breaking existing clients. Additionally, gRPC’s efficient binary serialization and support for both unary and streaming calls make it a good choice for high-performance, scalable service-to-service communication. For more information on gRPC and Protobuf, see [https://grpc.io](https://grpc.io/) and [the Protobuf 3 guide](https://protobuf.dev/programming-guides/proto3/).

gRPC Endpoint components make it possible to conveniently define APIs accepting and responding in protobuf — the binary, typed protocol used by gRPC which is designed to handle evolution of a service over time.

|  | Our recommendation is to use gRPC Endpoints for cross-service calls (be it with another Akka service or other backend services) and HTTP Endpoints for APIs consumed directly by client-facing / frontend applications — for which the use of gRPC comes at a greater cost. For a deeper dive into the differences between gRPC and HTTP Endpoints, see [Endpoints](../concepts/grpc-vs-http-endpoints.html). |

## <a href="about:blank#_basics"></a> Basics

To define a gRPC Endpoint component, you start by defining a `.proto` file that defines the service and its messages
in `src/main/proto` of the project.

[customer_grpc_endpoint.proto](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/proto/com/example/customer_grpc_endpoint.proto)
```protobuf
syntax = "proto3";

option java_multiple_files = true;
option java_package = "customer.api.proto"; // (1)

package customer.api;

message Address {
  string street = 1;
  string city = 2;
}

message Customer {
  string email = 1;
  string name = 2;
  Address address = 3;
}

message GetCustomerRequest {
  string customer_id = 1;
}

service CustomerGrpcEndpoint {
  rpc GetCustomer (GetCustomerRequest) returns (Customer) {} // (2)
}
```

| **1** | Define the java package in which the generated classes will be placed. |
| **2** | Declare the method along with its input and output types. |

|  | For a reference on how to format your protobuf files, check [protobuf.dev style guide](https://protobuf.dev/programming-guides/style/). |
When compiling the project, a Java interface for the service is generated at `customer.api.proto.CustomerGrpcEndpoint`. Define a class implementing this interface in the `api` package of your project
and annotate the class with `@GrpcEndpoint`:

[CustomerGrpcEndpointImpl.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/CustomerGrpcEndpointImpl.java)
```java
package com.example.api;

import akka.javasdk.annotations.GrpcEndpoint;
import customer.api.proto.Customer;
import customer.api.proto.CustomerGrpcEndpoint;
import customer.api.proto.GetCustomerRequest;

@GrpcEndpoint // (1)
public class CustomerGrpcEndpointImpl implements CustomerGrpcEndpoint {

  @Override
  public Customer getCustomer(GetCustomerRequest in) {
    // dummy implementation with hardcoded values
    var customer = Customer.newBuilder() // (2)
        .setName("Alice")
        .setEmail("alice@email.com")
        .build();
    return customer; // (3)
  }
}
```

| **1** | Mark class as a gRPC endpoint and implementing the generated interface `CustomerGrpcEndpoint`. |
| **2** | Create a new `Customer` protobuf message and set the `name` and `email` fields. |
| **3** | Respond with the `Customer` protobuf message to the client. |

|  | This implementation does not interact with any other components and has an hard-coded response for simplification purposes. Interacting with other components is covered in the next section. |

### <a href="about:blank#_error_responses"></a> Error responses

The gRPC protocol has different status codes to signal that something went wrong with a request, for example `INVALID_ARGUMENT` to signal that the request was malformed.

To signal an error in the response, throw a `GrpcServiceException` as shown in the example below:

[CustomerGrpcEndpointImpl.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/api/CustomerGrpcEndpointImpl.java)
```java
if (in.getCustomerId().isBlank())
      throw new GrpcServiceException(
          Status.INVALID_ARGUMENT.augmentDescription("Customer id must not be empty"));
```
In addition to the special `GrpcServiceException` and `StatusRuntimeException`, exceptions are handled like this:

- `IllegalArgumentException` is turned into a `INVALID_ARGUMENT`
- Any other exception is turned into a `INTERNAL` error.

  - In production the error is logged together with a correlation
id and the response message only includes the correlation id to not leak service internals to an untrusted client.
  - In local development and integration tests the full exception is returned as response body.

## <a href="about:blank#_securing_grpc_endpoints"></a> Securing gRPC endpoints

Akka’s gRPC endpoints can be secured by multiple approaches:

1. [Access Control Lists (ACLs)](access-control.html)
2. [JSON Web Tokens (JWTs)](../reference/jwts.html)
3. [TLS certificates](../operations/tls-certificates.html)

## <a href="about:blank#_interacting_with_other_components"></a> Interacting with other components

Endpoints are commonly used to interact with other components in a service. This is done through
the `akka.javasdk.client.ComponentClient`. If the constructor of the endpoint class has a parameter of this type,
it will be injected by the SDK and can then be available for use when processing requests. Let’s see how this is done:

[CustomerGrpcEndpointImpl.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/api/CustomerGrpcEndpointImpl.java)
```java
@GrpcEndpoint // (1)
public class CustomerGrpcEndpointImpl implements CustomerGrpcEndpoint {

  private static final Logger log = LoggerFactory.getLogger(CustomerGrpcEndpointImpl.class);

  private final ComponentClient componentClient;

  public CustomerGrpcEndpointImpl(ComponentClient componentClient) { // (2)
    this.componentClient = componentClient;
  }

  @Override
  public Customer getCustomer(GetCustomerRequest in) {
    if (in.getCustomerId().isBlank())
      throw new GrpcServiceException(
          Status.INVALID_ARGUMENT.augmentDescription("Customer id must not be empty"));

    try {
      var customer = componentClient.forEventSourcedEntity(in.getCustomerId()) // (3)
          .method(CustomerEntity::getCustomer)
          .invoke();

      return domainToApi(customer); // (4)
    } catch (Exception ex) {
      if (ex.getMessage().contains("No customer found for id")) throw new GrpcServiceException(Status.NOT_FOUND);
      else throw new RuntimeException(ex);
    }
  }

  private Customer domainToApi(customer.domain.Customer domainCustomer) {
    return Customer.newBuilder()
        .setName(domainCustomer.name())
        .setEmail(domainCustomer.email())
        .setAddress(domainToApi(domainCustomer.address()))
        .build();
  }

  private Address domainToApi(customer.domain.Address domainAddress) {
    if (domainAddress == null) return null;
    else {
      return Address.newBuilder()
          .setCity(domainAddress.city())
          .setStreet(domainAddress.street())
          .build();
    }
  }
```

| **1** | Mark class as a gRPC endpoint and implement the generated interface `CustomerGrpcEndpoint`. |
| **2** | Accept the `ComponentClient` and keep it in a field. |
| **3** | Use the component client to interact with an Event Sourced Entity that holds the customers, identified by `customerId`. |
| **4** | Transform the result from the component client to the external response. |
For more details see [Component and service calls](component-and-service-calls.html).

## <a href="about:blank#_streaming"></a> Streaming

gRPC supports streaming requests and responses, with which either the client or the server (or both) can send multiple messages. In this section, we will show how to stream the results of a request but the remaining combinations are similar.

To stream the results of a request, mark the return type of the method as `stream` in the `.proto` file:

[customer_grpc_endpoint.proto](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/proto/customer/api/customer_grpc_endpoint.proto)
```protobuf
message CustomerSummary {
  string email = 1;
  string name = 2;
}


service CustomerGrpcEndpoint {
  // ...
  rpc CustomerByEmailStream (CustomerByEmailRequest) returns (stream CustomerSummary) {}
}
```
Then, the method in the endpoint interface will need to construct and return a `Stream`:

[CustomerGrpcEndpointImpl.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/main/java/customer/api/CustomerGrpcEndpointImpl.java)
```java
@Override
  public Source<CustomerSummary, NotUsed> customerByEmailStream(CustomerByEmailRequest in) {
    // Shows of streaming consumption of a view, transforming
    // each element and passing along to a streamed response
    var customerSummarySource = componentClient.forView()
        .stream(CustomersByEmailView::getCustomersStream)
        .source(in.getEmail());

    return customerSummarySource.map(c ->
      CustomerSummary.newBuilder()
          .setName(c.name())
          .setEmail(c.email())
          .build());
  }
```

|  | The above example depends on existing a View component that also returns a `Stream` of `Customer` messages. See [Streaming the result](views.html#_streaming_the_result) for more details. |

## <a href="about:blank#_testing_the_endpoint"></a> Testing the Endpoint

To exercise a gRPC endpoint, the testkit contains methods to get a gRPC client for calling the methods of the endpoint:

[CustomerGrpcIntegrationTest.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry/src/test/java/customer/api/CustomerGrpcIntegrationTest.java)
```java
public class CustomerGrpcIntegrationTest extends TestKitSupport {

  @Test
  public void createCustomerCart() {

    var client = getGrpcEndpointClient(CustomerGrpcEndpointClient.class);

    var customerRequest = customer.api.proto.CreateCustomerRequest.newBuilder()
        .setCustomerId("customer-abc")
        .setCustomer(customer.api.proto.Customer.newBuilder()
            .setEmail("abc@email.com")
            .setName("John Doe")
            .build())
        .build();

    client.createCustomer(customerRequest);

    var getCustomer =
        client.getCustomer(customer.api.proto.GetCustomerRequest.newBuilder()
            .setCustomerId("customer-abc")
            .build());
    Assertions.assertEquals("John Doe", getCustomer.getName());
  }
}
```
By default, if ACLs are defined, the testkit client is authenticated as if it was the service itself calling the endpoint,
but there is also an overload to `getGrpcEndpointClient` that takes a `Principal` parameter for specifying what principal
client should seem like from the endpoints point of view, for example to simulate another service `Principal.localService("other-service")` or a request coming from the public internet `Principal.INTERNET`.

## <a href="about:blank#_schema_evolution"></a> Schema evolution

Protobuf is designed to allow evolving the messages while keeping wire compatibility.

Following are the most common aspects of a message protocol you would want to change. For more details on what other changes can be compatible, see the [Protobuf documentation](https://protobuf.dev/programming-guides/proto3/#updating).

### <a href="about:blank#_grpc_services_and_their_methods"></a> gRPC services and their methods

If a gRPC service package, service name or RPC method name is changed, or whether an RPC method
is changed to accept streaming or return streaming data, clients that only know the old service description will no longer
be able to call the new service without recompiling and changing the consuming code.

### <a href="about:blank#_renaming_messages_or_their_fields"></a> Renaming messages or their fields

Fields, message names, and protobuf package names are not encoded in the wire protocol, instead the *tag number* -
the number assigned to each field is used. This means the names can be changed as long as the message structure is
intact. A client consuming messages with an old version of the protobuf messages will still be able to communicate
with a service that has name changes.

Changing names will however not be *source compatible*, since the generated Java class and field names will change
along with the protobuf name change, once a protocol file with name changes is introduced in a service it will need
updates to the code wherever it is using the old names.

### <a href="about:blank#_adding_fields"></a> Adding fields

To allow adding new fields without breaking the wire protocol, all fields are optional in protobuf, for primitive fields
this means that they will have a default value when not present over the wire. For nested messages a missing value leads
to a Java class instance with default for all values, but it is also possible to observe that the value is missing through
generated `has[FieldName]` methods for each field that is a message.

When deserializing, if there are any unknown fields in the message, the message will deserialize without problems but
the unknown fields can be inspected through `getUnknownFields()`.

### <a href="about:blank#_removing_fields"></a> Removing fields

The most important aspect to understand about evolution of protobuf messages is that the *tag number* - the number for
each field, must never be re-used. A field can just be dropped, but it is good practice to mark the original field number
as `reserved` to not accidentally re-use it in the future. It is possible to mark both the tag number and the old used
field name as reserved:

```protobuf
message Example {
  string first_still_used = 1;
  // used to be here:
  // string old_field = 2;
  string another_used = 3;
  reserved 2;
  reserved "old_field";
}
```
It is also possible to mark a field as deprecated, which leads to the field still being in the protocol but adds a `@Deprecated` annotation to the generated code to advise consumers not to use the field:

```protobuf
message Example {
  string first_still_used = 1;
  int32 old_field = 2 [deprecated = true];
  string another_used = 3;
}
```
Dropping fields will not be *source compatible*, since the generated Java class and set of fields will change
along with the protobuf message change, once a protocol file with name changes is introduced in a service it will need
updates to the code wherever it is accessing the old field.

## <a href="about:blank#_see_also"></a> See also

- [Access Control Lists (ACLs)](access-control.html)
- [JSON Web Tokens (JWTs)](../reference/jwts.html)
- [TLS certificates](../operations/tls-certificates.html)

<!-- <footer> -->
<!-- <nav> -->
[HTTP Endpoints](http-endpoints.html) [MCP Endpoints](mcp-endpoints.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->