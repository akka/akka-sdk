/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import static akka.javasdk.tooling.validation.Validations.effectMethods;
import static akka.javasdk.tooling.validation.Validations.hasEffectMethod;
import static akka.javasdk.tooling.validation.Validations.strictlyPublicCommandHandlerArityShouldBeZeroOrOne;

import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import java.util.ArrayList;
import java.util.List;

/** Contains validation logic specific to request-based Agent components. */
public class AgentValidations {
  private static final String[] effectTypes = {
    "akka.javasdk.agent.Agent.Effect", "akka.javasdk.agent.Agent.StreamEffect"
  };

  /**
   * Validates a request-based Agent component. Must have exactly one command handler.
   *
   * @param typeDef the Agent class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType("akka.javasdk.agent.Agent")) {
      return Validation.Valid.instance();
    }

    return hasEffectMethod(typeDef, effectTypes)
        .combine(mustHaveSinglePublicCommandHandler(typeDef))
        .combine(strictlyPublicCommandHandlerArityShouldBeZeroOrOne(typeDef, effectTypes))
        .combine(commandHandlerCannotHaveFunctionTool(typeDef));
  }

  /**
   * Validates that an Agent has exactly one command handler.
   *
   * @param typeDef the Agent class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation mustHaveSinglePublicCommandHandler(TypeDef typeDef) {
    int count = effectMethods(typeDef, effectTypes).size();

    if (count == 1) {
      return Validation.Valid.instance();
    } else {
      return Validation.of(
          Validations.errorMessage(
              typeDef,
              typeDef.getSimpleName()
                  + " has "
                  + count
                  + " command handlers. There must be one public method returning Agent.Effect or"
                  + " Agent.StreamEffect."));
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

    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getRawQualifiedName();
      // Check if this is a command handler (returns Effect or StreamEffect)
      if (returnTypeName.equals("akka.javasdk.agent.Agent.Effect")
          || returnTypeName.equals("akka.javasdk.agent.Agent.StreamEffect")) {
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
