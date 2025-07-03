/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;


import java.lang.annotation.*;

/**
 * Annotation to expose a method as an MCP resource that clients can fetch.
 * <p>
 * MCP resources provide static or dynamic content that AI models can access to gather information.
 * Resources can be static files, configuration data, or dynamically generated content based on parameters.
 * <p>
 * <strong>Resource Types:</strong>
 * <ul>
 *   <li><strong>Static Resources:</strong> Use {@link #uri()} for fixed content with no parameters</li>
 *   <li><strong>Dynamic Resources:</strong> Use {@link #uriTemplate()} for parameterized content</li>
 * </ul>
 * <p>
 * <strong>Method Requirements:</strong>
 * <ul>
 *   <li>Must be public</li>
 *   <li>For static resources ({@code uri}): no parameters allowed</li>
 *   <li>For dynamic resources ({@code uriTemplate}): parameters must match template placeholders</li>
 *   <li>Must be in a class annotated with {@link McpEndpoint}</li>
 * </ul>
 * <p>
 * <strong>Return Types:</strong>
 * <ul>
 *   <li>{@code String}: Returns as UTF-8 encoded text resource</li>
 *   <li>{@code byte[]}: Returns as base64 encoded binary resource</li>
 *   <li>Other types: Encoded to JSON with {@code application/json} MIME type</li>
 * </ul>
 * <p>
 * <strong>URI Templates:</strong>
 * Templates can contain named placeholders for entire path segments only. Each placeholder must match
 * a method parameter name. For example: {@code "file:///images/{category}/{file}"} matches
 * {@code file:///images/bicycles/tall_bike.jpg} and passes {@code "bicycles"} as {@code category}
 * and {@code "tall_bike.jpg"} as {@code file}.
 * <p>
 * <strong>Security:</strong>
 * Always validate input parameters to prevent path traversal attacks or unauthorized access.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpResource {
  /**
   * @return A unique URI identifying the resource. If this is defined {@code uriTemplate} must be empty.
   */
  String uri() default "";

  /**
   * @return A resource template path that can be used to create a URI that will identify a resource.
   *         If defined, {@code uri} must be empty, the URI template string can contain named variable placeholders
   *         for entire segments only. Each variable name must match a method parameter name. The method parameters must be of
   *         type {@code String}. For example: {@code "file:///images/{category}/{file}"} will match a resource request for
   *         {@code file:///images/bicycles/tall_bike.jpg} and pass {@code bicycles} as the {@code category} parameter
   *         and {@code tall_bike.jpg} as the {@code file} parameter.
   */
  String uriTemplate() default "";

  /**
   * @return A human-readable name for this resource. Clients can use this to populate UI elements, for example.
   */
  String name();

  /**
   * @return A description of what this resource represents. Clients can use this to improve the LLM's
   *         understanding of available resources. It can be thought of as a "hint" to the model.
   */
  String description() default  "";

  /**
   * @return The MIME type of this resource. If not defined, String output will be presented as {@code text/plain} and
   *         byte arrays as {@code appli}
   */
  String mimeType() default "";
}
