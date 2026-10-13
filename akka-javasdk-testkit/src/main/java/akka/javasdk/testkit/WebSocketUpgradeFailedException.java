/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.annotation.InternalApi;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.StatusCode;
import java.util.List;

/**
 * Thrown by {@link WebSocketRouteTester} when the service rejects the WebSocket upgrade request,
 * for example with 403 when the request has a JWT that does not match the {@code @JWT}
 * requirements, or with the status of an {@link akka.javasdk.http.HttpException} that the endpoint
 * method throws.
 *
 * <p>The response body of a rejected upgrade is not available, only the status and the headers.
 */
public final class WebSocketUpgradeFailedException extends RuntimeException {

  private final String uri;
  private final StatusCode status;
  private final List<HttpHeader> headers;

  /** INTERNAL API */
  @InternalApi
  public WebSocketUpgradeFailedException(
      String uri, StatusCode status, List<HttpHeader> headers, String reason) {
    super("WebSocket upgrade request was rejected: " + reason);
    this.uri = uri;
    this.status = status;
    this.headers = headers;
  }

  /** The URI of the rejected upgrade request */
  public String uri() {
    return uri;
  }

  /** The HTTP status code the service responded with */
  public StatusCode status() {
    return status;
  }

  /** The HTTP headers the service responded with */
  public List<HttpHeader> headers() {
    return headers;
  }
}
