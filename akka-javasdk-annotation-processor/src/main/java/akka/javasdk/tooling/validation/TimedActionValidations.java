/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Contains validation logic specific to TimedAction components. */
public class TimedActionValidations {

  /**
   * Validates a TimedAction component.
   *
   * @param element the TimedAction class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeElement element) {
    if (!Validations.extendsClass(element, "akka.javasdk.timedaction.TimedAction")) {
      return Validation.Valid.instance();
    }

    String effectType = "akka.javasdk.timedaction.TimedAction.Effect";
    return Validations.hasEffectMethod(element, effectType)
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, effectType))
        .combine(timedActionCannotHaveFunctionTools(element));
  }

  /**
   * Validates that TimedAction methods are not annotated with @FunctionTool.
   *
   * @param element the TimedAction class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation timedActionCannotHaveFunctionTools(TypeElement element) {
    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        if (Validations.findAnnotation(method, "akka.javasdk.annotations.FunctionTool") != null) {
          errors.add(
              Validations.errorMessage(
                  method, "TimedAction methods cannot be annotated with @FunctionTool."));
        }
      }
    }

    return Validation.of(errors);
  }
}
