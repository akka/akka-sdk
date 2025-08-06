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
 * Annotation to mark a method as handling HTTP DELETE requests.
 *
 * <p>DELETE requests are used to remove resources from the server. They should be idempotent,
 * meaning multiple identical requests should have the same effect (the resource remains deleted).
 *
 * <p><strong>Path Configuration:</strong> The annotation value specifies the path pattern for this
 * endpoint, which is combined with the {@link HttpEndpoint} class-level path prefix to form the
 * complete URL.
 *
 * <p><strong>Path Parameters:</strong> Use {@code {paramName}} in the path to identify the specific
 * resource to delete. DELETE requests typically don't include request bodies.
 *
 * <p><strong>Response Types:</strong> DELETE methods typically return:
 *
 * <ul>
 *   <li>204 No Content for successful deletion with no response body
 *   <li>200 OK with confirmation message or deleted resource representation
 *   <li>404 Not Found if the resource doesn't exist
 * </ul>
 *
 * Use {@link akka.javasdk.http.HttpResponses#noContent()} for standard deletion responses.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Delete {
  String value() default "";
}
