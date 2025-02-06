/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.http;


import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.CacheControl;
import akka.http.javadsl.model.headers.CacheDirectives;
import akka.http.javadsl.model.headers.Connection;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.javasdk.JsonSupport;
import akka.javasdk.impl.http.HttpClassPathResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import akka.stream.javadsl.Source;
import com.google.common.net.HttpHeaders;

import java.time.Duration;
import java.util.Arrays;


/**
 * Helper class for creating common HTTP responses.
 * <p>
 * Provides factory method for creating HttpResponse object for the most common cases.
 * <p>
 * Returned HttpResponses can be enriched with additional headers, status codes, etc.
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
   * FIXME a lot to describe here
   *
   * Returns 404 if there is no such resource.
   */
  public static HttpResponse resourceFromClassPath(String absolutePath) {
    return HttpClassPathResource.fromStaticPath(absolutePath);
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
