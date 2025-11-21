/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import static akka.javasdk.tooling.validation.Validations.functionToolMustNotBeOnNonPublicMethods;
import static akka.javasdk.tooling.validation.Validations.hasEffectMethod;
import static akka.javasdk.tooling.validation.Validations.strictlyPublicCommandHandlerArityShouldBeZeroOrOne;

import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Contains validation logic specific to EventSourcedEntity components. */
public class EventSourcedEntityValidations {

  /**
   * Validates an EventSourcedEntity component.
   *
   * @param typeDef the EventSourcedEntity class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType("akka.javasdk.eventsourcedentity.EventSourcedEntity")) {
      return Validation.Valid.instance();
    }

    String effectType = "akka.javasdk.eventsourcedentity.EventSourcedEntity.Effect";
    String readOnlyEffectType = "akka.javasdk.eventsourcedentity.EventSourcedEntity.ReadOnlyEffect";
    String[] effectTypes = {effectType, readOnlyEffectType};

    return hasEffectMethod(typeDef, effectType)
        .combine(strictlyPublicCommandHandlerArityShouldBeZeroOrOne(typeDef, effectTypes))
        .combine(commandHandlersMustHaveUniqueNames(typeDef, effectTypes))
        .combine(eventTypeMustBeSealed(typeDef))
        .combine(functionToolMustBeOnEffectMethods(typeDef))
        .combine(functionToolMustNotBeOnNonPublicMethods(typeDef));
  }

  /**
   * Validates that command handlers have unique names (no overloading).
   *
   * @param typeDef the EventSourcedEntity class to validate
   * @param effectTypeNames the effect type names
   * @return a Validation result indicating success or failure
   */
  private static Validation commandHandlersMustHaveUniqueNames(
      TypeDef typeDef, String... effectTypeNames) {
    Map<String, Integer> methodNameCounts = new HashMap<>();
    List<String> errors = new ArrayList<>();

    // Count occurrences of each method name for Effect-returning methods
    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      for (String effectTypeName : effectTypeNames) {
        if (returnTypeName.startsWith(effectTypeName)) {
          String methodName = method.getName();
          methodNameCounts.merge(methodName, 1, Integer::sum);
          break;
        }
      }
    }

    // Check for duplicates
    for (Map.Entry<String, Integer> entry : methodNameCounts.entrySet()) {
      if (entry.getValue() > 1) {
        errors.add(
            Validations.errorMessage(
                typeDef,
                typeDef.getSimpleName()
                    + " has "
                    + entry.getValue()
                    + " command handler methods named '"
                    + entry.getKey()
                    + "'. Command handlers must have unique names."));
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that the Event type parameter is sealed.
   *
   * @param typeDef the EventSourcedEntity class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation eventTypeMustBeSealed(TypeDef typeDef) {
    // Extract the event type from EventSourcedEntity<State, Event> (second type argument)
    List<TypeRefDef> typeArgs = typeDef.getSuperclassTypeArguments();
    if (typeArgs.size() < 2) {
      return Validation.Valid.instance();
    }

    TypeRefDef eventTypeRef = typeArgs.get(1);
    Optional<TypeDef> eventTypeDef = eventTypeRef.resolveTypeDef();

    if (eventTypeDef.isEmpty()) {
      return Validation.Valid.instance();
    }

    if (!eventTypeDef.get().isSealed()) {
      return Validation.of(
          Validations.errorMessage(
              typeDef,
              "The event type of an EventSourcedEntity is required to be a sealed interface."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that @FunctionTool is only used on Effect or ReadOnlyEffect methods.
   *
   * @param typeDef the EventSourcedEntity class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation functionToolMustBeOnEffectMethods(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getPublicMethods()) {
      if (method.hasAnnotation("akka.javasdk.annotations.FunctionTool")) {
        String returnTypeName = method.getReturnType().getQualifiedName();
        boolean isEffectMethod =
            returnTypeName.equals("akka.javasdk.eventsourcedentity.EventSourcedEntity.Effect")
                || returnTypeName.equals(
                    "akka.javasdk.eventsourcedentity.EventSourcedEntity.ReadOnlyEffect");

        if (!isEffectMethod) {
          errors.add(
              Validations.errorMessage(
                  method,
                  "EventSourcedEntity methods annotated with @FunctionTool must return Effect or"
                      + " ReadOnlyEffect."));
        }
      }
    }

    return Validation.of(errors);
  }
}
