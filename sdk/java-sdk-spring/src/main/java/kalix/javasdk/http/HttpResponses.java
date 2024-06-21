/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.http;


import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;
import kalix.javasdk.JsonSupport;


/**
 * Helper class for creating common HTTP responses.
 * <p>
 * Provides factory method for creating HttpResponse object for the most common cases.
 * <p>
 * Returned HttpResponses can be enriched with additional headers, status codes, etc.
 */
public class HttpResponses {


  /**
   * Creates an HTTP response with a text/plain body.
   */
  public static HttpResponse plainTextResponse(String text) {
    if (text == null) throw new IllegalArgumentException("text must not be null");
    return HttpResponse.create().withEntity(ContentTypes.TEXT_PLAIN_UTF8, text);
  }

  /**
   * Creates an HTTP response with an application/json body.
   * The passed Object is serialized to json using the application's default Jackson serializer.
   */
  public static HttpResponse jsonResponse(Object object) {
    if (object == null) throw new IllegalArgumentException("object must not be null");
    try {
      byte[] body = JsonSupport.encodeToBytes(object).toByteArray();
      return HttpResponse.create().withEntity(ContentTypes.APPLICATION_JSON, body);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates an HTTP response with an application/octet-stream body.
   */
  public static HttpResponse bytesResponse(byte[] body) {
    return HttpResponse.create().withEntity(ContentTypes.APPLICATION_OCTET_STREAM, body);
  }

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
  public static HttpResponse Ok() {
    return HttpResponse.create().withStatus(StatusCodes.OK);
  }

  /**
   * Creates a 200 OK response with a text/plain body.
   */
  public static HttpResponse Ok(String text) {
    return plainTextResponse(text).withStatus(StatusCodes.OK);
  }

  /**
   * Creates a 200 OK response with an application/json body.
   * The passed Object is serialized to json using the application's default Jackson serializer.
   */
  public static HttpResponse Ok(Object object) {
    return jsonResponse(object).withStatus(StatusCodes.OK);
  }

  /**
   * Creates a 200 OK response with an application/octet-stream body.
   */
  public static HttpResponse Ok(byte[] body) {
    return bytesResponse(body).withStatus(StatusCodes.OK);
  }

  /**
   * Creates a 201 CREATED response.
   */
  public static HttpResponse Created() {
    return HttpResponse.create().withStatus(StatusCodes.CREATED);
  }

  /**
   * Creates a 201 OK response with a text/plain body.
   */
  public static HttpResponse Created(String text) {
    return plainTextResponse(text).withStatus(StatusCodes.CREATED);
  }

  /**
   * Creates a 201 OK response with a text/plain body and a location header.
   */
  public static HttpResponse Created(String text, String location) {
    return plainTextResponse(text)
      .withStatus(StatusCodes.CREATED)
      .addHeader(HttpHeader.parse(HttpHeaders.LOCATION, location));
  }

  /**
   * Creates a 200 OK response with an application/json body
   * The passed Object is serialized to json using the application's default Jackson serializer.
   */
  public static HttpResponse Created(Object object) {
    return jsonResponse(object).withStatus(StatusCodes.CREATED);
  }

  /**
   * Creates a 200 OK response with an application/json body and a location header.
   * The passed Object is serialized to json using the application's default Jackson serializer.
   */
  public static HttpResponse Created(Object object, String location) {
    return jsonResponse(object).withStatus(StatusCodes.CREATED)
      .addHeader(HttpHeader.parse(HttpHeaders.LOCATION, location));
  }

  /**
   * Creates a 201 OK response with an application/octet-stream body.
   */
  public static HttpResponse Created(byte[] body) {
    return bytesResponse(body).withStatus(StatusCodes.CREATED);
  }

  /**
   * Creates a 201 OK response with an application/octet-stream body and a location header.
   */
  public static HttpResponse Created(byte[] body, String location) {
    return bytesResponse(body).withStatus(StatusCodes.CREATED)
      .addHeader(HttpHeader.parse(HttpHeaders.LOCATION, location));
  }

  /**
   * Creates a 202 ACCEPTED response.
   */
  public static HttpResponse Accepted() {
    return HttpResponse.create().withStatus(StatusCodes.ACCEPTED);
  }

  /**
   * Creates a 202 ACCEPTED response with a text/plain body.
   */
  public static HttpResponse Accepted(String text) {
    return plainTextResponse(text).withStatus(StatusCodes.ACCEPTED);
  }

  /**
   * Creates a 202 ACCEPTED response with an application/json body.
   * The passed Object is serialized to json using the application's default Jackson serializer.
   */
  public static HttpResponse Accepted(Object object) {
    return jsonResponse(object).withStatus(StatusCodes.ACCEPTED);
  }

  /**
   * Creates a 202 ACCEPTED response with an application/octet-stream body.
   */
  public static HttpResponse Accepted(byte[] body) {
    return bytesResponse(body).withStatus(StatusCodes.ACCEPTED);
  }

  /**
   * Creates a 204 NO CONTENT response.
   */
  public static HttpResponse NoContent() {
    return HttpResponse.create().withStatus(StatusCodes.NO_CONTENT);
  }

  /**
   * Creates a 400 BAD REQUEST response.
   */
  public static HttpResponse BadRequest() {
    return HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST);
  }

  /**
   * Creates a 400 BAD REQUEST response with a text/plain body.
   */
  public static HttpResponse BadRequest(String text) {
    return plainTextResponse(text).withStatus(StatusCodes.BAD_REQUEST);
  }

  /**
   * Creates a 404 NOT FOUND response.
   */
  public static HttpResponse NotFound() {
    return HttpResponse.create().withStatus(StatusCodes.NOT_FOUND);
  }

  /**
   * Creates a 404 NOT FOUND response with a text/plain body.
   */
  public static HttpResponse NotFound(String text) {
    return plainTextResponse(text).withStatus(StatusCodes.NOT_FOUND);
  }

  /**
   * Creates a 500 INTERNAL SERVER ERROR response.
   */
  public static HttpResponse InternalServerError() {
    return HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
  }

  /**
   * Creates a 500 INTERNAL SERVER ERROR response with a text/plain body.
   */
  public static HttpResponse InternalServerError(String text) {
    return plainTextResponse(text).withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
  }

  /**
   * Creates a 501 NOT IMPLEMENTED response.
   */
  public static HttpResponse NotImplemented() {
    return HttpResponse.create().withStatus(StatusCodes.NOT_IMPLEMENTED);
  }

  /**
   * Creates a 501 NOT IMPLEMENTED response with a text/plain body.
   */
  public static HttpResponse NotImplemented(String text) {
    return plainTextResponse(text).withStatus(StatusCodes.NOT_IMPLEMENTED);
  }


}
