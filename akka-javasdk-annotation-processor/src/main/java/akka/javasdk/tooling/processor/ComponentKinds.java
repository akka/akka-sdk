/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

/** Kinds of components and endpoints, as named in the component descriptor. */
final class ComponentKinds {

  // These must be kept in sync with ComponentLocator.scala in the akka-javasdk module.
  static final String HTTP_ENDPOINT = "http-endpoint";
  static final String GRPC_ENDPOINT = "grpc-endpoint";
  static final String MCP_ENDPOINT = "mcp-endpoint";
  static final String EVENT_SOURCED_ENTITY = "event-sourced-entity";
  static final String KEY_VALUE_ENTITY = "key-value-entity";
  static final String TIMED_ACTION = "timed-action";
  static final String CONSUMER = "consumer";
  static final String VIEW = "view";
  static final String WORKFLOW = "workflow";
  static final String AGENT = "agent";
  static final String AUTONOMOUS_AGENT = "autonomous-agent";
  static final String EVALUATOR = "evaluator";
  static final String DURABLE_EVALUATOR = "durable-evaluator";
  static final String SERVICE_SETUP = "service-setup";

  private ComponentKinds() {}

  /**
   * Returns the kind of a class annotated with the given annotation.
   *
   * @throws IllegalArgumentException for an unknown annotation or component supertype
   */
  static String kindFor(TypeElement annotatedClass, TypeElement annotation) {
    return switch (annotation.getQualifiedName().toString()) {
      case "akka.javasdk.annotations.http.HttpEndpoint" -> HTTP_ENDPOINT;
      case "akka.javasdk.annotations.GrpcEndpoint" -> GRPC_ENDPOINT;
      case "akka.javasdk.annotations.Setup" -> SERVICE_SETUP;
      case "akka.javasdk.annotations.mcp.McpEndpoint" -> MCP_ENDPOINT;
      case "akka.javasdk.annotations.Component" ->
          recurseForComponentType(annotatedClass, annotatedClass);
      default ->
          throw new IllegalArgumentException(
              "Unknown annotation type: " + annotation.getQualifiedName());
    };
  }

  private static String recurseForComponentType(TypeElement annotatedClass, Element current) {
    var superClassTypeMirror = ((TypeElement) current).getSuperclass();

    if (superClassTypeMirror.getKind() == TypeKind.NONE) {
      throw new IllegalArgumentException(
          "Unknown supertype for class ["
              + annotatedClass
              + "] annotated with @Component. Reached top of hierarchy without"
              + " finding a known component supertype.");
    }

    if (superClassTypeMirror.getKind() == TypeKind.ERROR) {
      throw new IllegalArgumentException(
          "Superclass for class ["
              + current
              + "] (processing ["
              + annotatedClass
              + "]) could not be resolved. Ensure all dependencies and imports are correct.");
    }

    if (!(superClassTypeMirror instanceof DeclaredType)) {
      throw new IllegalArgumentException(
          "Unknown supertype for class ["
              + annotatedClass
              + "] annotated with @Component. Superclass ["
              + superClassTypeMirror
              + "] is not a declared type (kind: "
              + superClassTypeMirror.getKind()
              + ").");
    }

    var superClassElement = ((DeclaredType) superClassTypeMirror).asElement();

    var superClassName = superClassTypeMirror.toString();

    // cut out type params if any
    int typeParams = superClassName.indexOf("<");
    var classNameWithoutTypeParams =
        typeParams > 0 ? superClassName.substring(0, typeParams) : superClassName;

    return switch (classNameWithoutTypeParams) {
      case "akka.javasdk.eventsourcedentity.EventSourcedEntity" -> EVENT_SOURCED_ENTITY;
      case "akka.javasdk.keyvalueentity.KeyValueEntity" -> KEY_VALUE_ENTITY;
      case "akka.javasdk.workflow.Workflow" -> WORKFLOW;
      case "akka.javasdk.timedaction.TimedAction" -> TIMED_ACTION;
      case "akka.javasdk.consumer.Consumer" -> CONSUMER;
      case "akka.javasdk.view.View" -> VIEW;
      case "akka.javasdk.agent.Agent" -> AGENT;
      case "akka.javasdk.agent.autonomous.AutonomousAgent" -> AUTONOMOUS_AGENT;
      case "akka.javasdk.evaluation.Evaluator" -> EVALUATOR;
      case "akka.javasdk.evaluation.DurableEvaluator" -> DURABLE_EVALUATOR;
      case "java.lang.Object" ->
          throw new IllegalArgumentException(
              "Unknown supertype for class ["
                  + annotatedClass
                  + "] annotated with @Component: ["
                  + superClassName
                  + "]");
      default ->
          // go through hierarchy
          recurseForComponentType(annotatedClass, superClassElement);
    };
  }
}
