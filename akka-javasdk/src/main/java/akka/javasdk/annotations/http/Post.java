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
 * Annotation to mark a method as handling HTTP POST requests.
 * <p>
 * POST requests are used to create new resources or submit data to the server. They typically
 * include request bodies with the data to be processed and can have side effects.
 * <p>
 * <strong>Path Configuration:</strong>
 * The annotation value specifies the path pattern for this endpoint, which is combined with
 * the {@link HttpEndpoint} class-level path prefix to form the complete URL.
 * <p>
 * <strong>Request Bodies:</strong>
 * POST methods commonly accept request bodies by adding a parameter that Jackson can deserialize
 * from JSON. The request body parameter must come last in the parameter list when combined
 * with path parameters.
 * <p>
 * <strong>Path Parameters:</strong>
 * Use {@code {paramName}} in the path to extract URL segments as method parameters.
 * These can be combined with request body parameters.
 * <p>
 * <strong>Response Types:</strong>
 * POST methods typically return created resources (often with 201 Created status),
 * confirmation messages, or error responses. Use {@link akka.javasdk.http.HttpResponses#created()}
 * for resource creation scenarios.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Post {
  String value() default "";
}
