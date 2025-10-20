/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import javax.lang.model.element.TypeElement;

/** Contains validation logic specific to Workflow components. */
public class WorkflowValidations {

  /**
   * Validates a Workflow component.
   *
   * @param element the Workflow class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeElement element) {
    if (!Validations.extendsClass(element, "akka.javasdk.workflow.Workflow")) {
      return Validation.Valid.instance();
    }

    String effectType = "akka.javasdk.workflow.Workflow.Effect";
    String readOnlyEffectType = "akka.javasdk.workflow.Workflow.ReadOnlyEffect";
    String stepEffectType = "akka.javasdk.workflow.Workflow.StepEffect";
    return Validation.Valid.instance()
        .combine(Validations.hasEffectMethod(element, effectType))
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, effectType))
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, readOnlyEffectType))
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, stepEffectType));
  }
}
