/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.view.View;
import akka.javasdk.workflow.Workflow;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to expose methods as function tools that can be invoked by AI agents.
 *
 * <p>Methods annotated with {@code @FunctionTool} become available as tools that the AI model can
 * choose to invoke based on the task requirements. The LLM determines which tools to call and with
 * which parameters based on the tool descriptions and the user's request.
 *
 * <p><strong>Tool Discovery:</strong> Function tools can be defined in three ways:
 *
 * <ul>
 *   <li><strong>Agent-defined:</strong> Methods within the agent class itself (can be private)
 *   <li><strong>External tools:</strong> Methods in separate classes registered via {@code
 *       effects().tools()}
 *   <li><strong>Components as tools:</strong> Methods in Workflows, Event Sourced Entities, Key
 *       Value Entities, and Views classes registered via {@code effects().tools()}
 * </ul>
 *
 * <p><strong>Components as tools rules:</strong>
 *
 * <ul>
 *   <li>Only Workflows, Event Sourced Entities, Key Value Entities, and Views can be used as tools
 *   <li>For Workflows, only public methods returning {@link Workflow.Effect} or {@link
 *       Workflow.ReadOnlyEffect} can be annotated with FunctionTool. Methods returning {@link
 *       Workflow.StepEffect} are not allowed.
 *   <li>For Event Sourced Entities, only public methods returning {@link EventSourcedEntity.Effect}
 *       or {@link EventSourcedEntity.ReadOnlyEffect} can be annotated with FunctionTool.
 *   <li>For Key Value Entities, only public methods returning {@link KeyValueEntity.Effect} or
 *       {@link KeyValueEntity.ReadOnlyEffect} can be annotated with FunctionTool.
 *   <li>For Views, only public methods returning {@link View.QueryEffect} can be annotated with
 *       FunctionTool. Methods returning {@link View.QueryStreamEffect} are not allowed.
 * </ul>
 *
 * <p><strong>Best Practices:</strong>
 *
 * <ul>
 *   <li>Provide clear, descriptive tool descriptions
 *   <li>Use {@link Description} annotations on parameters to help the LLM understand usage
 *   <li>Use {@code Optional} parameters for non-required arguments
 *   <li>Keep tool functions focused on a single, well-defined task
 * </ul>
 *
 * <p><strong>Tool Execution:</strong> The agent automatically handles the tool execution loop: the
 * LLM requests tool calls, the agent executes them, incorporates results into the session context,
 * and continues until the LLM no longer needs to invoke tools.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FunctionTool {

  String name() default "";

  String description();
}
