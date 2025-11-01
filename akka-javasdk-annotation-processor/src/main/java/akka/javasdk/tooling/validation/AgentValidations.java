/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Contains validation logic specific to Agent components. */
public class AgentValidations {

  /**
   * Validates an Agent component.
   *
   * @param element the Agent class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeElement element) {
    if (!Validations.extendsClass(element, "akka.javasdk.agent.Agent")) {
      return Validation.Valid.instance();
    }

    String[] effectTypes = {
      "akka.javasdk.agent.Agent.Effect", "akka.javasdk.agent.Agent.StreamEffect"
    };

    return mustHaveValidAgentDescription(element)
        .combine(Validations.hasEffectMethod(element, effectTypes))
        .combine(agentCommandHandlersMustBeOne(element))
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, effectTypes))
        .combine(commandHandlerCannotHaveFunctionTool(element));
  }

  /**
   * Validates that @AgentDescription is used correctly.
   *
   * @param element the Agent class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation mustHaveValidAgentDescription(TypeElement element) {
    AnnotationMirror agentDescAnn =
        Validations.findAnnotation(element, "akka.javasdk.annotations.AgentDescription");

    if (agentDescAnn == null) {
      return Validation.Valid.instance();
    }

    List<String> errors = new ArrayList<>();

    AnnotationMirror componentAnn =
        Validations.findAnnotation(element, "akka.javasdk.annotations.Component");
    AnnotationMirror agentRoleAnn =
        Validations.findAnnotation(element, "akka.javasdk.annotations.AgentRole");

    if (componentAnn != null) {
      String componentName = Validations.getAnnotationValue(componentAnn, "name");
      String componentDesc = Validations.getAnnotationValue(componentAnn, "description");

      if (componentName != null && !componentName.isEmpty()) {
        errors.add(
            Validations.errorMessage(
                element,
                "Both @AgentDescription.name and @Component.name are defined. "
                    + "Remove @AgentDescription.name and use only @Component.name."));
      }

      if (componentDesc != null && !componentDesc.isEmpty()) {
        errors.add(
            Validations.errorMessage(
                element,
                "Both @AgentDescription.description and @Component.description are defined. "
                    + "Remove @AgentDescription.description and use only @Component.description."));
      }
    } else {
      String agentDescName = Validations.getAnnotationValue(agentDescAnn, "name");
      String agentDescDescription = Validations.getAnnotationValue(agentDescAnn, "description");

      if (agentDescName == null || agentDescName.isBlank()) {
        errors.add(
            Validations.errorMessage(
                element,
                "@AgentDescription.name is empty. "
                    + "Remove @AgentDescription annotation and use only @Component."));
      }

      if (agentDescDescription == null || agentDescDescription.isBlank()) {
        errors.add(
            Validations.errorMessage(
                element,
                "@AgentDescription.description is empty."
                    + "Remove @AgentDescription annotation and use only @Component."));
      }
    }

    if (agentRoleAnn != null) {
      String agentDescRole = Validations.getAnnotationValue(agentDescAnn, "role");
      if (agentDescRole != null && !agentDescRole.isBlank()) {
        errors.add(
            Validations.errorMessage(
                element,
                "Both @AgentDescription.role and @AgentRole are defined. "
                    + "Remove @AgentDescription.role and use only @AgentRole."));
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that an Agent has exactly one command handler.
   *
   * @param element the Agent class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation agentCommandHandlersMustBeOne(TypeElement element) {
    int count = 0;

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if (returnTypeName.startsWith("akka.javasdk.agent.Agent.Effect")
            || returnTypeName.startsWith("akka.javasdk.agent.Agent.StreamEffect")) {
          count++;
        }
      }
    }

    if (count == 1) {
      return Validation.Valid.instance();
    } else {
      return Validation.of(
          Validations.errorMessage(
              element,
              element.getSimpleName()
                  + " has "
                  + count
                  + " command handlers. There must be one public method returning Agent.Effect."));
    }
  }

  /**
   * Validates that the Agent command handler method is not annotated with @FunctionTool.
   *
   * @param element the Agent class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation commandHandlerCannotHaveFunctionTool(TypeElement element) {
    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        // Check if this is a command handler (returns Effect or StreamEffect)
        if (returnTypeName.startsWith("akka.javasdk.agent.Agent.Effect")
            || returnTypeName.startsWith("akka.javasdk.agent.Agent.StreamEffect")) {
          // Check if it has @FunctionTool annotation
          if (Validations.findAnnotation(method, "akka.javasdk.annotations.FunctionTool") != null) {
            errors.add(
                Validations.errorMessage(
                    method,
                    "Agent command handler methods cannot be annotated with @FunctionTool. "
                        + "Only non-command handler methods can be annotated with @FunctionTool."));
          }
        }
      }
    }

    return Validation.of(errors);
  }
}
