/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Contains validation logic specific to Agent components. */
public class AgentValidations {

  /**
   * Validates an Agent component.
   *
   * @param typeDef the Agent class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType("akka.javasdk.agent.Agent")) {
      return Validation.Valid.instance();
    }

    String[] effectTypes = {
      "akka.javasdk.agent.Agent.Effect", "akka.javasdk.agent.Agent.StreamEffect"
    };

    return mustHaveValidAgentDescription(typeDef)
        .combine(Validations.hasEffectMethod(typeDef, effectTypes))
        .combine(agentCommandHandlersMustBeOne(typeDef))
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(typeDef, effectTypes))
        .combine(commandHandlerCannotHaveFunctionTool(typeDef));
  }

  /**
   * Validates that @AgentDescription is used correctly.
   *
   * @param typeDef the Agent class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation mustHaveValidAgentDescription(TypeDef typeDef) {
    Optional<AnnotationDef> agentDescAnn =
        typeDef.findAnnotation("akka.javasdk.annotations.AgentDescription");

    if (agentDescAnn.isEmpty()) {
      return Validation.Valid.instance();
    }

    AnnotationDef agentDesc = agentDescAnn.get();
    List<String> errors = new ArrayList<>();

    Optional<AnnotationDef> componentAnn =
        typeDef.findAnnotation("akka.javasdk.annotations.Component");
    Optional<AnnotationDef> agentRoleAnn =
        typeDef.findAnnotation("akka.javasdk.annotations.AgentRole");

    if (componentAnn.isPresent()) {
      AnnotationDef component = componentAnn.get();
      Optional<String> componentName = component.getStringValue("name");
      Optional<String> componentDesc = component.getStringValue("description");

      if (componentName.isPresent() && !componentName.get().isEmpty()) {
        errors.add(
            Validations.errorMessage(
                typeDef,
                "Both @AgentDescription.name and @Component.name are defined. "
                    + "Remove @AgentDescription.name and use only @Component.name."));
      }

      if (componentDesc.isPresent() && !componentDesc.get().isEmpty()) {
        errors.add(
            Validations.errorMessage(
                typeDef,
                "Both @AgentDescription.description and @Component.description are defined. "
                    + "Remove @AgentDescription.description and use only @Component.description."));
      }
    } else {
      Optional<String> agentDescName = agentDesc.getStringValue("name");
      Optional<String> agentDescDescription = agentDesc.getStringValue("description");

      if (agentDescName.isEmpty() || agentDescName.get().isBlank()) {
        errors.add(
            Validations.errorMessage(
                typeDef,
                "@AgentDescription.name is empty. "
                    + "Remove @AgentDescription annotation and use only @Component."));
      }

      if (agentDescDescription.isEmpty() || agentDescDescription.get().isBlank()) {
        errors.add(
            Validations.errorMessage(
                typeDef,
                "@AgentDescription.description is empty."
                    + "Remove @AgentDescription annotation and use only @Component."));
      }
    }

    if (agentRoleAnn.isPresent()) {
      Optional<String> agentDescRole = agentDesc.getStringValue("role");
      if (agentDescRole.isPresent() && !agentDescRole.get().isBlank()) {
        errors.add(
            Validations.errorMessage(
                typeDef,
                "Both @AgentDescription.role and @AgentRole are defined. "
                    + "Remove @AgentDescription.role and use only @AgentRole."));
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that an Agent has exactly one command handler.
   *
   * @param typeDef the Agent class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation agentCommandHandlersMustBeOne(TypeDef typeDef) {
    int count = 0;

    for (MethodDef method : typeDef.getMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.startsWith("akka.javasdk.agent.Agent.Effect")
          || returnTypeName.startsWith("akka.javasdk.agent.Agent.StreamEffect")) {
        count++;
      }
    }

    if (count == 1) {
      return Validation.Valid.instance();
    } else {
      return Validation.of(
          Validations.errorMessage(
              typeDef,
              typeDef.getSimpleName()
                  + " has "
                  + count
                  + " command handlers. There must be one public method returning Agent.Effect."));
    }
  }

  /**
   * Validates that the Agent command handler method is not annotated with @FunctionTool.
   *
   * @param typeDef the Agent class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation commandHandlerCannotHaveFunctionTool(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      // Check if this is a command handler (returns Effect or StreamEffect)
      if (returnTypeName.startsWith("akka.javasdk.agent.Agent.Effect")
          || returnTypeName.startsWith("akka.javasdk.agent.Agent.StreamEffect")) {
        // Check if it has @FunctionTool annotation
        if (method.hasAnnotation("akka.javasdk.annotations.FunctionTool")) {
          errors.add(
              Validations.errorMessage(
                  method,
                  "Agent command handler methods cannot be annotated with @FunctionTool. "
                      + "Only non-command handler methods can be annotated with @FunctionTool."));
        }
      }
    }

    return Validation.of(errors);
  }
}
