/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;

import java.lang.annotation.*;

/**
 * Annotation to mark a class as an MCP (Model Context Protocol) server endpoint.
 * <p>
 * MCP endpoints expose services to MCP clients such as LLM chat agent desktop applications and agents
 * running on other services. They provide tools, resources, and prompts that AI models can use to
 * extend their capabilities.
 * <p>
 * <strong>Endpoint Capabilities:</strong>
 * <ul>
 *   <li><strong>Tools:</strong> Methods annotated with {@link McpTool} that clients can call</li>
 *   <li><strong>Resources:</strong> Methods annotated with {@link McpResource} that provide data</li>
 *   <li><strong>Prompts:</strong> Methods annotated with {@link McpPrompt} that generate prompts</li>
 * </ul>
 * <p>
 * <strong>Requirements:</strong>
 * The annotated class must be public and have a public constructor. It should also be annotated
 * with appropriate {@link akka.javasdk.annotations.Acl} annotations to control access.
 * <p>
 * <strong>Constructor Injection:</strong>
 * Annotated classes can accept the following types in their constructor:
 * <ul>
 *   <li>{@link akka.javasdk.client.ComponentClient} - for calling other components</li>
 *   <li>{@link akka.javasdk.http.HttpClientProvider} - for HTTP service calls</li>
 *   <li>{@link akka.javasdk.mcp.McpRequestContext} - for request context access</li>
 *   <li>{@link akka.javasdk.timer.TimerScheduler}</li>
 *   <li>{@link akka.stream.Materializer}</li>
 *   <li>{@link com.typesafe.config.Config}</li>
 *   <li>{@link io.opentelemetry.api.trace.Span}</li>
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider}</li>
 * </ul>
 * <p>
 * <strong>Request Context Access:</strong>
 * If the annotated class extends {@link akka.javasdk.mcp.AbstractMcpEndpoint}, the request context
 * is available via {@code requestContext()} without constructor injection.
 * <p>
 * <strong>Transport:</strong>
 * MCP endpoints use a stateless streamable HTTP transport as defined by the MCP specification.
 * The endpoint is served at the specified path (default: {@code /mcp}).
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
