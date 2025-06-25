/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;

import java.lang.annotation.*;

/**
 * Mark a class to be made available as an MCP server. The annotated class should be public and have a public
 * constructor.
 * <p>
 * Annotated classes can accept the following types to the constructor:
 * <ul>
 *   <li>{@link akka.javasdk.client.ComponentClient}</li>
 *   <li>{@link akka.javasdk.http.HttpClientProvider}</li>
 *   <li>{@link akka.javasdk.timer.TimerScheduler}</li>
 *   <li>{@link akka.stream.Materializer}</li>
 *   <li>{@link com.typesafe.config.Config}</li>
 *   <li>{@link io.opentelemetry.api.trace.Span}</li>
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup</li>
 * </ul>
 * <p>If the annotated class extends {@link akka.javasdk.http.AbstractHttpEndpoint} the request context
 * is available without constructor injection.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpEndpoint {
  /**
   * @return The path to publish the MCP endpoint at, defaults to "/mcp"
   */
  String path() default "/mcp";
  /**
   * @return A server name to return to clients in the initialization response
   */
  String serverName();
  /**
   * @return A server version to return to clients in the initialization response
   */
  String serverVersion() default "";
  /**
   * @return Optional instructions to return to the client in the initialization response
   */
  String instructions() default "";
}
