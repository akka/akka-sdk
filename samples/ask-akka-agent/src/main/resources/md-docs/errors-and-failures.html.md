<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Setup and configuration](setup-and-configuration/index.html)
- [Errors and failures](errors-and-failures.html)

<!-- </nav> -->

# Errors and failures

The Akka SDK provides several mechanisms dealing with validation or when something is going wrong.

## <a href="about:blank#_errors"></a> Errors

The first line of the defense is the validation of the incoming data on the `Endpoint` level. Already described is details in the [request and response](http-endpoints.html#_advanced_http_requests_and_responses) section. This is a basic request validation, which doesn’t require domain state. It’s better to handle it as soon as possible, since it will reduce the load on the system. The logic can reject the request before it reaches the entity.

The next phase is domain validation error. An incoming command doesn’t fulfil the requirements or the current state doesn’t allow the command to be handled. Such errors can be signalled back to the client as an error effect using the `effects().error(message)` function.

[CounterEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/application/CounterEntity.java)
```java
public Effect<Integer> increaseWithError(Integer value) {
  if (currentState() + value > 10000) {
    return effects().error("Increasing the counter above 10000 is blocked"); // (1)
  }
  return effects()
    .persist(new ValueIncreased(value, currentState() + value))
    .thenReply(identity());
}
```

| **1** | Return an error effect with a message if the validation fails. |
The `effects().error` is later transformed into a `CommandException`. The default behavior is to return an HTTP 400 error with the error message as a response body.

[CounterEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/api/CounterEndpoint.java)
```java
@Post("/{counterId}/increase-with-error/{value}")
public Integer increaseWithError(String counterId, Integer value) {
  return componentClient
    .forEventSourcedEntity(counterId)
    .method(CounterEntity::increaseWithError)
    .invoke(value); // (1)
}
```

| **1** | Calling component method without additional exception handling. |
Calling such endpoint with an invalid request will return:

HTTP/1.1 400 Bad Request
Access-Control-Allow-Origin: *
Server: akka-http/10.6.3
Date: Wed, 25 Sep 2024 10:44:22 GMT
Content-Type: text/plain; charset=UTF-8
Content-Length: 28

Increasing counter above 10000 is blocked For more fine-tuned control over the error handling it’s possible to catch the `CommandException` and transform it into a proper HTTP error.

[CounterEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/api/CounterEndpoint.java)
```java
@Post("/{counterId}/increase-with-error-handling/{value}")
public HttpResponse increaseWithErrorHandling(String counterId, Integer value) {
  try {
    var result = componentClient
      .forEventSourcedEntity(counterId)
      .method(CounterEntity::increaseWithError)
      .invoke(value);
    return ok(result);
  } catch (CommandException e) { // (1)
    return badRequest("rejected: " + value);
  }
}
```

| **1** | Catching the `CommandException` and transforming it into 400 Bad Request error. |
Remember that the [called component](component-and-service-calls.html#_akka_components) can be on a different node. Only the `CommandException` and its subtypes are serialized and sent over the network. The [Jackson](serialization.html) serialization is configured to ignore fields like stack trace or cause from the Java `java.lang.Throwable` class. Other exceptions are not serializable and will be transformed into a generic HTTP 500 error.

Using the `effects().error(commandException)` method or simply throwing a `CommandException` will have the same effect. It’s possible to have a more dedicated exceptions that will be used to signal different situations. For example, you can create a `CounterLimitExceededException` that extends `CommandException` and use it in the command handler.

[CounterEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/application/CounterEntity.java)
```java
public static class CounterLimitExceededException extends CommandException { // (1)

  private final Integer value;

  public CounterLimitExceededException(Integer value) {
    super("Increasing the counter above 10000 is blocked");
    this.value = value;
  }

  public Integer getValue() {
    return value;
  }
}

public Effect<Integer> increaseWithException(Integer value) {
  if (currentState() + value > 10000) {
    throw new CounterLimitExceededException(value); // (2)
  }
  return effects()
    .persist(new ValueIncreased(value, currentState() + value))
    .thenReply(identity());
}
```

| **1** | Define a custom exception that extends `CommandException`. Inner classes must be static to be serializable. |
| **2** | Throw the `CounterLimitExceededException`. |
This way the client can match them by the type instead of the message.

[CounterEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/event-sourced-counter-brokers/src/main/java/counter/api/CounterEndpoint.java)
```java
@Post("/{counterId}/increase-with-exception/{value}")
public HttpResponse increaseWithException(String counterId, Integer value) {
  try {
    var result = componentClient
      .forEventSourcedEntity(counterId)
      .method(CounterEntity::increaseWithException)
      .invoke(value);
    return ok(result);
  } catch (CounterLimitExceededException e) { // (1)
    return badRequest("rejected: " + e.getValue());
  }
}
```

| **1** | Catching the `CounterLimitExceededException` and transforming it into 400 Bad Request error. |
Custom exceptions are not the only option to deal with errors in a more structured way. Another approach would be to encode them as a part of reply protocol. Make sure that you are familiar with the Jackson serialization library and how to use it with sealed interfaces and generic types, see [@JsonSubTypes](https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-annotations/2.19.1/com/fasterxml/jackson/annotation/JsonSubTypes.html) for details.

## <a href="about:blank#_failures"></a> Failures

All unexpected exception (that doesn’t extend `CommandException`) thrown by the user code are transformed into an HTTP 500 error. When running the service locally in dev mode, a stack trace will be a part of the HTTP response. In production, this information is hidden, to not leak internal details about the service to a client. The client will receive a non-descriptive message with a correlation ID, like below.

```none
Unexpected error [2c74bdfb-3130-464c-8852-cf9c3c2180ad]
```
That same correlation ID `2c74bdfb-3130-464c-8852-cf9c3c2180ad` is included in the log entry for the error as an MDC value with the key `correlationID`. This makes it possible to find the specific error in the logs using `akka logs` or by querying your configured logging backend for the service.

<!-- <footer> -->
<!-- <nav> -->
[Serialization](serialization.html) [Access Control Lists (ACLs)](access-control.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->