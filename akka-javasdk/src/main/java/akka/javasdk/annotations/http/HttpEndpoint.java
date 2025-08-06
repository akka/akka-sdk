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
 * Annotation to mark a class as an HTTP endpoint that exposes RESTful APIs.
 *
 * <p>HTTP endpoints handle incoming HTTP requests and can return responses in various formats
 * including JSON, plain text, or custom content types. They provide the external API for your
 * service.
 *
 * <p><strong>Basic Structure:</strong> The annotated class should be public with a public
 * constructor. Methods annotated with HTTP verb annotations ({@link Get}, {@link Post}, {@link
 * Put}, {@link Patch}, {@link Delete}) handle requests.
 *
 * <p><strong>Path Configuration:</strong> The annotation value specifies a common path prefix for
 * all methods in the class. Individual method paths are combined with this prefix to form the
 * complete endpoint URL.
 *
 * <p><strong>Method Features:</strong>
 *
 * <ul>
 *   <li><strong>Path Parameters:</strong> Use {@code {paramName}} in paths to extract URL segments
 *   <li><strong>Request Bodies:</strong> Accept JSON by adding parameters Jackson can deserialize
 *   <li><strong>Query Parameters:</strong> Access via {@code RequestContext.queryParams()}
 *   <li><strong>Headers:</strong> Access via {@code RequestContext.requestHeader()}
 * </ul>
 *
 * <p><strong>Response Types:</strong>
 *
 * <ul>
 *   <li>String - text/plain responses
 *   <li>Objects - JSON responses (serialized with Jackson)
 *   <li>{@link akka.http.javadsl.model.HttpResponse} - full control over response
 *   <li>{@code CompletionStage<T>} - asynchronous responses
 * </ul>
 *
 * <p><strong>Constructor Injection:</strong> Annotated classes can accept the following types in
 * their constructor:
 *
 * <ul>
 *   <li>{@link akka.javasdk.client.ComponentClient} - for calling other components
 *   <li>{@link akka.javasdk.http.HttpClientProvider} - for HTTP service calls
 *   <li>{@link akka.javasdk.http.RequestContext} - for request context access
 *   <li>{@link akka.javasdk.timer.TimerScheduler}
 *   <li>{@link akka.stream.Materializer}
 *   <li>{@link com.typesafe.config.Config}
 *   <li>{@link io.opentelemetry.api.trace.Span}
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider}
 * </ul>
 *
 * <p><strong>Request Context Access:</strong> If the annotated class extends {@link
 * akka.javasdk.http.AbstractHttpEndpoint}, the request context is available via {@code
 * requestContext()} without constructor injection.
 *
 * <p><strong>Security:</strong> Always annotate endpoints with appropriate {@link
 * akka.javasdk.annotations.Acl} annotations to control access. Without ACL annotations, no clients
 * are allowed to access the endpoint.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HttpEndpoint {
  String value() default "";
}
