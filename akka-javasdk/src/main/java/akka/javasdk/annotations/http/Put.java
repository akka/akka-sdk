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
 * Annotation to mark a method as handling HTTP PUT requests.
 * <p>
 * PUT requests are used to create or completely replace a resource at a specific location.
 * They should be idempotent, meaning multiple identical requests should have the same effect.
 * <p>
 * <strong>Path Configuration:</strong>
 * The annotation value specifies the path pattern for this endpoint, which is combined with
 * the {@link HttpEndpoint} class-level path prefix to form the complete URL.
 * <p>
 * <strong>Request Bodies:</strong>
 * PUT methods typically include request bodies with the complete resource representation
 * that should replace the existing resource. The request body parameter must come last
 * in the parameter list when combined with path parameters.
 * <p>
 * <strong>Path Parameters:</strong>
 * Use {@code {paramName}} in the path to identify the specific resource to create or update.
 * These can be combined with request body parameters.
 * <p>
 * <strong>Response Types:</strong>
 * PUT methods typically return the updated resource, a success confirmation, or appropriate
 * status codes (200 OK for updates, 201 Created for new resources).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Put {
  String value() default "";
}
