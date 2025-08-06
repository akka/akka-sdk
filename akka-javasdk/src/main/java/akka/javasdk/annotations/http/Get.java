/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.http;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a method as handling HTTP GET requests.
 *
 * <p>GET requests are used to retrieve data from the server and should be idempotent and safe (no
 * side effects). They typically don't include request bodies and use path parameters and query
 * parameters for input.
 *
 * <p><strong>Path Configuration:</strong> The annotation value specifies the path pattern for this
 * endpoint, which is combined with the {@link HttpEndpoint} class-level path prefix to form the
 * complete URL.
 *
 * <p><strong>Path Parameters:</strong> Use {@code {paramName}} in the path to extract URL segments
 * as method parameters. Parameters can be strings, numbers, or other primitive types.
 *
 * <p><strong>Query Parameters:</strong> Access query parameters via {@code
 * RequestContext.queryParams()} when extending {@link akka.javasdk.http.AbstractHttpEndpoint} or
 * injecting {@link akka.javasdk.http.RequestContext}.
 *
 * <p><strong>Response Types:</strong> GET methods can return strings, objects (serialized to JSON),
 * {@code HttpResponse} for full control, or {@code CompletionStage<T>} for asynchronous responses.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Get {
  String value() default "";
}
