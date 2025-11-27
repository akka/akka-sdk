/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import static akka.javasdk.tooling.validation.Validations.hasEffectMethod;
import static akka.javasdk.tooling.validation.Validations.strictlyPublicCommandHandlerArityShouldBeZeroOrOne;

import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import java.util.ArrayList;
import java.util.List;

/** Contains validation logic specific to TimedAction components. */
public class TimedActionValidations {

  /**
   * Validates a TimedAction component.
   *
   * @param typeDef the TimedAction class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType("akka.javasdk.timedaction.TimedAction")) {
      return Validation.Valid.instance();
    }

    String effectType = "akka.javasdk.timedaction.TimedAction.Effect";
    return hasEffectMethod(typeDef, effectType)
        .combine(strictlyPublicCommandHandlerArityShouldBeZeroOrOne(typeDef, effectType))
        .combine(timedActionCannotHaveFunctionTools(typeDef));
  }

  /**
   * Validates that TimedAction methods are not annotated with @FunctionTool.
   *
   * @param typeDef the TimedAction class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation timedActionCannotHaveFunctionTools(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getMethods()) {
      if (method.hasAnnotation("akka.javasdk.annotations.FunctionTool")) {
        errors.add(
            Validations.errorMessage(
                method, "TimedAction methods cannot be annotated with @FunctionTool."));
      }
    }

    return Validation.of(errors);
  }
}
