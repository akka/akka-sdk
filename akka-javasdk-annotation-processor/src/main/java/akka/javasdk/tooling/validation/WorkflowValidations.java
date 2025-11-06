/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
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
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, stepEffectType))
        .combine(functionToolOnlyOnValidEffectTypes(element));
  }

  /**
   * Validates that @FunctionTool is only used on methods returning Effect or ReadOnlyEffect, not
   * StepEffect.
   *
   * @param element the Workflow class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation functionToolOnlyOnValidEffectTypes(TypeElement element) {
    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        if (Validations.findAnnotation(method, "akka.javasdk.annotations.FunctionTool") != null) {
          String returnTypeName = method.getReturnType().toString();

          // Check if it's a StepEffect (not allowed)
          if (returnTypeName.equals("akka.javasdk.workflow.Workflow.StepEffect")
              || returnTypeName.startsWith("akka.javasdk.workflow.Workflow.StepEffect<")) {
            errors.add(
                Validations.errorMessage(
                    method,
                    "Workflow methods annotated with @FunctionTool cannot return StepEffect. Only"
                        + " methods returning Effect or ReadOnlyEffect can be annotated with"
                        + " @FunctionTool."));
          }
        }
      }
    }

    return Validation.of(errors);
  }
}
