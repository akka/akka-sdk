/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import static akka.javasdk.tooling.validation.Validations.getAnnotationClassValue;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/** Contains validation logic specific to View components. */
public class ViewValidations {

  /**
   * Validates a View component.
   *
   * @param element the View class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeElement element) {
    if (!Validations.extendsClass(element, "akka.javasdk.view.View")) {
      return Validation.Valid.instance();
    }

    // Get all TableUpdater nested classes
    List<TypeElement> tableUpdaters =
        element.getEnclosedElements().stream()
            .filter(enclosed -> enclosed instanceof TypeElement)
            .map(enclosed -> (TypeElement) enclosed)
            .filter(ViewValidations::isViewTableUpdater)
            .toList();

    // Validate View-level rules
    Validation viewValidation =
        viewMustNotHaveTableAnnotation(element)
            .combine(viewMustHaveAtLeastOneViewTableUpdater(element))
            .combine(viewMustHaveAtLeastOneQueryMethod(element))
            .combine(validateQueryResultTypes(element))
            .combine(viewQueriesWithStreamUpdatesMustBeStreaming(element))
            .combine(viewQueryMethodArityShouldBeZeroOrOne(element))
            .combine(viewMultipleTableUpdatersMustHaveTableAnnotations(element))
            .combine(functionToolOnlyOnQueryEffect(element));

    // Validate each TableUpdater
    for (TypeElement updater : tableUpdaters) {
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
   * @param element the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMustNotHaveTableAnnotation(TypeElement element) {
    AnnotationMirror tableAnn =
        Validations.findAnnotation(element, "akka.javasdk.annotations.Table");
    if (tableAnn != null) {
      return Validation.of(
          Validations.errorMessage(element, "A View itself should not be annotated with @Table."));
    }
    return Validation.Valid.instance();
  }

  /**
   * Validates that a View has at least one TableUpdater nested class.
   *
   * @param element the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMustHaveAtLeastOneViewTableUpdater(TypeElement element) {
    long tableUpdaterCount =
        element.getEnclosedElements().stream()
            .filter(enclosed -> enclosed instanceof TypeElement)
            .map(enclosed -> (TypeElement) enclosed)
            .filter(ViewValidations::isViewTableUpdater)
            .count();

    if (tableUpdaterCount < 1) {
      return Validation.of(
          Validations.errorMessage(
              element, "A view must contain at least one public static TableUpdater subclass."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Checks if a nested class is a View TableUpdater.
   *
   * @param element the nested class to check
   * @return true if it extends View.TableUpdater
   */
  private static boolean isViewTableUpdater(TypeElement element) {
    if (!element.getModifiers().contains(Modifier.PUBLIC)
        || !element.getModifiers().contains(Modifier.STATIC)) {
      return false;
    }
    return Validations.extendsClass(element, "akka.javasdk.view.TableUpdater");
  }

  /**
   * Validates that a View has at least one method annotated with @Query.
   *
   * @param element the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMustHaveAtLeastOneQueryMethod(TypeElement element) {
    boolean hasAtLeastOneQuery =
        element.getEnclosedElements().stream()
            .filter(enclosed -> enclosed instanceof ExecutableElement)
            .anyMatch(
                method ->
                    Validations.findAnnotation(method, "akka.javasdk.annotations.Query") != null);

    if (!hasAtLeastOneQuery) {
      return Validation.of(
          Validations.errorMessage(
              element,
              "No valid query method found. Views should have at least one method annotated with"
                  + " @Query."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates query result types - must return QueryEffect or QueryStreamEffect, and result type
   * cannot be a primitive wrapper.
   *
   * @param element the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation validateQueryResultTypes(TypeElement element) {
    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        AnnotationMirror queryAnn =
            Validations.findAnnotation(method, "akka.javasdk.annotations.Query");
        if (queryAnn != null) {
          String returnTypeName = method.getReturnType().toString();

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
            TypeMirror returnType = method.getReturnType();
            if (returnType instanceof DeclaredType declaredType) {
              List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
              if (!typeArgs.isEmpty()) {
                TypeMirror resultType = typeArgs.getFirst();
                if (isPrimitiveWrapper(resultType)) {
                  errors.add(
                      Validations.errorMessage(
                          method, "View query result type " + resultType + " is not supported"));
                }
              }
            }
          }
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Checks if a TypeMirror represents a primitive wrapper type.
   *
   * @param type the type to check
   * @return true if it's a primitive wrapper
   */
  private static boolean isPrimitiveWrapper(TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }

    Element element = declaredType.asElement();
    if (!(element instanceof TypeElement typeElement)) {
      return false;
    }

    String typeName = typeElement.getQualifiedName().toString();
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
   * @param element the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewQueriesWithStreamUpdatesMustBeStreaming(TypeElement element) {
    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        AnnotationMirror queryAnn =
            Validations.findAnnotation(method, "akka.javasdk.annotations.Query");
        if (queryAnn != null) {
          String streamUpdatesValue = Validations.getAnnotationValue(queryAnn, "streamUpdates");
          if ("true".equals(streamUpdatesValue)) {
            String returnTypeName = method.getReturnType().toString();
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
    }

    return Validation.of(errors);
  }

  /**
   * Validates that View query methods have zero or one parameter.
   *
   * @param element the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewQueryMethodArityShouldBeZeroOrOne(TypeElement element) {
    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        AnnotationMirror queryAnn =
            Validations.findAnnotation(method, "akka.javasdk.annotations.Query");
        if (queryAnn != null) {
          int paramCount = method.getParameters().size();
          if (paramCount > 1) {
            errors.add(
                Validations.errorMessage(
                    element,
                    "Method ["
                        + method.getSimpleName()
                        + "] must have zero or one argument. If you need to pass more arguments,"
                        + " wrap them in a class."));
          }
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that when there are multiple TableUpdaters, each must have @Table annotation.
   *
   * @param element the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMultipleTableUpdatersMustHaveTableAnnotations(TypeElement element) {
    List<TypeElement> tableUpdaters =
        element.getEnclosedElements().stream()
            .filter(enclosed -> enclosed instanceof TypeElement)
            .map(enclosed -> (TypeElement) enclosed)
            .filter(ViewValidations::isViewTableUpdater)
            .toList();

    if (tableUpdaters.size() > 1) {
      for (TypeElement tableUpdater : tableUpdaters) {
        AnnotationMirror tableAnn =
            Validations.findAnnotation(tableUpdater, "akka.javasdk.annotations.Table");
        if (tableAnn == null) {
          return Validation.of(
              Validations.errorMessage(
                  element,
                  "When there are multiple table updater, each must be annotated with @Table."));
        }
      }
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates a TableUpdater nested class.
   *
   * @param element the TableUpdater class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation validateViewTableUpdater(TypeElement element) {
    String effectType = "akka.javasdk.view.TableUpdater.Effect";
    return viewTableUpdaterMustHaveConsumeAnnotation(element)
        .combine(validateViewUpdaterRowType(element))
        .combine(viewDeleteHandlerValidation(element))
        .combine(viewCommonStateSubscriptionValidation(element))
        .combine(viewMustHaveCorrectUpdateHandlerWhenTransformingViewUpdates(element))
        .combine(Validations.ambiguousHandlerValidations(element, effectType))
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, effectType))
        .combine(viewMissingHandlerValidations(element, effectType))
        .combine(Validations.noSubscriptionMethodWithAcl(element, effectType))
        .combine(Validations.subscriptionMethodMustHaveOneParameter(element, effectType));
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
      TypeElement tableUpdater) {

    // Check for KeyValueEntity subscription
    if (Validations.hasKeyValueEntitySubscription(tableUpdater)) {
      TypeMirror tableType = extractTableTypeFromTableUpdater(tableUpdater);
      if (tableType == null) {
        return Validation.Valid.instance();
      }

      AnnotationMirror subscriptionAnnotation =
          Validations.findAnnotation(
              tableUpdater, "akka.javasdk.annotations.Consume.FromKeyValueEntity");
      if (subscriptionAnnotation == null) {
        return Validation.Valid.instance();
      }

      TypeMirror entityClass = getAnnotationClassValue(subscriptionAnnotation, "value");
      if (entityClass == null) {
        return Validation.Valid.instance();
      }

      TypeMirror stateType = extractStateTypeFromKeyValueEntity(entityClass);
      if (stateType == null) {
        return Validation.Valid.instance();
      }

      return validateTableUpdaterTransformation(tableUpdater, tableType, stateType);

    } else if (Validations.hasWorkflowSubscription(tableUpdater)) {
      // Check for Workflow subscription
      TypeMirror tableType = extractTableTypeFromTableUpdater(tableUpdater);
      if (tableType == null) {
        return Validation.Valid.instance();
      }

      AnnotationMirror subscriptionAnnotation =
          Validations.findAnnotation(tableUpdater, "akka.javasdk.annotations.Consume.FromWorkflow");
      if (subscriptionAnnotation == null) {
        return Validation.Valid.instance();
      }

      TypeMirror workflowClass = getAnnotationClassValue(subscriptionAnnotation, "value");
      if (workflowClass == null) {
        return Validation.Valid.instance();
      }

      TypeMirror stateType = extractStateTypeFromWorkflow(workflowClass);
      if (stateType == null) {
        return Validation.Valid.instance();
      }

      return validateTableUpdaterTransformation(tableUpdater, tableType, stateType);
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
      TypeElement tableUpdater, TypeMirror tableType, TypeMirror stateType) {

    if (!tableType.toString().equals(stateType.toString())) {
      // Types differ - need to check for transformation handler
      boolean hasTransformationHandler = false;

      for (Element enclosed : tableUpdater.getEnclosedElements()) {
        if (enclosed instanceof ExecutableElement method) {
          String returnTypeName = method.getReturnType().toString();
          if (returnTypeName.startsWith("akka.javasdk.view.TableUpdater.Effect")) {
            // Check if the return type's generic parameter matches the table type
            TypeMirror returnType = method.getReturnType();
            if (returnType instanceof DeclaredType declaredType) {
              List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
              if (!typeArgs.isEmpty()) {
                TypeMirror updateHandlerType = typeArgs.getFirst();
                if (updateHandlerType.toString().equals(tableType.toString())) {
                  hasTransformationHandler = true;
                  break;
                }
              }
            }
          }
        }
      }

      if (!hasTransformationHandler) {
        String message =
            "You are using a type level annotation in this TableUpdater and that requires the"
                + " TableUpdater type ["
                + tableType
                + "] to match the type ["
                + stateType
                + "]. If your intention is to transform the type, you should add a method like"
                + " `Effect<"
                + tableType
                + "> onChange("
                + stateType
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
   * @param element the TableUpdater class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation viewMissingHandlerValidations(
      TypeElement element, String effectTypeName) {
    // For KeyValueEntity and Workflow subscriptions, check if ANY handler exists
    if (Validations.hasKeyValueEntitySubscription(element)
        || Validations.hasWorkflowSubscription(element)) {

      // First, check if this is a passthrough scenario (state type matches row type)
      // In passthrough scenarios, no handlers are required
      TypeMirror tableType = extractTableTypeFromTableUpdater(element);
      TypeMirror stateType = null;

      if (Validations.hasKeyValueEntitySubscription(element)) {
        AnnotationMirror subscriptionAnnotation =
            Validations.findAnnotation(
                element, "akka.javasdk.annotations.Consume.FromKeyValueEntity");
        if (subscriptionAnnotation != null) {
          TypeMirror entityClass = getAnnotationClassValue(subscriptionAnnotation, "value");
          if (entityClass != null) {
            stateType = extractStateTypeFromKeyValueEntity(entityClass);
          }
        }
      } else if (Validations.hasWorkflowSubscription(element)) {
        AnnotationMirror subscriptionAnnotation =
            Validations.findAnnotation(element, "akka.javasdk.annotations.Consume.FromWorkflow");
        if (subscriptionAnnotation != null) {
          TypeMirror workflowClass = getAnnotationClassValue(subscriptionAnnotation, "value");
          if (workflowClass != null) {
            stateType = extractStateTypeFromWorkflow(workflowClass);
          }
        }
      }

      // If types match, it's a passthrough scenario - no handlers required
      if (tableType != null
          && stateType != null
          && tableType.toString().equals(stateType.toString())) {
        return Validation.Valid.instance();
      }

      // Check if there's any update handler or delete handler
      boolean hasAnyHandler = false;

      for (Element enclosed : element.getEnclosedElements()) {
        if (enclosed instanceof ExecutableElement method) {
          String returnTypeName = method.getReturnType().toString();
          if (returnTypeName.startsWith(effectTypeName)) {
            if (Validations.hasHandleDeletes(method)) {
              hasAnyHandler = true;
            } else if (!method.getParameters().isEmpty()) {
              // Any handler with parameters counts
              hasAnyHandler = true;
            }
          }
        }
      }

      if (!hasAnyHandler) {
        // Use the already-extracted stateType for the error message
        String stateTypeName = (stateType != null) ? stateType.toString() : "state";

        return Validation.of(
            Validations.errorMessage(
                element,
                "missing handlers. The class must have one handler with '"
                    + stateTypeName
                    + "' parameter and/or one parameterless method annotated with"
                    + " '@DeleteHandler'."));
      }

      return Validation.Valid.instance();
    }

    // For other subscription types (EventSourcedEntity, Topic, etc.), use the default validation
    return Validations.validateSubscriptionHandlersShared(element, effectTypeName);
  }

  /**
   * Extracts the table row type parameter from a TableUpdater class.
   *
   * @param tableUpdater the TableUpdater class
   * @return the TypeMirror representing the row type, or null if not found
   */
  private static TypeMirror extractTableTypeFromTableUpdater(TypeElement tableUpdater) {
    TypeMirror superclass = tableUpdater.getSuperclass();
    if (superclass instanceof DeclaredType declaredType) {
      String superclassName = declaredType.asElement().toString();
      if (superclassName.equals("akka.javasdk.view.TableUpdater")) {
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (!typeArgs.isEmpty()) {
          return typeArgs.getFirst();
        }
      }
    }
    return null;
  }

  /**
   * Extracts the state type parameter from a KeyValueEntity class.
   *
   * @param entityClassMirror the TypeMirror representing the entity class
   * @return the TypeMirror representing the state type, or null if not found
   */
  private static TypeMirror extractStateTypeFromKeyValueEntity(TypeMirror entityClassMirror) {
    if (!(entityClassMirror instanceof DeclaredType)) {
      return null;
    }

    Element element = ((DeclaredType) entityClassMirror).asElement();
    if (!(element instanceof TypeElement typeElement)) {
      return null;
    }

    return extractSingleTypeParameter(typeElement, "akka.javasdk.keyvalueentity.KeyValueEntity");
  }

  /**
   * Extracts the state type parameter from a Workflow class.
   *
   * @param workflowClassMirror the TypeMirror representing the workflow class
   * @return the TypeMirror representing the state type, or null if not found
   */
  private static TypeMirror extractStateTypeFromWorkflow(TypeMirror workflowClassMirror) {
    if (!(workflowClassMirror instanceof DeclaredType)) {
      return null;
    }

    Element element = ((DeclaredType) workflowClassMirror).asElement();
    if (!(element instanceof TypeElement typeElement)) {
      return null;
    }

    return extractSingleTypeParameter(typeElement, "akka.javasdk.workflow.Workflow");
  }

  /**
   * Extracts a single type parameter from a generic superclass.
   *
   * @param typeElement the type element to examine
   * @param expectedSuperclass the fully qualified name of the expected superclass
   * @return the TypeMirror representing the type parameter, or null if not found
   */
  private static TypeMirror extractSingleTypeParameter(
      TypeElement typeElement, String expectedSuperclass) {
    TypeMirror superclass = typeElement.getSuperclass();
    if (superclass instanceof DeclaredType declaredType) {
      String superclassName = declaredType.asElement().toString();
      if (superclassName.equals(expectedSuperclass)) {
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (!typeArgs.isEmpty()) {
          return typeArgs.getFirst();
        }
      }
      // Recursively check parent classes
      if (declaredType.asElement() instanceof TypeElement parentType) {
        return extractSingleTypeParameter(parentType, expectedSuperclass);
      }
    }
    return null;
  }

  /**
   * Validates that TableUpdater has @Consume annotation.
   *
   * @param tableUpdater the TableUpdater class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation viewTableUpdaterMustHaveConsumeAnnotation(TypeElement tableUpdater) {
    boolean hasConsumeAnnotation =
        Validations.findAnnotation(tableUpdater, "akka.javasdk.annotations.Consume.FromTopic")
                != null
            || Validations.findAnnotation(
                    tableUpdater, "akka.javasdk.annotations.Consume.FromKeyValueEntity")
                != null
            || Validations.findAnnotation(
                    tableUpdater, "akka.javasdk.annotations.Consume.FromEventSourcedEntity")
                != null
            || Validations.findAnnotation(
                    tableUpdater, "akka.javasdk.annotations.Consume.FromServiceStream")
                != null
            || Validations.findAnnotation(
                    tableUpdater, "akka.javasdk.annotations.Consume.FromWorkflow")
                != null;

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
  private static Validation validateViewUpdaterRowType(TypeElement tableUpdater) {
    TypeMirror superclass = tableUpdater.getSuperclass();
    if (superclass instanceof DeclaredType declaredType) {
      List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
      if (!typeArgs.isEmpty()) {
        TypeMirror rowType = typeArgs.getFirst();
        if (isPrimitiveWrapper(rowType)) {
          return Validation.of(
              Validations.errorMessage(
                  tableUpdater, "View row type " + rowType + " is not supported"));
        }
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
  private static Validation viewDeleteHandlerValidation(TypeElement tableUpdater) {
    if (Validations.doesNotHaveSubscription(tableUpdater)) {
      return Validation.Valid.instance();
    }

    List<ExecutableElement> deleteHandlers = new ArrayList<>();
    List<ExecutableElement> deleteHandlersWithParams = new ArrayList<>();

    for (Element enclosed : tableUpdater.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
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
    }

    List<String> errors = new ArrayList<>();

    for (ExecutableElement method : deleteHandlersWithParams) {
      int numParams = method.getParameters().size();
      errors.add(
          Validations.errorMessage(
              method,
              "Method annotated with '@DeleteHandler' must not have parameters. Found "
                  + numParams
                  + " method parameters."));
    }

    if (deleteHandlers.size() >= 2) {
      for (ExecutableElement method : deleteHandlers) {
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
  private static Validation viewCommonStateSubscriptionValidation(TypeElement tableUpdater) {
    // This validation only applies to KeyValueEntity and Workflow subscriptions (state-based)
    if (!Validations.hasKeyValueEntitySubscription(tableUpdater)
        && !Validations.hasWorkflowSubscription(tableUpdater)) {
      return Validation.Valid.instance();
    }

    List<ExecutableElement> updateMethods = new ArrayList<>();

    for (Element enclosed : tableUpdater.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if (returnTypeName.startsWith("akka.javasdk.view.TableUpdater.Effect")) {
          if (!Validations.hasHandleDeletes(method)) {
            updateMethods.add(method);
          }
        }
      }
    }

    List<String> errors = new ArrayList<>();

    if (updateMethods.size() >= 2) {
      List<String> methodNames =
          updateMethods.stream().map(m -> m.getSimpleName().toString()).toList();
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
  private static Validation viewTableAnnotationMustNotBeEmptyString(TypeElement tableUpdater) {
    AnnotationMirror tableAnn =
        Validations.findAnnotation(tableUpdater, "akka.javasdk.annotations.Table");
    if (tableAnn != null) {
      String tableName = Validations.getAnnotationValue(tableAnn, "value");
      if (tableName != null && tableName.isBlank()) {
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
   * @param element the View class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation functionToolOnlyOnQueryEffect(TypeElement element) {
    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        if (Validations.findAnnotation(method, "akka.javasdk.annotations.FunctionTool") != null) {
          String returnTypeName = method.getReturnType().toString();

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
    }

    return Validation.of(errors);
  }
}
