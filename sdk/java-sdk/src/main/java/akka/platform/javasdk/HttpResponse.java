/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk;


import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Represents an HTTP response with more level control over the status code, content type and body.
 */
public class HttpResponse {

  public static final String STATUS_CODE_EXTENSION_TYPE_URL = "Status-Code";

  private final StatusCode statusCode;
  private final String contentType;
  private final byte[] body;

  private HttpResponse(StatusCode statusCode, String contentType, byte[] body) {
    if (statusCode == null) throw new IllegalArgumentException("statusCode must not be null");
    if (contentType == null) throw new IllegalArgumentException("contentType must not be null");
    if (body == null) throw new IllegalArgumentException("body must not be null");
    this.statusCode = statusCode;
    this.contentType = contentType;
    this.body = body;
  }

  private HttpResponse(StatusCode statusCode) {
    this(statusCode, "application/octet-stream", new byte[0]);
  }

  public StatusCode getStatusCode() {
    return statusCode;
  }

  public String getContentType() {
    return contentType;
  }

  public byte[] getBody() {
    return body;
  }

  /**
   * Creates a 200 OK response.
   */
  public static HttpResponse ok() {
    return new HttpResponse(StatusCodes.OK);
  }

  /**
   * Creates a 201 CREATED response.
   */
  public static HttpResponse created() {
    return new HttpResponse(StatusCodes.CREATED);
  }

  /**
   * Creates a 202 ACCEPTED response.
   */
  public static HttpResponse accepted() {
    return new HttpResponse(StatusCodes.ACCEPTED);
  }

  /**
   * Creates a 204 NO CONTENT response.
   */
  public static HttpResponse noContent() {
    return new HttpResponse(StatusCodes.NO_CONTENT);
  }

  /**
   * Creates a 200 OK response with a text/plain body.
   */
  public static HttpResponse ok(String text) {
    if (text == null) throw new IllegalArgumentException("text must not be null");
    return new HttpResponse(StatusCodes.OK, "text/plain", text.getBytes(UTF_8));
  }

  /**
   * Creates a 200 OK response with a application/json body. Object is encoded using Jackson serializer.
   */
  public static HttpResponse ok(Object object) {
    if (object == null) throw new IllegalArgumentException("object must not be null");
    try {
      byte[] body = JsonSupport.encodeToBytes(object).toByteArray();
      return new HttpResponse(StatusCodes.OK, "application/json", body);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates a 200 OK response with a application/octet-stream body.
   */
  public static HttpResponse ok(byte[] body) {
    return new HttpResponse(StatusCodes.OK, "application/octet-stream", body);
  }

  /**
   * Creates an HTTP response with specified status code, content type and body.
   *
   * @param statusCode  HTTP status code
   * @param contentType HTTP content type
   * @param body        HTTP body
   */
  public static HttpResponse of(StatusCode statusCode, String contentType, byte[] body) {
    return new HttpResponse(statusCode, contentType, body);
  }

  /**
   * Parses an HTTP body to a specified JSON type using Jackson deserializer.
   */
  public <T> T bodyAsJson(Class<T> clazz) {
    try {
      return JsonSupport.parseBytes(body, clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}