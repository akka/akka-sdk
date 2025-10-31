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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/** Contains validation logic specific to EventSourcedEntity components. */
public class EventSourcedEntityValidations {

  /**
   * Validates an EventSourcedEntity component.
   *
   * @param element the EventSourcedEntity class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeElement element) {
    if (!Validations.extendsClass(element, "akka.javasdk.eventsourcedentity.EventSourcedEntity")) {
      return Validation.Valid.instance();
    }

    String effectType = "akka.javasdk.eventsourcedentity.EventSourcedEntity.Effect";
    String readOnlyEffectType = "akka.javasdk.eventsourcedentity.EventSourcedEntity.ReadOnlyEffect";
    return eventSourcedEntityEventMustBeSealed(element)
        .combine(Validations.hasEffectMethod(element, effectType))
        .combine(eventSourcedCommandHandlersMustBeUnique(element))
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, effectType))
        .combine(functionToolOnlyOnValidEffectTypes(element, effectType, readOnlyEffectType));
  }

  /**
   * Validates that the event type of an EventSourcedEntity is sealed.
   *
   * @param element the EventSourcedEntity class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation eventSourcedEntityEventMustBeSealed(TypeElement element) {
    // Get the event type from the generic parameter of EventSourcedEntity
    TypeMirror superclass = element.getSuperclass();
    if (superclass instanceof DeclaredType declaredType) {
      if (!declaredType.getTypeArguments().isEmpty()) {
        // EventSourcedEntity has two type parameters: State and Event
        // Event is the second one (index 1)
        if (declaredType.getTypeArguments().size() >= 2) {
          TypeMirror eventType = declaredType.getTypeArguments().get(1);
          if (eventType instanceof DeclaredType eventDeclaredType) {
            Element eventElement = eventDeclaredType.asElement();
            if (eventElement instanceof TypeElement eventTypeElement) {
              // Check if the event type is sealed
              if (!eventTypeElement.getModifiers().contains(Modifier.SEALED)) {
                return Validation.of(
                    "The event type of an EventSourcedEntity is required to be a sealed interface."
                        + " Event '"
                        + eventTypeElement.getQualifiedName()
                        + "' in '"
                        + element.getQualifiedName()
                        + "' is not sealed.");
              }
            }
          }
        }
      }
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that EventSourcedEntity command handlers have unique names (no overloading).
   *
   * @param element the EventSourcedEntity class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation eventSourcedCommandHandlersMustBeUnique(TypeElement element) {
    // Collect all command handlers (methods returning EventSourcedEntity.Effect)
    Map<String, List<ExecutableElement>> handlersByName = new HashMap<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        // Match both fully qualified and simple names for Effect
        if (returnTypeName.startsWith("akka.javasdk.eventsourcedentity.EventSourcedEntity.Effect")
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
   * @param element the EventSourcedEntity class to validate
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
                    "EventSourcedEntity methods annotated with @FunctionTool must return Effect or"
                        + " ReadOnlyEffect."));
          }
        }
      }
    }

    return Validation.of(errors);
  }
}
