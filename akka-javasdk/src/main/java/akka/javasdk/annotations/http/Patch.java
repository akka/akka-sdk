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
 * Annotation to mark a method as handling HTTP PATCH requests.
 * <p>
 * PATCH requests are used to apply partial updates to existing resources. Unlike PUT requests
 * that replace the entire resource, PATCH only modifies the specified fields while leaving
 * other fields unchanged.
 * <p>
 * <strong>Path Configuration:</strong>
 * The annotation value specifies the path pattern for this endpoint, which is combined with
 * the {@link HttpEndpoint} class-level path prefix to form the complete URL.
 * <p>
 * <strong>Request Bodies:</strong>
 * PATCH methods typically include request bodies with the partial data to be updated.
 * The request body parameter must come last in the parameter list when combined with
 * path parameters.
 * <p>
 * <strong>Path Parameters:</strong>
 * Use {@code {paramName}} in the path to identify the specific resource to update.
 * These can be combined with request body parameters.
 * <p>
 * <strong>Response Types:</strong>
 * PATCH methods typically return the updated resource, a success confirmation, or 
 * 200 OK responses. Use {@link akka.javasdk.http.HttpResponses#ok()} for standard update responses.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Patch {
  String value() default "";
}
