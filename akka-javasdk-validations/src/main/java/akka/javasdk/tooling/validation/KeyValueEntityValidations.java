/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import static akka.javasdk.tooling.validation.Validations.functionToolMustNotBeOnNonPublicMethods;
import static akka.javasdk.tooling.validation.Validations.hasEffectMethod;
import static akka.javasdk.tooling.validation.Validations.strictlyPublicCommandHandlerArityShouldBeZeroOrOne;

import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Contains validation logic specific to KeyValueEntity components. */
public class KeyValueEntityValidations {

  /**
   * Validates a KeyValueEntity component.
   *
   * @param typeDef the KeyValueEntity class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType("akka.javasdk.keyvalueentity.KeyValueEntity")) {
      return Validation.Valid.instance();
    }

    String effectType = "akka.javasdk.keyvalueentity.KeyValueEntity.Effect";
    String readOnlyEffectType = "akka.javasdk.keyvalueentity.KeyValueEntity.ReadOnlyEffect";
    String[] effectTypes = {effectType, readOnlyEffectType};

    return hasEffectMethod(typeDef, effectType)
        .combine(strictlyPublicCommandHandlerArityShouldBeZeroOrOne(typeDef, effectTypes))
        .combine(commandHandlersMustHaveUniqueNames(typeDef, effectTypes))
        .combine(functionToolMustBeOnEffectMethods(typeDef))
        .combine(functionToolMustNotBeOnNonPublicMethods(typeDef));
  }

  /**
   * Validates that command handlers have unique names (no overloading).
   *
   * @param typeDef the KeyValueEntity class to validate
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
   * Validates that @FunctionTool is only used on Effect or ReadOnlyEffect methods.
   *
   * @param typeDef the KeyValueEntity class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation functionToolMustBeOnEffectMethods(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    // check on non-public
    for (MethodDef method : typeDef.getMethods()) {
      if (method.hasAnnotation("akka.javasdk.annotations.FunctionTool")) {
        boolean isEffectMethod = isEffectMethod(method);

        if (!isEffectMethod) {
          errors.add(
              Validations.errorMessage(
                  method,
                  "KeyValueEntity methods annotated with @FunctionTool must return Effect or"
                      + " ReadOnlyEffect."));
        }
      }
    }

    return Validation.of(errors);
  }

  private static boolean isEffectMethod(MethodDef method) {
    String returnTypeName = method.getReturnType().getQualifiedName();
    return returnTypeName.equals("akka.javasdk.keyvalueentity.KeyValueEntity.Effect")
        || returnTypeName.equals("akka.javasdk.keyvalueentity.KeyValueEntity.ReadOnlyEffect");
  }
}
