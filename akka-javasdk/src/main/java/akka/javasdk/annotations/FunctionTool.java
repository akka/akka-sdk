/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to expose methods as function tools that can be invoked by AI agents.
 * <p>
 * Methods annotated with {@code @FunctionTool} become available as tools that the AI model can choose
 * to invoke based on the task requirements. The LLM determines which tools to call and with which
 * parameters based on the tool descriptions and the user's request.
 * <p>
 * <strong>Tool Discovery:</strong>
 * Function tools can be defined in two ways:
 * <ul>
 *   <li><strong>Agent-defined:</strong> Methods within the agent class itself (can be private)</li>
 *   <li><strong>External tools:</strong> Methods in separate classes registered via {@code effects().tools()}</li>
 * </ul>
 * <p>
 * <strong>Example Usage:</strong>
 * <pre>{@code
 * @FunctionTool(description = "Get the current weather for a location")
 * public String getWeather(
 *     @Description("The city name") String city,
 *     @Description("Date in yyyy-MM-dd format") Optional<String> date) {
 *   // Implementation
 *   return "Sunny, 22°C";
 * }
 * }</pre>
 * <p>
 * <strong>Best Practices:</strong>
 * <ul>
 *   <li>Provide clear, descriptive tool descriptions</li>
 *   <li>Use {@link Description} annotations on parameters to help the LLM understand usage</li>
 *   <li>Use {@code Optional} parameters for non-required arguments</li>
 *   <li>Keep tool functions focused on a single, well-defined task</li>
 * </ul>
 * <p>
 * <strong>Tool Execution:</strong>
 * The agent automatically handles the tool execution loop: the LLM requests tool calls,
 * the agent executes them, incorporates results into the session context, and continues
 * until the LLM no longer needs to invoke tools.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FunctionTool {

  String name() default "";
  String description();
}
