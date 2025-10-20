/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

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
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, effectType));
  }
}
