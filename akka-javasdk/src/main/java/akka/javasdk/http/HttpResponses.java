/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;

import akka.NotUsed;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.CacheControl;
import akka.http.javadsl.model.headers.CacheDirectives;
import akka.http.javadsl.model.headers.Connection;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.http.scaladsl.server.RequestContextImpl;
import akka.japi.pf.Match;
import akka.javasdk.JsonSupport;
import akka.javasdk.impl.SdkRunner;
import akka.javasdk.impl.http.HttpClassPathResource;
import akka.javasdk.impl.http.HttpRequestContextImpl;
import akka.javasdk.impl.http.WebSockets;
import akka.javasdk.view.EntryWithMetadata;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.google.common.net.HttpHeaders;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import scala.Option;

/**
 * Factory class for creating common HTTP responses in endpoint methods.
 *
 * <p>HttpResponses provides convenient factory methods for creating {@link
 * akka.http.javadsl.model.HttpResponse} objects for the most common HTTP status codes and response
 * types. This eliminates the need to work directly with the lower-level Akka HTTP APIs in most
 * cases.
 *
 * <p><strong>Response Types:</strong>
 *
 * <ul>
 *   <li><strong>Success responses:</strong> {@link #ok()}, {@link #created()}, {@link #accepted()},
 *       {@link #noContent()}
 *   <li><strong>Error responses:</strong> {@link #badRequest()}, {@link #notFound()}, {@link
 *       #internalServerError()}
 *   <li><strong>Static content:</strong> {@link #staticResource(String)} for serving files
 *   <li><strong>Streaming:</strong> {@link #serverSentEvents(Source)} for SSE responses
 * </ul>
 *
 * <p><strong>Content Types:</strong> Methods automatically set appropriate content types:
 *
 * <ul>
 *   <li>String parameters result in {@code text/plain} responses
 *   <li>Object parameters are serialized to JSON with {@code application/json}
 *   <li>Static resources use MIME type detection based on file extension
 * </ul>
 *
 * <p><strong>Response Customization:</strong> All returned {@code HttpResponse} objects can be
 * further customized with additional headers, different status codes, or other modifications using
 * the Akka HTTP API.
 *
 * <p><strong>Static Resources:</strong> Use {@link #staticResource(String)} to serve files from the
 * {@code src/main/resources/static-resources} directory. This is convenient for documentation or
 * small web UIs but not recommended for production where UI and service lifecycles should be
 * decoupled.
 */
public class HttpResponses {

  // static factory class, no instantiation
  private HttpResponses() {}

  /**
   * Creates an HTTP response with specified status code, content type and body.
   *
   * @param statusCode HTTP status code
   * @param contentType HTTP content type
   * @param body HTTP body
   */
  public static HttpResponse of(StatusCode statusCode, ContentType contentType, byte[] body) {
    return HttpResponse.create().withStatus(statusCode).withEntity(contentType, body);
  }

  /** Creates a 200 OK response. */
  public static HttpResponse ok() {
    return HttpResponse.create().withStatus(StatusCodes.OK);
  }

  /** Creates a 200 OK response with a text/plain body. */
  public static HttpResponse ok(String text) {
    if (text == null) throw new IllegalArgumentException("text must not be null");
    return HttpResponse.create().withEntity(ContentTypes.TEXT_PLAIN_UTF8, text);
  }

  /**
   * Creates a 200 OK response with an application/json body. The passed Object is serialized to
   * json using the application's default Jackson serializer.
   */
  public static HttpResponse ok(Object object) {
    if (object == null) throw new IllegalArgumentException("object must not be null");
    var body = JsonSupport.encodeToAkkaByteString(object);
    return HttpResponse.create().withEntity(ContentTypes.APPLICATION_JSON, body);
  }

  /** Creates a 201 CREATED response. */
  public static HttpResponse created() {
    return HttpResponse.create().withStatus(StatusCodes.CREATED);
  }

  /** Creates a 201 CREATED response with a text/plain body. */
  public static HttpResponse created(String text) {
    return ok(text).withStatus(StatusCodes.CREATED);
  }

  /** Creates a 201 CREATED response with a text/plain body and a location header. */
  public static HttpResponse created(String text, String location) {
    return ok(text)
        .withStatus(StatusCodes.CREATED)
        .addHeader(HttpHeader.parse(HttpHeaders.LOCATION, location));
  }

  /**
   * Creates a 201 CREATED response with an application/json body The passed Object is serialized to
   * json using the application's default Jackson serializer.
   */
  public static HttpResponse created(Object object) {
    return ok(object).withStatus(StatusCodes.CREATED);
  }

  /**
   * Creates a 201 CREATED response with an application/json body and a location header. The passed
   * Object is serialized to json using the application's default Jackson serializer.
   */
  public static HttpResponse created(Object object, String location) {
    return ok(object)
        .withStatus(StatusCodes.CREATED)
        .addHeader(HttpHeader.parse(HttpHeaders.LOCATION, location));
  }

  /** Creates a 202 ACCEPTED response. */
  public static HttpResponse accepted() {
    return HttpResponse.create().withStatus(StatusCodes.ACCEPTED);
  }

  /** Creates a 202 ACCEPTED response with a text/plain body. */
  public static HttpResponse accepted(String text) {
    return ok(text).withStatus(StatusCodes.ACCEPTED);
  }

  /**
   * Creates a 202 ACCEPTED response with an application/json body. The passed Object is serialized
   * to json using the application's default Jackson serializer.
   */
  public static HttpResponse accepted(Object object) {
    return ok(object).withStatus(StatusCodes.ACCEPTED);
  }

  /** Creates a 204 NO CONTENT response. */
  public static HttpResponse noContent() {
    return HttpResponse.create().withStatus(StatusCodes.NO_CONTENT);
  }

  /** Creates a 400 BAD REQUEST response. */
  public static HttpResponse badRequest() {
    return HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST);
  }

  /** Creates a 400 BAD REQUEST response with a text/plain body. */
  public static HttpResponse badRequest(String text) {
    return ok(text).withStatus(StatusCodes.BAD_REQUEST);
  }

  /** Creates a 404 NOT FOUND response. */
  public static HttpResponse notFound() {
    return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND);
  }

  /** Creates a 404 NOT FOUND response with a text/plain body. */
  public static HttpResponse notFound(String text) {
    return ok(text).withStatus(StatusCodes.NOT_FOUND);
  }

  /** Creates a 500 INTERNAL SERVER ERROR response. */
  public static HttpResponse internalServerError() {
    return HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
  }

  /** Creates a 500 INTERNAL SERVER ERROR response with a text/plain body. */
  public static HttpResponse internalServerError(String text) {
    return ok(text).withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
  }

  /** Creates a 501 NOT IMPLEMENTED response. */
  public static HttpResponse notImplemented() {
    return HttpResponse.create().withStatus(StatusCodes.NOT_IMPLEMENTED);
  }

  /** Creates a 501 NOT IMPLEMENTED response with a text/plain body. */
  public static HttpResponse notImplemented(String text) {
    return ok(text).withStatus(StatusCodes.NOT_IMPLEMENTED);
  }

  /**
   * Load a resource from the class-path directory <code>static-resources</code> and return it as an
   * HTTP response.
   *
   * @param resourcePath A relative path to the resource folder <code>static-resources</code> on the
   *     class path. Must not start with <code>/</code>
   * @return A 404 not found response if there is no such resource. 403 forbidden if the path
   *     contains <code>..</code> or references a folder.
   */
  public static HttpResponse staticResource(String resourcePath) {
    return HttpClassPathResource.fromStaticPath(resourcePath);
  }

  /**
   * Load a resource from the class-path directory <code>static-resources</code> and return it as an
   * HTTP response.
   *
   * @param request A request to use the path from
   * @param prefixToStrip Strip this prefix from the request path, to create the actual path
   *     relative to <code>static-resources</code> to load the resource from. Must not be empty.
   * @return A 404 not found response if there is no such resource. 403 forbidden if the path
   *     contains <code>..</code> or references a folder.
   * @throws RuntimeException if the request path does not start with <code>prefixToStrip</code> or
   *     if <code>prefixToStrip</code> is empty
   */
  public static HttpResponse staticResource(HttpRequest request, String prefixToStrip) {
    if (prefixToStrip.isEmpty()) throw new RuntimeException("prefixToStrip must not be empty");
    var actualPrefixToStrip = prefixToStrip.startsWith("/") ? prefixToStrip : "/" + prefixToStrip;
    actualPrefixToStrip =
        actualPrefixToStrip.endsWith("/") ? actualPrefixToStrip : actualPrefixToStrip + "/";
    var fullPath = request.getUri().getPathString();
    if (!fullPath.startsWith(actualPrefixToStrip)) {
      throw new RuntimeException(
          "Request path ["
              + fullPath
              + "] does not start with the expected prefix ["
              + prefixToStrip
              + "]");
    }
    var strippedPath = fullPath.substring(actualPrefixToStrip.length());
    return staticResource(strippedPath);
  }

  /**
   * @param source A stream of text
   * @return A chunked HTTP response that will emit the text as it arrives rather than collect all
   *     before responding
   */
  public static HttpResponse streamText(Source<String, ?> source) {
    return HttpResponse.create()
        .withStatus(StatusCodes.OK)
        .withEntity(
            HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, source.map(ByteString::fromString)));
  }

  private static final ContentType TEXT_EVENT_STREAM = ContentTypes.parse("text/event-stream");

  /**
   * Return a stream of events as an HTTP SSE response. <a
   * href="https://html.spec.whatwg.org/multipage/server-sent-events.html">See the Living HTML
   * standard for more details on SSE</a>
   *
   * <p><b>Note</b> that browsers only support consuming SSE using HTTP GET requests.
   *
   * <p><b>Note</b> in most cases you should use one of the overloads extracting an event id so that
   * clients can reconnect and continue the stream form the last seen event in case the response
   * connection is lost. This overload of the method can only be used in scenarios where a
   * reconnecting client without any offset to start from is fine.
   *
   * @return A HttpResponse with a server sent events (SSE) stream response. The HTTP response will
   *     contain each element in the source, rendered to JSON using jackson. An SSE keepalive
   *     element is emitted every 10 seconds if the stream is idle.
   */
  public static <T> HttpResponse serverSentEvents(Source<T, ?> source) {
    return serverSentEvents(source, t -> t, Optional.empty(), Optional.empty());
  }

  /**
   * Return a stream of events as an HTTP SSE response. <a
   * href="https://html.spec.whatwg.org/multipage/server-sent-events.html">See the Living HTML
   * standard for more details on SSE</a>
   *
   * <p><b>Note</b> that browsers only support consuming SSE using HTTP GET requests.
   *
   * @param extractEventId A function to extract a unique id or offset from the events to include in
   *     the stream as SSE event id. This is then used by clients, passed as a header, in an HTTP
   *     endpoint this will be available from {@link RequestContext#lastSeenSseEventId()} in the
   *     HTTP endpoint. The extracted string id must not contain the null character, line feed or
   *     carriage return.
   * @return A HttpResponse with a server sent events (SSE) stream response. The HTTP response will
   *     contain each element in the source, rendered to JSON using jackson. An SSE keepalive
   *     element is emitted every 10 seconds if the stream is idle.
   */
  public static <T> HttpResponse serverSentEvents(
      Source<T, ?> source, Function<T, String> extractEventId) {
    return serverSentEvents(source, t -> t, Optional.of(extractEventId), Optional.empty());
  }

  /**
   * Return a stream of events as an HTTP Server Sent Events (SSE) response. <a
   * href="https://html.spec.whatwg.org/multipage/server-sent-events.html">See the Living HTML
   * standard for more details on SSE</a>.
   *
   * <p><b>Note</b> that browsers only support consuming SSE using HTTP GET requests.
   *
   * @param extractEventId A function to extract a unique id or offset from the events to include in
   *     the stream as SSE event id. This is then used by clients, passed as a header, in an HTTP
   *     endpoint this will be available from {@link RequestContext#lastSeenSseEventId()} in the
   *     HTTP endpoint. The extracted string id must not contain the null character, line feed or
   *     carriage return.
   * @param extractEventType A function extracting an event type for the event, making it easier for
   *     the SSE client to distinguish between a set of different kinds of events emitted.
   * @return A HttpResponse with a server sent events (SSE) stream response. The HTTP response will
   *     contain each element in the source, rendered to JSON using jackson. An SSE keepalive
   *     element is emitted every 10 seconds if the stream is idle.
   */
  public static <T> HttpResponse serverSentEvents(
      Source<T, ?> source,
      Function<T, String> extractEventId,
      Function<T, String> extractEventType) {
    return serverSentEvents(
        source, t -> t, Optional.of(extractEventId), Optional.of(extractEventType));
  }

  /**
   * Convenience for emitting a streaming-updates view query as resume-able SSE stream, where the
   * latest seen event is where the query continues on reconnect.
   *
   * @param source A source from the view component client
   * @return An HTTP stream with the events from the query
   * @param <T> The type of the entries in the view
   */
  public static <T> HttpResponse serverSentEventsForView(Source<EntryWithMetadata<T>, ?> source) {
    Function<EntryWithMetadata<T>, String> extractId = entry -> entry.lastUpdated().toString();
    return serverSentEvents(
        source, EntryWithMetadata::entry, Optional.of(extractId), Optional.empty());
  }

  private static <T> HttpResponse serverSentEvents(
      Source<T, ?> source,
      Function<T, Object> extractValue,
      Optional<Function<T, String>> extractEventId,
      Optional<Function<T, String>> extractEventType) {
    var sseSource =
        source
            .map(
                elem -> {
                  var jsonPayload =
                      JsonSupport.getObjectMapper().writeValueAsString(extractValue.apply(elem));
                  var eventId = extractEventId.map(f -> f.apply(elem));
                  var eventType = extractEventType.map(f -> f.apply(elem));
                  return ServerSentEvent.create(
                      jsonPayload, eventType, eventId, OptionalInt.empty());
                })
            .keepAlive(Duration.ofSeconds(10), ServerSentEvent::heartbeat)
            .map(ServerSentEvent::encode)
            .recoverWith(
                Match.<Throwable, Source<ByteString, NotUsed>, Throwable>match(
                        Throwable.class,
                        (ex) -> {
                          // Note: no natural way to convey stream errors to client with SSE - the
                          // HTTP response with status is already sent to client
                          //       so we recover/complete stream and log error
                          SdkRunner.userServiceLog()
                              .error("HTTP endpoint SSE stream failed with error", ex);
                          return Source.<ByteString>empty();
                        })
                    .build());

    return HttpResponse.create()
        .withStatus(StatusCodes.OK)
        .withHeaders(
            Arrays.asList(
                CacheControl.create(CacheDirectives.NO_CACHE), Connection.create("keep-alive")))
        .withEntity(HttpEntities.create(TEXT_EVENT_STREAM, sseSource));
  }

  /**
   * Expect and handle a websocket upgrade in a HttpEndpoint {@code Get} method. Incoming binary
   * websocket messages will be passed into the flow and ByteStrings emitted from the flow will be
   * passed back to the client.
   *
   * <p>The response or web socket stream will be failed if:
   *
   * <ul>
   *   <li>The request is not a WebSocket upgrade request
   *   <li>If the client sends a websocket text message rather than binary message.
   *   <li>If the client sends an individual streamed message payload that takes a long time to
   *       arrive
   * </ul>
   *
   * <p><b>Important:</b> WebSocket endpoint methods are not served out of the box in deployed
   * service, additional route configuration is required. See <a href="link-to-docs">the Akka SDK
   * documentation</a> for details
   *
   * @param requestContext The request context from the endpoint (the endpoint must extend
   *     AbstractHttpEndpoint to access it)
   * @param handler A flow that will be fed incoming messages, outgoing messages will be passed back
   *     to the client.
   * @return An HTTP response to return from the HttpEndpoint method
   */
  public static HttpResponse binaryWebsocket(
      RequestContext requestContext, Flow<ByteString, ByteString, NotUsed> handler) {
    var requestContextImpl = ((RequestContextImpl) requestContext);
    return WebSockets.binaryWebSocketResponse(
        requestContextImpl.request(), Option.empty(), handler, requestContextImpl.materializer());
  }

  /**
   * Expect and handle a websocket upgrade in a HttpEndpoint {@code Get} method. Incoming binary
   * websocket messages will be passed into the flow and ByteStrings emitted from the flow will be
   * passed back to the client.
   *
   * <p>The response or web socket stream will be failed if:
   *
   * <ul>
   *   <li>The request is not a WebSocket upgrade request
   *   <li>The request does not contain the specified subprotocol
   *   <li>If the client sends a websocket text message rather than binary message.
   *   <li>If the client sends an individual streamed message payload that takes a long time to
   *       arrive
   * </ul>
   *
   * <p><b>Important:</b> WebSocket endpoint methods are not served out of the box in deployed
   * service, additional route configuration is required. See <a href="link-to-docs">the Akka SDK
   * documentation</a> for details
   *
   * @param requestContext The request context from the endpoint (the endpoint must extend
   *     AbstractHttpEndpoint to access it)
   * @param protocol An application level protocol that is supported by this websocket
   * @param handler A flow that will be fed incoming messages, outgoing messages will be passed back
   *     to the client.
   * @return An HTTP response to return from the HttpEndpoint method
   */
  public static HttpResponse binaryWebsocket(
      RequestContext requestContext,
      String protocol,
      Flow<ByteString, ByteString, NotUsed> handler) {
    var requestContextImpl = ((RequestContextImpl) requestContext);
    return WebSockets.binaryWebSocketResponse(
        requestContextImpl.request(),
        Option.apply(protocol),
        handler,
        requestContextImpl.materializer());
  }

  /**
   * Expect and handle a websocket upgrade in a HttpEndpoint {@code Get} method. Incoming websocket
   * text messages will be passed into the flow and strings emitted from the flow will be passed
   * back to the client.
   *
   * <p>The response or web socket stream will be failed if:
   *
   * <ul>
   *   <li>The request is not a WebSocket upgrade request
   *   <li>If the client sends a websocket binary message rather than text message.
   *   <li>If the client sends an individual streamed message payload that takes a long time to
   *       arrive
   * </ul>
   *
   * <p><b>Important:</b> WebSocket endpoint methods are not served out of the box in deployed
   * service, additional route configuration is required. See <a href="link-to-docs">the Akka SDK
   * documentation</a> for details
   *
   * @param requestContext The request context from the endpoint (the endpoint must extend
   *     AbstractHttpEndpoint to access it)
   * @param handler A flow that will be fed incoming messages, outgoing messages will be passed back
   *     to the client.
   * @return An HTTP response to return from the HttpEndpoint method
   */
  public static HttpResponse textWebsocket(
      RequestContext requestContext, Flow<String, String, NotUsed> handler) {
    var requestContextImpl = ((HttpRequestContextImpl) requestContext);
    return WebSockets.textWebSocketResponse(
        requestContextImpl.request(), Option.empty(), handler, requestContextImpl.materializer());
  }

  /**
   * Expect and handle a websocket upgrade in a HttpEndpoint {@code Get} method. Incoming websocket
   * text messages will be passed into the flow and strings emitted from the flow will be passed
   * back to the client.
   *
   * <p>The response or web socket stream will be failed if:
   *
   * <ul>
   *   <li>The request is not a WebSocket upgrade request
   *   <li>The request does not contain the specified subprotocol
   *   <li>If the client sends a websocket binary message rather than text message.
   *   <li>If the client sends an individual streamed message payload that takes a long time to
   *       arrive
   * </ul>
   *
   * <p><b>Important:</b> WebSocket endpoint methods are not served out of the box in deployed
   * service, additional route configuration is required. See <a href="link-to-docs">the Akka SDK
   * documentation</a> for details
   *
   * @param requestContext The request context from the endpoint (the endpoint must extend
   *     AbstractHttpEndpoint to access it)
   * @param protocol An application level protocol that is supported by this websocket
   * @param handler A flow that will be fed incoming messages, outgoing messages will be passed back
   *     to the client.
   * @return An HTTP response to return from the HttpEndpoint method
   */
  public static HttpResponse textWebsocket(
      RequestContext requestContext, String protocol, Flow<String, String, NotUsed> handler) {
    var requestContextImpl = ((HttpRequestContextImpl) requestContext);
    return WebSockets.textWebSocketResponse(
        requestContextImpl.request(),
        Option.apply(protocol),
        handler,
        requestContextImpl.materializer());
  }
}
