<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Components](components/index.html)
- [HTTP Endpoints](http-endpoints.html)

<!-- </nav> -->

# Designing HTTP endpoints

![Endpoint](../_images/endpoint.png)
An Endpoint is a component that creates an externally accessible API. Endpoints are how you expose your services to the outside world. Two different types of endpoints are available: HTTP endpoints and gRPC endpoints. In this page, we will focus on HTTP endpoints.

HTTP Endpoint components make it possible to conveniently define such APIs accepting and responding in JSON,
or dropping down to lower level APIs for ultimate flexibility in what types of data is accepted and returned.

## <a href="about:blank#_basics"></a> Basics

To define an HTTP Endpoint component, create a public class and annotate it with `@HttpEndpoint("/path-prefix")`.

Each public method on the endpoint that is annotated with method `@Get`, `@Post`, `@Put`, `@Patch` or `@Delete` will be handling incoming requests matching the `/path-prefix` and the method-specific path used as value defined
for the path annotation.

The most basic example:

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

@HttpEndpoint("/example") // (1)
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL)) // (2)
public class ExampleEndpoint extends AbstractHttpEndpoint { // (1)



  @Get("/hello") // (3)
  public String hello() {
    return "Hello World"; // (4)
  }
```

| **1** | Common path prefix for all methods in the same class `/example`. |
| **2** | ACL configuration allowing any client to access the endpoint. |
| **3** | `GET` endpoint path is combined with the prefix and becomes available at `/example/hello` |
| **4** | Return value, is turned into an `200 Ok` response, with content type `text/plain` and the specified string as body. |

|  | Without an ACL annotation no client is allowed to access the endpoint. For more details on how ACLs can be configured, see [Access Control Lists (ACLs)](access-control.html) |

### <a href="about:blank#_path_parameters"></a> Path parameters

The path can also contain one or more parameters, which are extracted and passed to the method:

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
@Get("/hello/{name}") // (1)
  public String hello(String name) { // (2)
    return "Hello " + name;
  }

  @Get("/hello/{name}/{age}") // (3)
  public String hello(String name, int age) { // (4)
    return "Hello " + name + "! You are " + age + " years old";
  }
```

| **1** | Path parameter `name` in expression. |
| **2** | Method parameter named as the one in the expression |
| **3** | When there are multiple parameters |
| **4** | The method must accept all the same names in the same order as in the path expression. |
Path parameter can be of types `String`, `int`, `long`, `boolean`, `float`, `double`, `short` and `char` as well
as their `java.lang` class counterparts.

### <a href="about:blank#_request_body"></a> Request body

To accept an HTTP JSON body, specify a parameter that is a class that [Jackson](https://github.com/FasterXML/jackson?tab=readme-ov-file#what-is-jackson) can deserialize:

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
public record GreetingRequest(String name, int age) {} // (1)

  @Post("/hello")
  public String hello(GreetingRequest greetingRequest) { // (2)
    return "Hello " + greetingRequest.name + "! " +
        "You are " + greetingRequest.age + " years old";
  }

  @Post("/hello/{number}") // (3)
  public String hello(int number, GreetingRequest greetingRequest) { // (4)
    return number + " Hello " + greetingRequest.name + "! " +
        "You are " + greetingRequest.age + " years old";
  }
```

| **1** | A class that Jackson can serialize and deserialize to JSON |
| **2** | A parameter of the request body type |
| **3** | When combining request body with path variables |
| **4** | The body must come last in the parameter list |
Additionally, the request body parameter can be of the following types:

- `String` for any request with a text content type, the body decoded into a string
- `java.util.List<T>` where `T` is a type Jackson can deserialize, accepts a JSON array.
- `akka.http.javadsl.model.HttpEntity.Strict` for the entire request body as bytes together with the content type for
arbitrary payload handling.
- `akka.http.javadsl.model.HttpRequest` for a low level, streaming representation of the entire request
including headers. See [Low level requests](about:blank#_low_level_requests) below for more details

### <a href="about:blank#_request_headers"></a> Request headers

Accessing request headers is done through the [RequestContext](_attachments/api/akka/javasdk/http/RequestContext.html) methods `requestHeader(String headerName)` and `allRequestHeaders()`.

By letting the endpoint extend [AbstractHttpEndpoint](_attachments/api/akka/javasdk/http/AbstractHttpEndpoint.html) request context is available through the method `requestContext()`.

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
public class ExampleEndpoint extends AbstractHttpEndpoint { // (1)

  @Get("/hello-request-header-from-context")
  public String requestHeaderFromContext() {
    var name = requestContext().requestHeader("X-my-special-header") // (2)
        .map(HttpHeader::value)
        .orElseThrow(() -> new IllegalArgumentException("Request is missing my special header"));

    return "Hello " + name + "!";
  }
}
```

| **1** | Extend `AbstractHttpEndpoint` class. |
| **2** | `requestHeader(headerName)` returns an `Optional` which is empty if the header was not present. |

### <a href="about:blank#_query_parameters"></a> Query parameters

Accessing query parameter is done through the `requestContext()`, inherited from [AbstractHttpEndpoint](_attachments/api/akka/javasdk/http/AbstractHttpEndpoint.html).

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
@Get("/hello-query-params-from-context")
  public String queryParamsFromContext() {
    var name = requestContext().queryParams().getString("name").orElse(""); // (1)
    return "Hello " + name + "!";
  }
```

| **1** | `queryParams().get("name")` returns an `Optional` which is empty if the query parameter is not present. |

### <a href="about:blank#_response_body"></a> Response body

To return response with JSON, the return value can be a class that Jackson can serialize:

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
public record MyResponse(String name, int age) {}

  @Get("/hello-response/{name}/{age}")
  public MyResponse helloJson(String name, int age) {
    return new MyResponse(name, age); // (1)
  }
```

| **1** | Returning an object that Jackson can serialize into JSON |
In addition to an object that can be turned to JSON, a request handler can return the following:

- `null` or `void` to return an empty body.
- `String` to return a UTF-8 encoded `text/plain` HTTP response.
- A record or other class that can be serialized to JSON.
- `CompletionStage<T>` to respond based on an asynchronous result.

  - When the completion stage is completed with a `T` it is
turned into a response.
  - If it is instead failed, the failure leads to an error response according to
the error handling explained in [error responses](about:blank#_error_responses).
- `akka.http.javadsl.model.HttpResponse` for complete control over the response, see [Low level responses](about:blank#_low_level_responses) below

### <a href="about:blank#_error_responses"></a> Error responses

The HTTP protocol has several status codes to signal that something went wrong with a request, for
example HTTP `400 Bad request` to signal that the incoming request was not valid.

Responding with an error can be done by throwing one of the exceptions available through static factory methods in `akka.javasdk.http.HttpException`.

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
@Get("/hello-code/{name}/{age}")
  public String helloWithValidation(String name, int age) {
    if (age > 130)
      throw HttpException.badRequest("It is unlikely that you are " + age + " years old");  // (1)
    else
      return " Hello " + name + "!";  // (2)
  }
```

| **1** | Throw one of the exceptions created through factory methods provided by `HttpException` to respond with a HTTP error |
| **2** | Return non-error |
In addition to the special `HttpException`s, exceptions are handled like this:

- `IllegalArgumentException` is turned into a `400 Bad request`
- Any other exception is turned into a `500 Internal server error`.

  - In production the error is logged together with a correlation
id and the response message only includes the correlation id to not leak service internals to an untrusted client.
  - In local development and integration tests the full exception is returned as response body.

## <a href="about:blank#_securing_http_endpoints"></a> Securing HTTP endpoints

Akka’s HTTP endpoints can be secured by multiple approaches:

1. [Access Control Lists (ACLs)](access-control.html)
2. [JSON Web Tokens (JWTs)](../reference/jwts.html)
3. [TLS certificates](../operations/tls-certificates.html)
4. [HTTP Basic authentication](../operations/services/invoke-service.html#_http_basic_authentication)

## <a href="about:blank#_interacting_with_other_components"></a> Interacting with other components

The most common use case for endpoints is to interact with other components in a service. This is done through
the `akka.javasdk.client.ComponentClient`. If the constructor of the endpoint class has a parameter of this type,
it will be injected by the SDK.

[ShoppingCartEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/shopping-cart-quickstart/src/main/java/shoppingcart/api/ShoppingCartEndpoint.java)
```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/carts") // (1)
public class ShoppingCartEndpoint {

  private final ComponentClient componentClient;

  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEndpoint.class);

  public ShoppingCartEndpoint(ComponentClient componentClient) { // (2)
    this.componentClient = componentClient;
  }


  @Get("/{cartId}") // (3)
  public ShoppingCart get(String cartId) {
    logger.info("Get cart id={}", cartId);
    return componentClient.forEventSourcedEntity(cartId) // (4)
        .method(ShoppingCartEntity::getCart)
        .invoke(); // (5)
  }


  @Put("/{cartId}/item") // (6)
  public HttpResponse addItem(String cartId, ShoppingCart.LineItem item) {
    logger.info("Adding item to cart id={} item={}", cartId, item);
    componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::addItem)
      .invoke(item);
    return HttpResponses.ok(); // (7)
  }
```

| **1** | Common path prefix for all methods in the same class `/carts`. |
| **2** | Accept the `ComponentClient` and keep it in a field. |
| **3** | GET endpoint path is combined with a path parameter name, e.g. `/carts/123`. |
| **4** | The component client can be used to interact with other components. |
| **5** | Result of a request to a component is the Effect’s reply type. |
| **6** | Use path parameter `{cartId}` in combination with request body `ShoppingCart.LineItem`. |
| **7** | Result of request mapped to a more suitable response, in this case, `200 Ok` with an empty body. |
For more details see [Component and service calls](component-and-service-calls.html)

## <a href="about:blank#_interacting_with_other_http_services"></a> Interacting with other HTTP services

It is also possible to interact with other services over HTTP. This is done through the `akka.javasdk.http.HttpClientProvider`.

When the other service is also an Akka service deployed in the same project, it can be looked up via the deployed name
of the service:

[CustomerRegistryEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-customer-registry-subscriber/src/main/java/customer/api/CustomerRegistryEndpoint.java)
```java
@HttpEndpoint("/customer")
public class CustomerRegistryEndpoint {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final HttpClient httpClient;
  private final ComponentClient componentClient;

  public record Address(String street, String city) { }

  public record CreateCustomerRequest(String email, String name, Address address) { }


  public CustomerRegistryEndpoint(HttpClientProvider webClientProvider, // (1)
                                  ComponentClient componentClient) {
    this.httpClient = webClientProvider.httpClientFor("customer-registry"); // (2)
    this.componentClient = componentClient;
  }

  @Post("/{id}")
  public HttpResponse create(String id, CreateCustomerRequest createRequest) {
    log.info("Delegating customer creation to upstream service: {}", createRequest);
    if (id == null || id.isBlank())
      throw HttpException.badRequest("No id specified");

    // make call to customer-registry service
    var response = httpClient.POST("/customer/" + id) // (3)
        .withRequestBody(createRequest)
        .invoke();

    if (response.httpResponse().status() == StatusCodes.CREATED) {
      return HttpResponses.created(); // (4)
    } else {
      throw new RuntimeException("Delegate call to create upstream customer failed, response status: " + response.httpResponse().status());
    }
  }
```

| **1** | Accept the `HttpClientProvider` |
| **2** | Use it to create a client for the service `customer-registry` |
| **3** | Issue an HTTP POST request to the service |
| **4** | Turn the response it into our own response |

|  | If you’re looking to test this locally, you will likely need to run the 2 services with different ports. For more details, consult [Running multiple services](running-locally.html#multiple_services). |
It is also possible to interact with arbitrary non-Akka services using the `HttpClientProvider`, for such use,
pass a string with `https://example.com` or `http://example.com` instead of a service name.

For more details see [Component and service calls](component-and-service-calls.html)

## <a href="about:blank#_advanced_http_requests_and_responses"></a> Advanced HTTP requests and responses

For more control over the request and responses it is also possible to use the more
low-level Akka HTTP model APIs.

### <a href="about:blank#_low_level_responses"></a> Low level responses

Returning `akka.http.javadsl.model.HttpResponse` makes it possible to do more flexible and advanced responses.

For example, it allows returning custom headers, custom response body encodings and even streaming responses.

As a convenience `akka.javasdk.http.HttpResponses` provides factories for common response scenarios without
having to reach for the Akka HTTP model APIs directly:

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
record HelloResponse(String greeting) {}

  @Get("/hello-low-level-response/{name}/{age}")
  public HttpResponse lowLevelResponseHello(String name, int age) { // (1)
    if (age > 130)
      return HttpResponses.badRequest("It is unlikely that you are " + age + " years old");  // (2)
    else
      return HttpResponses.ok(new HelloResponse("Hello " + name + "!"));  // (3)
  }
```

| **1** | Declare the return type as `akka.http.javadsl.model.HttpResponse` |
| **2** | Return a bad request response |
| **3** | Return an ok response, you can still use arbitrary objects and get them serialized to JSON |
`akka.javasdk.http.HttpResponses` provides convenient factories for common response message types without
having to reach for the Akka HTTP model APIs directly:

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
record HelloResponse(String greeting) {}

  @Get("/hello-low-level-response/{name}/{age}")
  public HttpResponse lowLevelResponseHello(String name, int age) { // (1)
    if (age > 130)
      return HttpResponses.badRequest("It is unlikely that you are " + age + " years old");  // (2)
    else
      return HttpResponses.ok(new HelloResponse("Hello " + name + "!"));  // (3)
  }
```

| **1** | Declare the return type as `akka.http.javadsl.model.HttpResponse` |
| **2** | Return a bad request response |
| **3** | Return an ok response |
Dropping all the way down to the Akka HTTP API:

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
@Get("/hello-lower-level-response/{name}/{age}")
  public HttpResponse lowerLevelResponseHello(String name, int age) {
    if (age > 130)
      return HttpResponse.create()
          .withStatus(StatusCodes.BAD_REQUEST)
          .withEntity("It is unlikely that you are " + age + " years old");
    else {
        var jsonBytes = JsonSupport.encodeToAkkaByteString(new HelloResponse("Hello " + name + "!")); // (1)
        return HttpResponse.create() // (2)
            .withEntity(ContentTypes.APPLICATION_JSON, jsonBytes); // (3)
    }
  }
```

| **1** | At this level there is no convenience, the response object must manually be rendered into JSON bytes |
| **2** | The response returned by `HttpResponse.create` is `200 Ok` |
| **3** | Pass the response body bytes and the `ContentType` to describe what they contain |

### <a href="about:blank#_low_level_requests"></a> Low level requests

Accepting `HttpEntity.Strict` will collect all request entity bytes into memory for processing (up to 8Mb),
for example to handle uploads of a custom media type:

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
private final static ContentType IMAGE_JPEG = ContentTypes.create(MediaTypes.IMAGE_JPEG);
  @Post("/post-image/{name}")
  public String lowLevelRequestHello(String name, HttpEntity.Strict strictRequestBody) {
    if (!strictRequestBody.getContentType().equals(IMAGE_JPEG)) // (1)
      throw HttpException.badRequest("This service only accepts " + IMAGE_JPEG);
    else {
      return "Got " + strictRequestBody.getData().size() + " bytes for image name " + name;  // (2)
    }
  }
```

| **1** | `HttpEntity.Strict` gives access to the request body content type |
| **2** | as well as the actual bytes, in a `akka.util.ByteString` |
Accepting `akka.http.javadsl.model.HttpRequest` makes it possible to do more flexible and advanced request handling
but at the cost of quite a bit more complex request handling.

This way of handling requests should only be used for advanced use cases when there is no other option.

In such a method it is paramount that the streaming request body is always handled, for example by discarding it
or collecting it all into memory, if not it will stall the incoming HTTP connection.

Handling the streaming request will require a `akka.stream.Materializer`, to get access to a materializer, define a
constructor parameter of this type to have it injected by the SDK.

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
public class ExampleEndpoint extends AbstractHttpEndpoint { // (1)


  private final Materializer materializer;

  public ExampleEndpoint(Materializer materializer) { // (1)
    this.materializer = materializer;
  }

  @Get("/hello-request-header/{name}")
  public CompletionStage<String> lowerLevelRequestHello(String name, HttpRequest request) {
    if (request.getHeader("X-my-special-header").isEmpty()) {
      return request.discardEntityBytes(materializer).completionStage().thenApply(__ -> { // (2)
        throw HttpException.forbidden("Missing the special header");
      });
    } else {
      return request.entity().toStrict(1000, materializer).thenApply(strictRequestBody ->  // (3)
        " Hello " + name + "! " +
            "We got your " + strictRequestBody.getData().size() + " bytes " +
            "of type " + strictRequestBody.getContentType()
      );
    }
  }
```

| **1** | Accept the materializer and keep it in a field |
| **2** | Make sure to discard the request body when failing |
| **3** | Or collect the bytes into memory |

### <a href="about:blank#_serving_static_content"></a> Serving static content

Static resources such as HTML, CSS files can be packaged together with the service. This is done
by placing the resource files in `src/main/resources/static-resources` and returning them from an endpoint
method using [HttpResponses.staticResource](_attachments/api/akka/javasdk/http/HttpResponses.html#staticResource).

This can be done for a single filename:

[StaticResourcesEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/StaticResourcesEndpoint.java)
```java
@Get("/") // (1)
  public HttpResponse index() {
    return HttpResponses.staticResource("index.html"); // (2)
  }

  @Get("/favicon.ico") // (3)
  public HttpResponse favicon() {
    return HttpResponses.staticResource("favicon.ico"); // (4)

  }
```

| **1** | The specific path `/` |
| **2** | Load a specific file placed in `src/main/resources/static-resources/index.html` |
| **3** | Another specific path `/favicon.ico` |
| **4** | The specific resource to serve |
It is also possible to map an entire path subtree using `**` as a wildcard at the end of the path:

[StaticResourcesEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/StaticResourcesEndpoint.java)
```java
@Get("/static/**") // (1)
  public HttpResponse webPageResources(HttpRequest request) { // (2)
    return HttpResponses.staticResource(request, "/static/"); // (3)
  }
```

| **1** | Endpoint method for any path under `/static/` |
| **2** | Accept `akka.http.javadsl.model.HttpRequest` for further inspection of the actual path. |
| **3** | Load any available file under `static-resources` after first removing `/static` from the request path. The request path `/static/images/example.png` is resolved to the file `src/main/resources/static-resources/images/example.png` from the project. |

|  | This is convenient for service documentation or small self-contained services with web user interface but is not intended
 for production, where coupling of the service lifecycle with the user interface would mean that a new service version would need to be deployed for any changes in the user interface. |

### <a href="about:blank#sse"></a> Streaming responses with server-sent events

[Server-sent events (SSE)](https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events) is a way to push a stream of elements through a single HTTP response
that the client can see one by one rather than have to wait for the entire response to complete.

Any Akka stream `Source` of elements where the elements can be serialized to JSON using Jackson can
be turned into an SSE endpoint method. If the stream is idle, a heartbeat is emitted every 5 seconds
to make sure the response stream is kept alive through proxies and firewalls.

[ExampleEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleEndpoint.java)
```java
@Get("/current-time")
  public HttpResponse streamCurrentTime() {
    Source<Long, Cancellable> timeSource =
        Source.tick(Duration.ZERO, Duration.ofSeconds// (5), "tick") // (1)
            .map(__ -> System.currentTimeMillis()); // (2)

    return HttpResponses.serverSentEvents(timeSource); // (3)
  }
```

| **1** | `Source.tick` emits the element `"tick"` immediately (after `Duration.ZERO`) and then every 5 seconds |
| **2** | Every time a tick is seen, we turn it into a system clock timestamp |
| **3** | Passing the `Source` to `serverSentEvents` returns a `HttpResponse` for the endpoint method. |
A more realistic use case would be to stream the changes from a view, using the [stream view updates view
feature](views.html#_streaming_view_updates).

[CustomerEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/api/CustomerEndpoint.java)
```java
@Get("/by-city-sse/{cityName}")
  public HttpResponse continousByCityNameServerSentEvents(String cityName) {
    // view will keep stream going, toggled with streamUpdates = true on the query
    Source<Customer, NotUsed> customerSummarySource = componentClient.forView() // (1)
        .stream(CustomersByCity::continuousCustomersInCity)
        .source(cityName);

    return HttpResponses.serverSentEvents(customerSummarySource); // (2)
  }
```

| **1** | The view is annotated with `@Query(value = [a query], streamUpdates = true)` to keep polling the database after the initial result is returned and return updates matching the query filter |
| **2** | The stream of view entries and then updates are turned into an SSE response. |
Another realistic example is to periodically poll an entity for its state,
but only emit an element over SSE when the state changes:

[CustomerEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/key-value-customer-registry/src/main/java/customer/api/CustomerEndpoint.java)
```java
private record CustomerStreamState(Optional<Customer> customer, boolean isSame) {}

  @Get("/stream-customer-changes/{customerId}")
  public HttpResponse streamCustomerChanges(String customerId) {
    Source<Customer, Cancellable> stateEvery5Seconds =
        // stream of ticks, one immediately, then one every five seconds
        Source.tick(Duration.ZERO, Duration.ofSeconds// (5), "tick") // (1)
          // for each tick, request the entity state
          .mapAsync(1, __ ->
            // Note: not safe to turn this into `.invoke()` in a stream `.map()`
            componentClient.forKeyValueEntity(customerId)
                .method(CustomerEntity::getCustomer)
                .invokeAsync().handle((Customer customer, Throwable error) -> {
                  if (error == null) {
                    return Optional.of(customer);
                  } else if (error instanceof IllegalArgumentException) {
                    // calling getCustomer throws IllegalArgument if the customer does not exist
                    // we want the stream to continue polling in that case, so turn it into an empty optional
                    return Optional.<Customer>empty();
                  } else {
                    throw new RuntimeException("Unexpected error polling customer state", error);
                  }
                })
          )
          // then filter out the empty optionals and return the actual customer states for nonempty
          // so that the stream contains only Customer elements
          .filter(Optional::isPresent).map(Optional::get);

    // deduplicate, so that we don't emit if the state did not change from last time
    Source<Customer, Cancellable> streamOfChanges = // (2)
        stateEvery5Seconds.scan(new CustomerStreamState(Optional.empty(), true),
          (state, newCustomer) ->
            new CustomerStreamState(Optional.of(newCustomer), state.customer.isPresent() && state.customer.get().equals(newCustomer))
        ).filterNot(state -> state.isSame || state.customer.isEmpty())
        .map(state -> state.customer.get());

    // now turn each changed internal state representation into public API representation,
    // just like get endpoint above
    Source<ApiCustomer, Cancellable> streamOfChangesAsApiType = // (3)
        streamOfChanges.map(customer -> toApiCustomer(customerId, customer));

    // turn into server sent event response
    return HttpResponses.serverSentEvents(streamOfChangesAsApiType); // (4)
  }
```

| **1** | Right away, and then every 5 seconds, use the `ComponentClient` to call `CustomerEntity#getCustomer` to get the current state. |
| **2** | Use `scan` to filter out updates where the state did not change |
| **3** | Transform the internal customer domain type to a public API representation |
| **4** | Turn the stream to a SSE response |
This uses more advanced Akka stream operators, you can find more details of those in the [Akka libraries documentation](https://doc.akka.io/libraries/akka-core/current/stream/operators/index.html).

## <a href="about:blank#_see_also"></a> See also

- [Access Control Lists (ACLs)](access-control.html)
- [JSON Web Tokens (JWTs)](../reference/jwts.html)
- [TLS certificates](../operations/tls-certificates.html)
- [HTTP Basic authentication](../operations/services/invoke-service.html#_http_basic_authentication)

<!-- <footer> -->
<!-- <nav> -->
[Key Value Entities](key-value-entities.html) [gRPC Endpoints](grpc-endpoints.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->