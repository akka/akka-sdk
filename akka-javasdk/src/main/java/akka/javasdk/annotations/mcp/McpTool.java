/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
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
 *   <li>Can return a {@code String}, an object type, an array or collection, or nothing. See the
 *       return types below.
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
 * <p><strong>Return Types:</strong>
 *
 * <ul>
 *   <li>The endpoint sends a {@code String} return value to the client as text content.
 *   <li>For an object type, such as a record, the tool listing contains an output schema generated
 *       from the fields of the type. The endpoint sends the object as structured content.
 *   <li>For an array or a collection, the output schema is an object with a single {@code result}
 *       field, and the endpoint sends the elements in the {@code result} field of the structured
 *       content.
 *   <li>For a {@code void} method, the endpoint sends the text {@code SUCCESS}.
 *   <li>For other types, such as primitives, enums and dates, the endpoint sends the JSON of the
 *       value as text content.
 *   <li>A method can return a {@code CompletionStage} of any of the types above. The endpoint sends
 *       the result when the stage completes.
 * </ul>
 *
 * <p>The endpoint also sends structured content as JSON text content, for clients that do not
 * support structured content. A {@code null} return value fails the tool call.
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
