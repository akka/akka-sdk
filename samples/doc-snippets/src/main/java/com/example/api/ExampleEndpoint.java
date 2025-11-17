package com.example.api;

import akka.NotUsed;
import akka.actor.Cancellable;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.JsonSupport;
// tag::basic-endpoint[]
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
// end::basic-endpoint[]
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

// tag::basic-endpoint[]

@HttpEndpoint("/example") // <1>
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL)) // <2>
// tag::lower-level-request[]
// tag::header-access[]
public class ExampleEndpoint extends AbstractHttpEndpoint { // <1>

  // end::header-access[]
  // end::basic-endpoint[]
  private final Materializer materializer;

  public ExampleEndpoint(Materializer materializer) { // <1>
    this.materializer = materializer;
  }

  // end::lower-level-request[]
  // tag::basic-endpoint[]
  @Get("/hello") // <3>
  public String hello() {
    return "Hello World"; // <4>
  }

  // end::basic-endpoint[]
  // tag::basic-path-parameters[]
  @Get("/hello/{name}") // <1>
  public String hello(String name) { // <2>
    return "Hello " + name;
  }

  @Get("/hello/{name}/{age}") // <3>
  public String hello(String name, int age) { // <4>
    return "Hello " + name + "! You are " + age + " years old";
  }

  // end::basic-path-parameters[]

  // tag::request-body[]
  public record GreetingRequest(String name, int age) {} // <1>

  // prettier-ignore
  @Post("/hello")
  public String hello(GreetingRequest greetingRequest) { // <2>
    return "Hello " + greetingRequest.name + "! " + 
           "You are " + greetingRequest.age + " years old";
    
  }

  // prettier-ignore
  @Post("/hello/{number}") // <3>
  public String hello(int number, GreetingRequest greetingRequest) { // <4>
    return number + " Hello " + greetingRequest.name + "! " +
           "You are " + greetingRequest.age + " years old";
  }

  // end::request-body[]

  // tag::response-body[]
  public record MyResponse(String name, int age) {}

  @Get("/hello-response/{name}/{age}")
  public MyResponse helloJson(String name, int age) {
    return new MyResponse(name, age); // <1>
  }

  // end::response-body[]

  // tag::error-exceptions[]
  @Get("/hello-code/{name}/{age}")
  public String helloWithValidation(String name, int age) {
    // prettier-ignore
    if (age > 130)
      throw HttpException
        .badRequest("It is unlikely that you are " + age + " years old"); // <1>
    else return " Hello " + name + "!"; // <2>
  }

  // end::error-exceptions[]

  // tag::low-level-response[]
  record HelloResponse(String greeting) {}

  @Get("/hello-low-level-response/{name}/{age}")
  public HttpResponse lowLevelResponseHello(String name, int age) { // <1>
    // prettier-ignore
    if (age > 130)
      return HttpResponses
        .badRequest("It is unlikely that you are " + age + " years old"); // <2>
    else return HttpResponses.ok(new HelloResponse("Hello " + name + "!")); // <3>
  }

  // end::low-level-response[]

  // tag::even-lower-level-response[]
  @Get("/hello-lower-level-response/{name}/{age}")
  public HttpResponse lowerLevelResponseHello(String name, int age) {
    if (age > 130) return HttpResponse.create()
      .withStatus(StatusCodes.BAD_REQUEST)
      .withEntity("It is unlikely that you are " + age + " years old");
    else {
      var jsonBytes = JsonSupport.encodeToAkkaByteString(
        new HelloResponse("Hello " + name + "!")
      ); // <1>
      return HttpResponse.create() // <2>
        .withEntity(ContentTypes.APPLICATION_JSON, jsonBytes); // <3>
    }
  }

  // end::even-lower-level-response[]

  // tag::low-level-request[]
  private static final ContentType IMAGE_JPEG = ContentTypes.create(MediaTypes.IMAGE_JPEG);

  @Post("/post-image/{name}")
  public String lowLevelRequestHello(String name, HttpEntity.Strict strictRequestBody) {
    if (
      !strictRequestBody.getContentType().equals(IMAGE_JPEG)
    ) throw HttpException.badRequest("This service only accepts " + IMAGE_JPEG);
    else { // <1>
      return "Got " + strictRequestBody.getData().size() + " bytes for image name " + name; // <2>
    }
  }

  // end::low-level-request[]

  // tag::lower-level-request[]
  @Get("/hello-request-header/{name}")
  public CompletionStage<String> lowerLevelRequestHello(String name, HttpRequest request) {
    if (request.getHeader("X-my-special-header").isEmpty()) {
      return request
        .discardEntityBytes(materializer)
        .completionStage()
        .thenApply(__ -> { // <2>
          throw HttpException.forbidden("Missing the special header");
        });
    } else {
      return request
        .entity()
        .toStrict(1000, materializer)
        .thenApply(strictRequestBody -> // <3>
          " Hello " +
          name +
          "! " +
          "We got your " +
          strictRequestBody.getData().size() +
          " bytes " +
          "of type " +
          strictRequestBody.getContentType());
    }
  }

  // end::lower-level-request[]

  // tag::header-access[]
  @Get("/hello-request-header-from-context")
  public String requestHeaderFromContext() {
    var name = requestContext()
      .requestHeader("X-my-special-header") // <2>
      .map(HttpHeader::value)
      .orElseThrow(
        () -> new IllegalArgumentException("Request is missing my special header")
      );

    return "Hello " + name + "!";
  }

  // end::header-access[]

  // tag::query-params[]
  @Get("/hello-query-params-from-context")
  public String queryParamsFromContext() {
    var name = requestContext().queryParams().getString("name").orElse(""); // <1>
    return "Hello " + name + "!";
  }

  // end::query-params[]

  // tag::basic-sse[]
  @Get("/current-time")
  public HttpResponse streamCurrentTime() {
    Source<Long, Cancellable> timeSource = Source.tick( // <1>
      Duration.ZERO,
      Duration.ofSeconds(5),
      "tick"
    ).map(__ -> System.currentTimeMillis()); // <2>

    return HttpResponses.serverSentEvents(timeSource); // <3>
  }

  // end::basic-sse[]

  // tag::sse-with-id[]
  record ChatMessage(
    Instant timestamp, // <1>
    String message
  ) {}

  public interface ChatRoom {
    Source<ChatMessage, NotUsed> streamChat(Optional<Instant> startFrom); // <1>
  }

  private final ChatRoom chatRoom = // end::sse-with-id[]
    null;

  // tag::sse-with-id[]

  @Get("/chatroom")
  public HttpResponse resumableStream() {
    Optional<Instant> startFrom = requestContext()
      .lastSeenSseEventId() // <3>
      .map(Instant::parse);
    Source<ChatMessage, NotUsed> chatMessageStream = chatRoom.streamChat(startFrom);

    return HttpResponses.serverSentEvents(
      chatMessageStream,
      chatMessage -> chatMessage.timestamp().toString()
    ); // <2>
  }
  // end::sse-with-id[]

  // tag::header-access[]
}
// end::header-access[]
