/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;


import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.CacheControl;
import akka.http.javadsl.model.headers.CacheDirectives;
import akka.http.javadsl.model.headers.Connection;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.javasdk.JsonSupport;
import akka.javasdk.impl.http.HttpClassPathResource;
import akka.stream.javadsl.Source;
import com.google.common.net.HttpHeaders;

import java.time.Duration;
import java.util.Arrays;


/**
 * Factory class for creating common HTTP responses in endpoint methods.
 * <p>
 * HttpResponses provides convenient factory methods for creating {@link akka.http.javadsl.model.HttpResponse}
 * objects for the most common HTTP status codes and response types. This eliminates the need to work
 * directly with the lower-level Akka HTTP APIs in most cases.
 * <p>
 * <strong>Response Types:</strong>
 * <ul>
 *   <li><strong>Success responses:</strong> {@link #ok()}, {@link #created()}, {@link #accepted()}, {@link #noContent()}</li>
 *   <li><strong>Error responses:</strong> {@link #badRequest()}, {@link #notFound()}, {@link #internalServerError()}</li>
 *   <li><strong>Static content:</strong> {@link #staticResource(String)} for serving files</li>
 *   <li><strong>Streaming:</strong> {@link #serverSentEvents(Source)} for SSE responses</li>
 * </ul>
 * <p>
 * <strong>Content Types:</strong>
 * Methods automatically set appropriate content types:
 * <ul>
 *   <li>String parameters result in {@code text/plain} responses</li>
 *   <li>Object parameters are serialized to JSON with {@code application/json}</li>
 *   <li>Static resources use MIME type detection based on file extension</li>
 * </ul>
 * <p>
 * <strong>Response Customization:</strong>
 * All returned {@code HttpResponse} objects can be further customized with additional headers,
 * different status codes, or other modifications using the Akka HTTP API.
 * <p>
 * <strong>Static Resources:</strong>
 * Use {@link #staticResource(String)} to serve files from the {@code src/main/resources/static-resources}
 * directory. This is convenient for documentation or small web UIs but not recommended for production
 * where UI and service lifecycles should be decoupled.
 */
public class HttpResponses {

  // static factory class, no instantiation
  private HttpResponses() {}

  /**
   * Creates an HTTP response with specified status code, content type and body.
   *
   * @param statusCode  HTTP status code
   * @param contentType HTTP content type
   * @param body        HTTP body
   */
  public static HttpResponse of(StatusCode statusCode, ContentType contentType, byte[] body) {
    return HttpResponse.create().withStatus(statusCode).withEntity(contentType, body);
  }

  /**
   * Creates a 200 OK response.
   */
  public static HttpResponse ok() {
    return HttpResponse.create().withStatus(StatusCodes.OK);
  }

  /**
   * Creates a 200 OK response with a text/plain body.
   */
  public static HttpResponse ok(String text) {
    if (text == null) throw new IllegalArgumentException("text must not be null");
    return HttpResponse.create().withEntity(ContentTypes.TEXT_PLAIN_UTF8, text);
  }

  /**
   * Creates a 200 OK response with an application/json body.
   * The passed Object is serialized to json using the application's default Jackson serializer.
   */
  public static HttpResponse ok(Object object) {
    if (object == null) throw new IllegalArgumentException("object must not be null");
    var body = JsonSupport.encodeToAkkaByteString(object);
    return HttpResponse.create().withEntity(ContentTypes.APPLICATION_JSON, body);
  }

  /**
   * Creates a 201 CREATED response.
   */
  public static HttpResponse created() {
    return HttpResponse.create().withStatus(StatusCodes.CREATED);
  }

  /**
   * Creates a 201 CREATED response with a text/plain body.
   */
  public static HttpResponse created(String text) {
    return ok(text).withStatus(StatusCodes.CREATED);
  }

  /**
   * Creates a 201 CREATED response with a text/plain body and a location header.
   */
  public static HttpResponse created(String text, String location) {
    return ok(text)
      .withStatus(StatusCodes.CREATED)
      .addHeader(HttpHeader.parse(HttpHeaders.LOCATION, location));
  }

  /**
   * Creates a 201 CREATED response with an application/json body
   * The passed Object is serialized to json using the application's default Jackson serializer.
   */
  public static HttpResponse created(Object object) {
    return ok(object).withStatus(StatusCodes.CREATED);
  }

  /**
   * Creates a 201 CREATED response with an application/json body and a location header.
   * The passed Object is serialized to json using the application's default Jackson serializer.
   */
  public static HttpResponse created(Object object, String location) {
    return ok(object).withStatus(StatusCodes.CREATED)
      .addHeader(HttpHeader.parse(HttpHeaders.LOCATION, location));
  }

  /**
   * Creates a 202 ACCEPTED response.
   */
  public static HttpResponse accepted() {
    return HttpResponse.create().withStatus(StatusCodes.ACCEPTED);
  }

  /**
   * Creates a 202 ACCEPTED response with a text/plain body.
   */
  public static HttpResponse accepted(String text) {
    return ok(text).withStatus(StatusCodes.ACCEPTED);
  }

  /**
   * Creates a 202 ACCEPTED response with an application/json body.
   * The passed Object is serialized to json using the application's default Jackson serializer.
   */
  public static HttpResponse accepted(Object object) {
    return ok(object).withStatus(StatusCodes.ACCEPTED);
  }

  /**
   * Creates a 204 NO CONTENT response.
   */
  public static HttpResponse noContent() {
    return HttpResponse.create().withStatus(StatusCodes.NO_CONTENT);
  }

  /**
   * Creates a 400 BAD REQUEST response.
   */
  public static HttpResponse badRequest() {
    return HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST);
  }

  /**
   * Creates a 400 BAD REQUEST response with a text/plain body.
   */
  public static HttpResponse badRequest(String text) {
    return ok(text).withStatus(StatusCodes.BAD_REQUEST);
  }

  /**
   * Creates a 404 NOT FOUND response.
   */
  public static HttpResponse notFound() {
    return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND);
  }

  /**
   * Creates a 404 NOT FOUND response with a text/plain body.
   */
  public static HttpResponse notFound(String text) {
    return ok(text).withStatus(StatusCodes.NOT_FOUND);
  }

  /**
   * Creates a 500 INTERNAL SERVER ERROR response.
   */
  public static HttpResponse internalServerError() {
    return HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
  }

  /**
   * Creates a 500 INTERNAL SERVER ERROR response with a text/plain body.
   */
  public static HttpResponse internalServerError(String text) {
    return ok(text).withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
  }

  /**
   * Creates a 501 NOT IMPLEMENTED response.
   */
  public static HttpResponse notImplemented() {
    return HttpResponse.create().withStatus(StatusCodes.NOT_IMPLEMENTED);
  }

  /**
   * Creates a 501 NOT IMPLEMENTED response with a text/plain body.
   */
  public static HttpResponse notImplemented(String text) {
    return ok(text).withStatus(StatusCodes.NOT_IMPLEMENTED);
  }

  /**
   * Load a resource from the class-path directory <code>static-resources</code> and return it as an HTTP response.
   *
   * @param resourcePath A relative path to the resource folder <code>static-resources</code> on the class path. Must not
   *                     start with <code>/</code>
   * @return A 404 not found response if there is no such resource. 403 forbidden if the path contains <code>..</code> or references a folder.
   */
  public static HttpResponse staticResource(String resourcePath) {
    return HttpClassPathResource.fromStaticPath(resourcePath);
  }


  /**
   * Load a resource from the class-path directory <code>static-resources</code> and return it as an HTTP response.
   *
   * @param request A request to use the path from
   * @param prefixToStrip Strip this prefix from the request path, to create the actual path relative to <code>static-resources</code>
   *                      to load the resource from. Must not be empty.
   * @return A 404 not found response if there is no such resource. 403 forbidden if the path contains <code>..</code> or references a folder.
   * @throws RuntimeException if the request path does not start with <code>prefixToStrip</code> or if <code>prefixToStrip</code> is empty
   */
  public static HttpResponse staticResource(HttpRequest request, String prefixToStrip) {
    if (prefixToStrip.isEmpty()) throw new RuntimeException("prefixToStrip must not be empty");
    var actualPrefixToStrip = prefixToStrip.startsWith("/") ? prefixToStrip : "/" + prefixToStrip;
    actualPrefixToStrip = actualPrefixToStrip.endsWith("/") ? actualPrefixToStrip : actualPrefixToStrip + "/";
    var fullPath = request.getUri().getPathString();
    if (!fullPath.startsWith(actualPrefixToStrip)) {
      throw new RuntimeException("Request path [" + fullPath + "] does not start with the expected prefix [" + prefixToStrip + "]");
    }
    var strippedPath = fullPath.substring(actualPrefixToStrip.length());
    return staticResource(strippedPath);
  }


  private final static ContentType TEXT_EVENT_STREAM = ContentTypes.parse("text/event-stream");

  /**
   * @return A HttpResponse with a server sent events (SSE) stream response. The HTTP response will contain each element
   *         in the source, rendered to JSON using jackson. An SSE keepalive element is emitted every 10 seconds if the stream
   *         is idle.
   */
  public static <T> HttpResponse serverSentEvents(Source<T, ?> source) {
    var sseSource = source
        .map(elem -> ServerSentEvent.create(JsonSupport.getObjectMapper().writeValueAsString(elem)))
        .keepAlive(Duration.ofSeconds(10), ServerSentEvent::heartbeat)
        // Note: using Akka HTTP internal method
        .map(e -> ((akka.http.scaladsl.model.sse.ServerSentEvent) e).encode());

    return HttpResponse.create()
        .withStatus(StatusCodes.OK)
        .withHeaders(Arrays.asList(
            CacheControl.create(CacheDirectives.NO_CACHE),
            Connection.create("keep-alive")
        ))
        .withEntity(HttpEntities.create(TEXT_EVENT_STREAM, sseSource));
  }


}
