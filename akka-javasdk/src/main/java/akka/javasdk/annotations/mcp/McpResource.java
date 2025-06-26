/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;


import java.lang.annotation.*;

/**
 * Marks a method that returns an MCP resource that clients can fetch.
 * <p>
 * If {@code uri} is defined the annotated method must have no parameters.
 * If {@code uriTemplate} is defined the method must take one string parameter, which will be fed the suffix of the template.
 * <p>
 * If the method returns {@code String} an MCP text resource is returned (implicitly UTF-8 encoded).
 * If the method returns {@code byte[]} a binary resource is returned, with the payload base64 encoded.
 * For all other returned types, the value is encoded to JSON using jackson and is returned as a text resource with
 * default mime type {@code application/json}.
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
