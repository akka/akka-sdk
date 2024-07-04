/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.http;


import akka.http.javadsl.model.HttpResponse;

/**
 * A strict response that contains both the HTTP response and the body.
 * <p>
 * The body is derived from the HttpResponse. Meaning that the HttpResponse body content have
 * already been fully received and cannot be consumed once more. To access its content use the body field.
 * <p>
 * The HttpResponse can be used to access other response fields, like content-type, headers and http status code.
 * <p>
 *
 * @param <T> The type of the body in the response.
 * @param httpResponse The HTTP response.
 * @param body The body of the response, already parsed and ready to be used.
 */
public record StrictResponse<T>(HttpResponse httpResponse, T body) {

}