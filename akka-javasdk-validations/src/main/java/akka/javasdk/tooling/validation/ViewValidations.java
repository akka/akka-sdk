/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Contains validation logic specific to View components. */
public class ViewValidations {

  /**
   * Validates a View component.
   *
   * @param typeDef the View class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType("akka.javasdk.view.View")) {
      return Validation.Valid.instance();
    }

    // Get all TableUpdater nested classes
    List<TypeDef> tableUpdaters =
        typeDef.getNestedTypes().stream().filter(ViewValidations::isViewTableUpdater).toList();

    // Validate View-level rules
    Validation viewValidation =
        viewMustNotHaveTableAnnotation(typeDef)
            .combine(viewMustHaveAtLeastOneViewTableUpdater(typeDef))
            .combine(viewMustHaveAtLeastOneQueryMethod(typeDef))
            .combine(validateQueryResultTypes(typeDef))
            .combine(viewQueriesWithStreamUpdatesMustBeStreaming(typeDef))
            .combine(viewQueryMethodArityShouldBeZeroOrOne(typeDef))
            .combine(viewMultipleTableUpdatersMustHaveTableAnnotations(typeDef))
            .combine(functionToolOnlyOnQueryEffect(typeDef))
            .combine(Validations.functionToolMustNotBeOnPrivateMethods(typeDef));

    // Validate each TableUpdater
    for (TypeDef updater : tableUpdaters) {
      viewValidation =
          viewValidation
              .combine(validateViewTableUpdater(updater))
              .combine(viewTableAnnotationMustNotBeEmptyString(updater));
    }

    return viewValidation;
  }

  /**
   * Validates that a View itself is not annotated with @Table.
   *
   * @param typeDef the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMustNotHaveTableAnnotation(TypeDef typeDef) {
    if (typeDef.hasAnnotation("akka.javasdk.annotations.Table")) {
      return Validation.of(
          Validations.errorMessage(typeDef, "A View itself should not be annotated with @Table."));
    }
    return Validation.Valid.instance();
  }

  /**
   * Validates that a View has at least one TableUpdater nested class.
   *
   * @param typeDef the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMustHaveAtLeastOneViewTableUpdater(TypeDef typeDef) {
    long tableUpdaterCount =
        typeDef.getNestedTypes().stream().filter(ViewValidations::isViewTableUpdater).count();

    if (tableUpdaterCount < 1) {
      return Validation.of(
          Validations.errorMessage(
              typeDef, "A view must contain at least one public static TableUpdater subclass."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Checks if a nested class is a View TableUpdater.
   *
   * @param typeDef the nested class to check
   * @return true if it extends View.TableUpdater
   */
  private static boolean isViewTableUpdater(TypeDef typeDef) {
    if (!typeDef.isPublic() || !typeDef.isStatic()) {
      return false;
    }
    return typeDef.extendsType("akka.javasdk.view.TableUpdater");
  }

  /**
   * Validates that a View has at least one method annotated with @Query.
   *
   * @param typeDef the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMustHaveAtLeastOneQueryMethod(TypeDef typeDef) {
    boolean hasAtLeastOneQuery =
        typeDef.getPublicMethods().stream()
            .anyMatch(method -> method.hasAnnotation("akka.javasdk.annotations.Query"));

    if (!hasAtLeastOneQuery) {
      return Validation.of(
          Validations.errorMessage(
              typeDef,
              "No valid query method found. Views should have at least one method annotated with"
                  + " @Query."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates query result types - must return QueryEffect or QueryStreamEffect, and result type
   * cannot be a primitive wrapper.
   *
   * @param typeDef the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation validateQueryResultTypes(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getPublicMethods()) {
      if (method.hasAnnotation("akka.javasdk.annotations.Query")) {
        String returnTypeName = method.getReturnType().getQualifiedName();

        boolean isQueryEffect = returnTypeName.startsWith("akka.javasdk.view.View.QueryEffect");
        boolean isQueryStreamEffect =
            returnTypeName.startsWith("akka.javasdk.view.View.QueryStreamEffect");

        if (!isQueryEffect && !isQueryStreamEffect) {
          errors.add(
              Validations.errorMessage(
                  method,
                  "Query methods must return View.QueryEffect<RowType> or"
                      + " View.QueryStreamEffect<RowType> (was "
                      + returnTypeName
                      + ")."));
        } else {
          // Check if result type is a primitive wrapper
          List<TypeRefDef> typeArgs = method.getReturnType().getTypeArguments();
          if (!typeArgs.isEmpty()) {
            TypeRefDef resultType = typeArgs.getFirst();
            if (isPrimitiveWrapper(resultType)) {
              errors.add(
                  Validations.errorMessage(
                      method,
                      "View query result type "
                          + resultType.getQualifiedName()
                          + " is not supported"));
            }
          }
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Checks if a TypeRefDef represents a primitive wrapper type.
   *
   * @param typeRef the type to check
   * @return true if it's a primitive wrapper
   */
  private static boolean isPrimitiveWrapper(TypeRefDef typeRef) {
    String typeName = typeRef.getQualifiedName();
    return typeName.equals("java.lang.Integer")
        || typeName.equals("java.lang.Long")
        || typeName.equals("java.lang.Float")
        || typeName.equals("java.lang.Double")
        || typeName.equals("java.lang.Byte")
        || typeName.equals("java.lang.Short")
        || typeName.equals("java.lang.Boolean")
        || typeName.equals("java.lang.Character")
        || typeName.equals("java.lang.String");
  }

  /**
   * Validates that queries marked with streamUpdates return QueryStreamEffect.
   *
   * @param typeDef the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewQueriesWithStreamUpdatesMustBeStreaming(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getPublicMethods()) {
      Optional<AnnotationDef> queryAnn = method.findAnnotation("akka.javasdk.annotations.Query");
      if (queryAnn.isPresent()) {
        Optional<Boolean> streamUpdates = queryAnn.get().getBooleanValue("streamUpdates");
        if (streamUpdates.isPresent() && streamUpdates.get()) {
          String returnTypeName = method.getReturnType().getQualifiedName();
          if (!returnTypeName.startsWith("akka.javasdk.view.View.QueryStreamEffect")) {
            errors.add(
                Validations.errorMessage(
                    method,
                    "Query methods marked with streamUpdates must return"
                        + " View.QueryStreamEffect<RowType>"));
          }
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that View query methods have zero or one parameter.
   *
   * @param typeDef the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewQueryMethodArityShouldBeZeroOrOne(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getPublicMethods()) {
      if (method.hasAnnotation("akka.javasdk.annotations.Query")) {
        int paramCount = method.getParameters().size();
        if (paramCount > 1) {
          errors.add(
              Validations.errorMessage(
                  typeDef,
                  "Method ["
                      + method.getName()
                      + "] must have zero or one argument. If you need to pass more arguments,"
                      + " wrap them in a class."));
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that when there are multiple TableUpdaters, each must have @Table annotation.
   *
   * @param typeDef the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMultipleTableUpdatersMustHaveTableAnnotations(TypeDef typeDef) {
    List<TypeDef> tableUpdaters =
        typeDef.getNestedTypes().stream().filter(ViewValidations::isViewTableUpdater).toList();

    if (tableUpdaters.size() > 1) {
      for (TypeDef tableUpdater : tableUpdaters) {
        if (!tableUpdater.hasAnnotation("akka.javasdk.annotations.Table")) {
          return Validation.of(
              Validations.errorMessage(
                  typeDef,
                  "When there are multiple table updater, each must be annotated with @Table."));
        }
      }
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates a TableUpdater nested class.
   *
   * @param tableUpdater the TableUpdater class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation validateViewTableUpdater(TypeDef tableUpdater) {
    String effectType = "akka.javasdk.view.TableUpdater.Effect";
    return viewTableUpdaterMustHaveConsumeAnnotation(tableUpdater)
        .combine(validateViewUpdaterRowType(tableUpdater))
        .combine(viewDeleteHandlerValidation(tableUpdater))
        .combine(viewCommonStateSubscriptionValidation(tableUpdater))
        .combine(viewMustHaveCorrectUpdateHandlerWhenTransformingViewUpdates(tableUpdater))
        .combine(Validations.ambiguousHandlerValidations(tableUpdater, effectType))
        .combine(
            Validations.strictlyPublicCommandHandlerArityShouldBeZeroOrOne(
                tableUpdater, effectType))
        .combine(viewMissingHandlerValidations(tableUpdater, effectType))
        .combine(Validations.noSubscriptionMethodWithAcl(tableUpdater, effectType))
        .combine(Validations.subscriptionMethodMustHaveOneParameter(tableUpdater, effectType));
  }

  /**
   * Validates that TableUpdaters with type-level subscriptions have correct update handlers when
   * transforming types.
   *
   * <p>When a TableUpdater subscribes to a KeyValueEntity or Workflow at the type level, and the
   * state type differs from the table row type, it must provide an update handler method that
   * returns Effect with the table type.
   *
   * @param tableUpdater the TableUpdater class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMustHaveCorrectUpdateHandlerWhenTransformingViewUpdates(
      TypeDef tableUpdater) {

    // Check for KeyValueEntity subscription
    if (Validations.hasKeyValueEntitySubscription(tableUpdater)) {
      Optional<TypeRefDef> tableType = extractTableTypeFromTableUpdater(tableUpdater);
      if (tableType.isEmpty()) {
        return Validation.Valid.instance();
      }

      Optional<AnnotationDef> subscriptionAnnotation =
          tableUpdater.findAnnotation("akka.javasdk.annotations.Consume.FromKeyValueEntity");
      if (subscriptionAnnotation.isEmpty()) {
        return Validation.Valid.instance();
      }

      Optional<TypeRefDef> entityClassRef = subscriptionAnnotation.get().getClassValue("value");
      if (entityClassRef.isEmpty()) {
        return Validation.Valid.instance();
      }

      Optional<TypeRefDef> stateType = extractStateTypeFromKeyValueEntity(entityClassRef.get());
      if (stateType.isEmpty()) {
        return Validation.Valid.instance();
      }

      return validateTableUpdaterTransformation(tableUpdater, tableType.get(), stateType.get());

    } else if (Validations.hasWorkflowSubscription(tableUpdater)) {
      // Check for Workflow subscription
      Optional<TypeRefDef> tableType = extractTableTypeFromTableUpdater(tableUpdater);
      if (tableType.isEmpty()) {
        return Validation.Valid.instance();
      }

      Optional<AnnotationDef> subscriptionAnnotation =
          tableUpdater.findAnnotation("akka.javasdk.annotations.Consume.FromWorkflow");
      if (subscriptionAnnotation.isEmpty()) {
        return Validation.Valid.instance();
      }

      Optional<TypeRefDef> workflowClassRef = subscriptionAnnotation.get().getClassValue("value");
      if (workflowClassRef.isEmpty()) {
        return Validation.Valid.instance();
      }

      Optional<TypeRefDef> stateType = extractStateTypeFromWorkflow(workflowClassRef.get());
      if (stateType.isEmpty()) {
        return Validation.Valid.instance();
      }

      return validateTableUpdaterTransformation(tableUpdater, tableType.get(), stateType.get());
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that when state type differs from table type, there's an update handler for
   * transformation.
   *
   * @param tableUpdater the TableUpdater class
   * @param tableType the table row type
   * @param stateType the state/workflow state type
   * @return a Validation result indicating success or failure
   */
  private static Validation validateTableUpdaterTransformation(
      TypeDef tableUpdater, TypeRefDef tableType, TypeRefDef stateType) {

    if (!tableType.getQualifiedName().equals(stateType.getQualifiedName())) {
      // Types differ - need to check for transformation handler
      boolean hasTransformationHandler = false;

      for (MethodDef method : tableUpdater.getPublicMethods()) {
        String returnTypeName = method.getReturnType().getQualifiedName();
        if (returnTypeName.startsWith("akka.javasdk.view.TableUpdater.Effect")) {
          // Check if the return type's generic parameter matches the table type
          List<TypeRefDef> typeArgs = method.getReturnType().getTypeArguments();
          if (!typeArgs.isEmpty()) {
            TypeRefDef updateHandlerType = typeArgs.getFirst();
            if (updateHandlerType.getQualifiedName().equals(tableType.getQualifiedName())) {
              hasTransformationHandler = true;
              break;
            }
          }
        }
      }

      if (!hasTransformationHandler) {
        String message =
            "You are using a type level annotation in this TableUpdater and that requires the"
                + " TableUpdater type ["
                + tableType.getQualifiedName()
                + "] to match the type ["
                + stateType.getQualifiedName()
                + "]. If your intention is to transform the type, you should add a method like"
                + " `Effect<"
                + tableType.getQualifiedName()
                + "> onChange("
                + stateType.getQualifiedName()
                + " state)`.";
        return Validation.of(Validations.errorMessage(tableUpdater, message));
      }
    }

    return Validation.Valid.instance();
  }

  /**
   * View-specific missing handler validation that understands type transformation.
   *
   * <p>For Views with KeyValueEntity or Workflow subscriptions, this validation checks if there's
   * any handler method present (either matching state type or transformation handler).
   *
   * @param tableUpdater the TableUpdater class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMissingHandlerValidations(
      TypeDef tableUpdater, String effectTypeName) {
    // For KeyValueEntity and Workflow subscriptions, check if ANY handler exists
    if (Validations.hasKeyValueEntitySubscription(tableUpdater)
        || Validations.hasWorkflowSubscription(tableUpdater)) {

      // First, check if this is a passthrough scenario (state type matches row type)
      // In passthrough scenarios, no handlers are required
      Optional<TypeRefDef> tableType = extractTableTypeFromTableUpdater(tableUpdater);
      Optional<TypeRefDef> stateType = Optional.empty();

      if (Validations.hasKeyValueEntitySubscription(tableUpdater)) {
        Optional<AnnotationDef> subscriptionAnnotation =
            tableUpdater.findAnnotation("akka.javasdk.annotations.Consume.FromKeyValueEntity");
        if (subscriptionAnnotation.isPresent()) {
          Optional<TypeRefDef> entityClassRef = subscriptionAnnotation.get().getClassValue("value");
          if (entityClassRef.isPresent()) {
            stateType = extractStateTypeFromKeyValueEntity(entityClassRef.get());
          }
        }
      } else if (Validations.hasWorkflowSubscription(tableUpdater)) {
        Optional<AnnotationDef> subscriptionAnnotation =
            tableUpdater.findAnnotation("akka.javasdk.annotations.Consume.FromWorkflow");
        if (subscriptionAnnotation.isPresent()) {
          Optional<TypeRefDef> workflowClassRef =
              subscriptionAnnotation.get().getClassValue("value");
          if (workflowClassRef.isPresent()) {
            stateType = extractStateTypeFromWorkflow(workflowClassRef.get());
          }
        }
      }

      // If types match, it's a passthrough scenario - no handlers required
      if (tableType.isPresent()
          && stateType.isPresent()
          && tableType.get().getQualifiedName().equals(stateType.get().getQualifiedName())) {
        return Validation.Valid.instance();
      }

      // Check if there's any update handler or delete handler
      boolean hasAnyHandler = false;

      for (MethodDef method : tableUpdater.getPublicMethods()) {
        String returnTypeName = method.getReturnType().getQualifiedName();
        if (returnTypeName.startsWith(effectTypeName)) {
          if (Validations.hasHandleDeletes(method)) {
            hasAnyHandler = true;
          } else if (!method.getParameters().isEmpty()) {
            // Any handler with parameters counts
            hasAnyHandler = true;
          }
        }
      }

      if (!hasAnyHandler) {
        // Use the already-extracted stateType for the error message
        String stateTypeName = stateType.isPresent() ? stateType.get().getQualifiedName() : "state";

        return Validation.of(
            Validations.errorMessage(
                tableUpdater,
                "missing handlers. The class must have one handler with '"
                    + stateTypeName
                    + "' parameter and/or one parameterless method annotated with"
                    + " '@DeleteHandler'."));
      }

      return Validation.Valid.instance();
    }

    // For other subscription types (EventSourcedEntity, Topic, etc.), use the default validation
    return Validations.missingHandlerValidations(tableUpdater, effectTypeName);
  }

  /**
   * Extracts the table row type parameter from a TableUpdater class.
   *
   * @param tableUpdater the TableUpdater class
   * @return the TypeRefDef representing the row type, or empty if not found
   */
  private static Optional<TypeRefDef> extractTableTypeFromTableUpdater(TypeDef tableUpdater) {
    List<TypeRefDef> typeArgs = tableUpdater.getSuperclassTypeArguments();
    if (!typeArgs.isEmpty()) {
      return Optional.of(typeArgs.getFirst());
    }
    return Optional.empty();
  }

  /**
   * Extracts the state type parameter from a KeyValueEntity class.
   *
   * @param entityClassRef the TypeRefDef representing the entity class
   * @return the TypeRefDef representing the state type, or empty if not found
   */
  private static Optional<TypeRefDef> extractStateTypeFromKeyValueEntity(
      TypeRefDef entityClassRef) {
    Optional<TypeDef> entityClassDef = entityClassRef.resolveTypeDef();
    if (entityClassDef.isEmpty()) {
      return Optional.empty();
    }

    List<TypeRefDef> typeArgs = entityClassDef.get().getSuperclassTypeArguments();
    if (!typeArgs.isEmpty()) {
      return Optional.of(typeArgs.getFirst());
    }
    return Optional.empty();
  }

  /**
   * Extracts the state type parameter from a Workflow class.
   *
   * @param workflowClassRef the TypeRefDef representing the workflow class
   * @return the TypeRefDef representing the state type, or empty if not found
   */
  private static Optional<TypeRefDef> extractStateTypeFromWorkflow(TypeRefDef workflowClassRef) {
    Optional<TypeDef> workflowClassDef = workflowClassRef.resolveTypeDef();
    if (workflowClassDef.isEmpty()) {
      return Optional.empty();
    }

    List<TypeRefDef> typeArgs = workflowClassDef.get().getSuperclassTypeArguments();
    if (!typeArgs.isEmpty()) {
      return Optional.of(typeArgs.getFirst());
    }
    return Optional.empty();
  }

  /**
   * Validates that TableUpdater has @Consume annotation.
   *
   * @param tableUpdater the TableUpdater class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewTableUpdaterMustHaveConsumeAnnotation(TypeDef tableUpdater) {
    boolean hasConsumeAnnotation =
        tableUpdater.hasAnnotation("akka.javasdk.annotations.Consume.FromTopic")
            || tableUpdater.hasAnnotation("akka.javasdk.annotations.Consume.FromKeyValueEntity")
            || tableUpdater.hasAnnotation("akka.javasdk.annotations.Consume.FromEventSourcedEntity")
            || tableUpdater.hasAnnotation("akka.javasdk.annotations.Consume.FromServiceStream")
            || tableUpdater.hasAnnotation("akka.javasdk.annotations.Consume.FromWorkflow");

    if (!hasConsumeAnnotation) {
      return Validation.of(
          Validations.errorMessage(
              tableUpdater,
              "A TableUpdater subclass must be annotated with `@Consume` annotation."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that the TableUpdater row type is not a primitive wrapper.
   *
   * @param tableUpdater the TableUpdater class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation validateViewUpdaterRowType(TypeDef tableUpdater) {
    List<TypeRefDef> typeArgs = tableUpdater.getSuperclassTypeArguments();
    if (!typeArgs.isEmpty()) {
      TypeRefDef rowType = typeArgs.getFirst();
      if (isPrimitiveWrapper(rowType)) {
        return Validation.of(
            Validations.errorMessage(
                tableUpdater, "View row type " + rowType.getQualifiedName() + " is not supported"));
      }
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates delete handler constraints for all View TableUpdaters. Delete handlers must: - Have
   * zero parameters - Be unique (only one delete handler allowed)
   *
   * @param tableUpdater the TableUpdater class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewDeleteHandlerValidation(TypeDef tableUpdater) {
    if (Validations.doesNotHaveSubscription(tableUpdater)) {
      return Validation.Valid.instance();
    }

    List<MethodDef> deleteHandlers = new ArrayList<>();
    List<MethodDef> deleteHandlersWithParams = new ArrayList<>();

    for (MethodDef method : tableUpdater.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.startsWith("akka.javasdk.view.TableUpdater.Effect")) {
        if (Validations.hasHandleDeletes(method)) {
          if (method.getParameters().isEmpty()) {
            deleteHandlers.add(method);
          } else {
            deleteHandlersWithParams.add(method);
          }
        }
      }
    }

    List<String> errors = new ArrayList<>();

    for (MethodDef method : deleteHandlersWithParams) {
      int numParams = method.getParameters().size();
      errors.add(
          Validations.errorMessage(
              method,
              "Method annotated with '@DeleteHandler' must not have parameters. Found "
                  + numParams
                  + " method parameters."));
    }

    if (deleteHandlers.size() >= 2) {
      for (MethodDef method : deleteHandlers) {
        errors.add(
            Validations.errorMessage(
                method, "Multiple methods annotated with @DeleteHandler are not allowed."));
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates subscription rules specific to state-based View TableUpdaters (KVE/Workflow). For
   * state subscriptions, only one update method is allowed.
   *
   * @param tableUpdater the TableUpdater class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewCommonStateSubscriptionValidation(TypeDef tableUpdater) {
    // This validation only applies to KeyValueEntity and Workflow subscriptions (state-based)
    if (!Validations.hasKeyValueEntitySubscription(tableUpdater)
        && !Validations.hasWorkflowSubscription(tableUpdater)) {
      return Validation.Valid.instance();
    }

    List<MethodDef> updateMethods = new ArrayList<>();

    for (MethodDef method : tableUpdater.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.startsWith("akka.javasdk.view.TableUpdater.Effect")) {
        if (!Validations.hasHandleDeletes(method)) {
          updateMethods.add(method);
        }
      }
    }

    List<String> errors = new ArrayList<>();

    if (updateMethods.size() >= 2) {
      List<String> methodNames = updateMethods.stream().map(MethodDef::getName).toList();
      errors.add(
          Validations.errorMessage(
              tableUpdater,
              "Duplicated update methods ["
                  + String.join(", ", methodNames)
                  + "] for state subscription are not allowed."));
    }

    return Validation.of(errors);
  }

  /**
   * Validates that @Table annotation value is not empty.
   *
   * @param tableUpdater the TableUpdater class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewTableAnnotationMustNotBeEmptyString(TypeDef tableUpdater) {
    Optional<AnnotationDef> tableAnn =
        tableUpdater.findAnnotation("akka.javasdk.annotations.Table");
    if (tableAnn.isPresent()) {
      Optional<String> tableName = tableAnn.get().getStringValue("value");
      if (tableName.isPresent() && tableName.get().isBlank()) {
        return Validation.of(
            Validations.errorMessage(
                tableUpdater, "@Table name is empty, must be a non-empty string."));
      }
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that @FunctionTool is only used on methods returning QueryEffect, not
   * QueryStreamEffect.
   *
   * @param typeDef the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation functionToolOnlyOnQueryEffect(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getMethods()) {
      if (method.hasAnnotation("akka.javasdk.annotations.FunctionTool")) {
        String returnTypeName = method.getReturnType().getQualifiedName();

        // Check if it's a QueryStreamEffect (not allowed)
        if (returnTypeName.equals("akka.javasdk.view.View.QueryStreamEffect")
            || returnTypeName.startsWith("akka.javasdk.view.View.QueryStreamEffect<")) {
          errors.add(
              Validations.errorMessage(
                  method,
                  "View methods annotated with @FunctionTool cannot return QueryStreamEffect."
                      + " Only methods returning QueryEffect can be annotated with"
                      + " @FunctionTool."));
        }
      }
    }

    return Validation.of(errors);
  }
}
