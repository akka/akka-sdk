/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations.mcp;

import java.lang.annotation.*;

/**
 * Marks a method that can construct an LLM prompt that clients can fetch.
 * <p>
 * The prompt method can have zero or more parameters used as prompt "arguments". All parameters must be either {@code String} for required parameters
 * or {@code Optional<String>} for optional parameters.
 * <p>
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpPrompt {
  /**
   * @return The name of the prompt or prompt template. If it is undefined, the annotated method name is used.
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
