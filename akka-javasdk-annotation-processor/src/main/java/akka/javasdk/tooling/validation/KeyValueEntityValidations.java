/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Contains validation logic specific to KeyValueEntity components. */
public class KeyValueEntityValidations {

  /**
   * Validates a KeyValueEntity component.
   *
   * @param element the KeyValueEntity class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeElement element) {
    if (!Validations.extendsClass(element, "akka.javasdk.keyvalueentity.KeyValueEntity")) {
      return Validation.Valid.instance();
    }

    String effectType = "akka.javasdk.keyvalueentity.KeyValueEntity.Effect";
    String readOnlyEffectType = "akka.javasdk.keyvalueentity.KeyValueEntity.ReadOnlyEffect";
    return keyValueEntityCommandHandlersMustBeUnique(element)
        .combine(Validations.hasEffectMethod(element, effectType))
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, effectType))
        .combine(functionToolOnlyOnValidEffectTypes(element, effectType, readOnlyEffectType));
  }

  /**
   * Validates that KeyValueEntity command handlers have unique names (no overloading).
   *
   * @param element the KeyValueEntity class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation keyValueEntityCommandHandlersMustBeUnique(TypeElement element) {
    // Collect all command handlers (methods returning KeyValueEntity.Effect)
    Map<String, List<ExecutableElement>> handlersByName = new HashMap<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        // Match both fully qualified and simple names for Effect
        if (returnTypeName.startsWith("akka.javasdk.keyvalueentity.KeyValueEntity.Effect")
            || returnTypeName.equals("Effect")) {
          String methodName = method.getSimpleName().toString();
          handlersByName.computeIfAbsent(methodName, k -> new ArrayList<>()).add(method);
        }
      }
    }

    // Find methods with duplicate names
    List<String> errors = new ArrayList<>();
    for (Map.Entry<String, List<ExecutableElement>> entry : handlersByName.entrySet()) {
      if (entry.getValue().size() > 1) {
        String methodName = entry.getKey();
        int count = entry.getValue().size();
        errors.add(
            Validations.errorMessage(
                element,
                element.getSimpleName()
                    + " has "
                    + count
                    + " command handler methods named '"
                    + methodName
                    + "'. Command handlers must have unique names."));
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that @FunctionTool is only used on methods returning Effect or ReadOnlyEffect.
   *
   * @param element the KeyValueEntity class to validate
   * @param effectType the Effect type name
   * @param readOnlyEffectType the ReadOnlyEffect type name
   * @return a Validation result indicating success or failure
   */
  private static Validation functionToolOnlyOnValidEffectTypes(
      TypeElement element, String effectType, String readOnlyEffectType) {
    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        if (Validations.findAnnotation(method, "akka.javasdk.annotations.FunctionTool") != null) {
          String returnTypeName = method.getReturnType().toString();

          // Check if the return type is Effect or ReadOnlyEffect
          boolean isValidEffectType =
              returnTypeName.equals(effectType)
                  || returnTypeName.startsWith(effectType + "<")
                  || returnTypeName.equals(readOnlyEffectType)
                  || returnTypeName.startsWith(readOnlyEffectType + "<");

          if (!isValidEffectType) {
            errors.add(
                Validations.errorMessage(
                    method,
                    "KeyValueEntity methods annotated with @FunctionTool must return Effect or"
                        + " ReadOnlyEffect."));
          }
        }
      }
    }

    return Validation.of(errors);
  }
}
