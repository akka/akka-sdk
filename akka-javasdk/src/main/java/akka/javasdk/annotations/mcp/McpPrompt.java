/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;

import java.lang.annotation.*;

/**
 * Annotation to expose a method as an MCP prompt that clients can fetch and use.
 *
 * <p>MCP prompts provide template prompts that can be customized with input parameters. They help
 * standardize common prompting patterns and make them reusable across different AI interactions.
 *
 * <p><strong>Method Requirements:</strong>
 *
 * <ul>
 *   <li>Must be public
 *   <li>Must return a {@code String}
 *   <li>Can have zero or more parameters
 *   <li>Must be in a class annotated with {@link McpEndpoint}
 * </ul>
 *
 * <p><strong>Parameter Types:</strong> All parameters must be either {@code String} for required
 * parameters or {@code Optional<String>} for optional parameters. Use {@link
 * akka.javasdk.annotations.Description} annotations to describe the purpose of each parameter.
 *
 * <p><strong>Prompt Roles:</strong> Prompts can be designated as "user" prompts (default) or
 * "assistant" prompts using the {@link #role()} attribute. This helps clients understand how to use
 * the prompt in their conversation flow.
 *
 * <p><strong>Use Cases:</strong>
 *
 * <ul>
 *   <li>Code review templates
 *   <li>Analysis frameworks
 *   <li>Structured questioning patterns
 *   <li>Domain-specific prompt templates
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpPrompt {
  /**
   * @return The name of the prompt or prompt template. If it is undefined, the annotated method
   *     name is used.
   */
  String name() default "";

  /**
   * @return An optional description of what this prompt provides
   */
  String description() default "";

  /**
   * @return Either "user" for user prompts or "assistant". Default if undefined is "user".
   */
  String role() default "user";
}
