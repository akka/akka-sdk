/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;

import java.lang.annotation.*;

/**
 * Annotation to expose a method as an MCP tool that can be called by MCP clients.
 *
 * <p>MCP tools are functions that AI models can invoke to perform specific tasks or retrieve
 * information. The LLM determines which tools to call based on the tool descriptions and the user's
 * request.
 *
 * <p><strong>Method Requirements:</strong>
 *
 * <ul>
 *   <li>Must be public
 *   <li>Must return a {@code String}
 *   <li>Can accept 0 or more parameters
 *   <li>Must be in a class annotated with {@link McpEndpoint}
 * </ul>
 *
 * <p><strong>Parameter Types:</strong> Only simple parameter types are supported. Fields must be
 * primitive types, boxed Java primitives, or strings. All parameters are required by default; use
 * {@code Optional<T>} for optional parameters.
 *
 * <p><strong>Schema Generation:</strong> The input schema is automatically generated from method
 * parameters unless a manual schema is provided via {@link #inputSchema()}. Use {@link
 * akka.javasdk.annotations.Description} annotations on parameters to help the LLM understand their
 * purpose.
 *
 * <p><strong>Best Practices:</strong>
 *
 * <ul>
 *   <li>Provide clear, descriptive tool descriptions
 *   <li>Use {@link akka.javasdk.annotations.Description} on all parameters
 *   <li>Keep tools focused on single, well-defined tasks
 *   <li>Validate input parameters for security
 *   <li>Use {@link ToolAnnotation} to describe tool behavior characteristics
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

  /**
   * @return The name of the tool. Must be unique in the same MCP endpoint if specified, if not
   *     specified, the method name is used.
   */
  String name() default "";

  /**
   * @return A clear description of what the tools, used by the client LLM to determine what the
   *     tool can be used for
   */
  String description();

  /**
   * Normally, the schema is inferred from the input parameters of the tool method.
   *
   * @return A manually specified schema instead of the automatic must match the input parameters
   *     and how they are parsed by Jackson.
   */
  String inputSchema() default "";

  /** Optional annotations describing what the tool does to the client. */
  ToolAnnotation[] annotations() default {};
}
