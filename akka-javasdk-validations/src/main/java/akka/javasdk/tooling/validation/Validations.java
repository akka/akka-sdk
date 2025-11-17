/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import akka.javasdk.validation.ast.TypeRefDef;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Contains validation logic for component classes. This class encapsulates all validation rules
 * that are applied during annotation processing or runtime validation.
 */
public class Validations {

  /**
   * Validates a component class.
   *
   * @param typeDef the component class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validateComponent(TypeDef typeDef) {
    return componentMustBePublic(typeDef)
        .combine(mustHaveValidComponentId(typeDef))
        .combine(TimedActionValidations.validate(typeDef))
        .combine(ConsumerValidations.validate(typeDef))
        .combine(WorkflowValidations.validate(typeDef))
        .combine(KeyValueEntityValidations.validate(typeDef))
        .combine(EventSourcedEntityValidations.validate(typeDef))
        .combine(AgentValidations.validate(typeDef))
        .combine(ViewValidations.validate(typeDef));
  }

  // ==================== Subscription Helper Methods ====================

  /**
   * Checks if a component has a KeyValueEntity subscription.
   *
   * @param typeDef the component class to check
   * @return true if the component has @Consume.FromKeyValueEntity
   */
  public static boolean hasKeyValueEntitySubscription(TypeDef typeDef) {
    return typeDef.hasAnnotation("akka.javasdk.annotations.Consume.FromKeyValueEntity");
  }

  /**
   * Checks if a component has a Workflow subscription.
   *
   * @param typeDef the component class to check
   * @return true if the component has @Consume.FromWorkflow
   */
  public static boolean hasWorkflowSubscription(TypeDef typeDef) {
    return typeDef.hasAnnotation("akka.javasdk.annotations.Consume.FromWorkflow");
  }

  /**
   * Checks if a component has an EventSourcedEntity subscription.
   *
   * @param typeDef the component class to check
   * @return true if the component has @Consume.FromEventSourcedEntity
   */
  public static boolean hasEventSourcedEntitySubscription(TypeDef typeDef) {
    return typeDef.hasAnnotation("akka.javasdk.annotations.Consume.FromEventSourcedEntity");
  }

  /**
   * Checks if a component does not have any subscription annotation.
   *
   * @param typeDef the component class to check
   * @return true if the component has no subscription annotations
   */
  public static boolean doesNotHaveSubscription(TypeDef typeDef) {
    return !typeDef.hasAnnotationStartingWith("akka.javasdk.annotations.Consume");
  }

  /**
   * Checks if a method has @DeleteHandler annotation.
   *
   * @param method the method to check
   * @return true if the method has @DeleteHandler
   */
  public static boolean hasHandleDeletes(MethodDef method) {
    return method.hasAnnotation("akka.javasdk.annotations.DeleteHandler");
  }

  // ==================== Subscription Validation Methods ====================

  /**
   * Validates that subscription methods have exactly one parameter (unless marked
   * with @DeleteHandler).
   *
   * @param typeDef the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  public static Validation subscriptionMethodMustHaveOneParameter(
      TypeDef typeDef, String effectTypeName) {
    if (doesNotHaveSubscription(typeDef)) {
      return Validation.Valid.instance();
    }

    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      // Use startsWith to handle generic types like Effect<T>
      if (returnTypeName.equals(effectTypeName)
          || returnTypeName.startsWith(effectTypeName + "<")) {
        // Skip methods marked with @DeleteHandler
        if (hasHandleDeletes(method)) {
          continue;
        }

        int paramCount = method.getParameters().size();
        if (paramCount != 1) {
          errors.add(
              Validations.errorMessage(
                  method,
                  "Subscription method must have exactly one parameter, unless it's marked with"
                      + " @DeleteHandler."));
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that subscription methods are not annotated with @Acl.
   *
   * @param typeDef the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  public static Validation noSubscriptionMethodWithAcl(TypeDef typeDef, String effectTypeName) {
    if (doesNotHaveSubscription(typeDef)) {
      return Validation.Valid.instance();
    }

    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.equals(effectTypeName)
          || returnTypeName.startsWith(effectTypeName + "<")) {
        if (method.hasAnnotation("akka.javasdk.annotations.Acl")) {
          errors.add(
              Validations.errorMessage(
                  method,
                  "Methods from classes annotated with Akka @Consume annotations are for internal"
                      + " use only and cannot be annotated with ACL annotations."));
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that there are no ambiguous handlers (multiple methods with the same parameter type).
   *
   * @param typeDef the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  public static Validation ambiguousHandlerValidations(TypeDef typeDef, String effectTypeName) {
    if (doesNotHaveSubscription(typeDef)) {
      return Validation.Valid.instance();
    }

    // Group handlers by their last parameter type
    java.util.Map<String, List<MethodDef>> handlersByType = new java.util.HashMap<>();

    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      // Use startsWith to handle generic types like Effect<T>
      if (returnTypeName.equals(effectTypeName)
          || returnTypeName.startsWith(effectTypeName + "<")) {
        // Get the last parameter type (or empty string for parameterless methods)
        String paramType = "";
        if (!method.getParameters().isEmpty()) {
          paramType = method.getParameters().getLast().getType().getQualifiedName();
        }

        handlersByType.computeIfAbsent(paramType, k -> new ArrayList<>()).add(method);
      }
    }

    List<String> errors = new ArrayList<>();

    // Check for ambiguous handlers
    for (java.util.Map.Entry<String, List<MethodDef>> entry : handlersByType.entrySet()) {
      if (entry.getValue().size() > 1) {
        String paramType = entry.getKey();
        List<String> methodNames =
            entry.getValue().stream().map(MethodDef::getName).sorted().toList();

        if (paramType.isEmpty()) {
          // Multiple delete handlers
          errors.add(
              Validations.errorMessage(
                  typeDef, "Ambiguous delete handlers: [" + String.join(", ", methodNames) + "]."));
        } else {
          // Multiple handlers for the same type
          errors.add(
              Validations.errorMessage(
                  typeDef,
                  "Ambiguous handlers for "
                      + paramType
                      + ", methods: ["
                      + String.join(", ", methodNames)
                      + "] consume the same type."));
        }
      }
    }

    return Validation.of(errors).combine(sealedTypeHandlerValidations(typeDef, effectTypeName));
  }

  /**
   * Validates that there are no ambiguous handlers for sealed event types.
   *
   * @param typeDef the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation sealedTypeHandlerValidations(TypeDef typeDef, String effectTypeName) {
    if (!hasEventSourcedEntitySubscription(typeDef)) {
      return Validation.Valid.instance();
    }

    // Check if ignoreUnknown is true
    Optional<AnnotationDef> subscriptionAnnotation =
        typeDef.findAnnotation("akka.javasdk.annotations.Consume.FromEventSourcedEntity");
    if (subscriptionAnnotation.isPresent()) {
      Optional<Boolean> ignoreUnknown =
          subscriptionAnnotation.get().getBooleanValue("ignoreUnknown");
      if (ignoreUnknown.isPresent() && ignoreUnknown.get()) {
        return Validation.Valid.instance();
      }
    }

    List<String> errors = new ArrayList<>();

    // Check for raw byte array handler
    boolean hasRawHandler = false;
    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.equals(effectTypeName)
          || returnTypeName.startsWith(effectTypeName + "<")) {
        if (method.getParameters().size() == 1) {
          String paramType = method.getParameters().getFirst().getType().getQualifiedName();
          if (paramType.equals("byte[]")) {
            hasRawHandler = true;
            break;
          }
        }
      }
    }

    // Collect all handler parameter types (excluding raw handler and DeleteHandler)
    List<TypeRefDef> handlerParamTypes = new ArrayList<>();
    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.equals(effectTypeName)
          || returnTypeName.startsWith(effectTypeName + "<")) {
        if (!hasHandleDeletes(method) && method.getParameters().size() == 1) {
          TypeRefDef paramType = method.getParameters().getFirst().getType();
          if (!paramType.getQualifiedName().equals("byte[]")) {
            handlerParamTypes.add(paramType);
          }
        }
      }
    }

    // Check for sealed types and validate coverage
    for (TypeRefDef paramType : handlerParamTypes) {
      Optional<TypeDef> resolvedType = paramType.resolveTypeDef();
      if (resolvedType.isPresent() && resolvedType.get().isSealed()) {
        TypeDef sealedType = resolvedType.get();
        String sealedTypeName = sealedType.getQualifiedName();
        List<TypeRefDef> permittedSubclasses = sealedType.getPermittedSubclasses();

        // If there's a raw handler, sealed type validation is relaxed
        if (hasRawHandler) {
          continue;
        }

        // Check if there's a single handler for the sealed interface itself
        boolean hasSealedInterfaceHandler =
            handlerParamTypes.stream().anyMatch(t -> t.getQualifiedName().equals(sealedTypeName));

        if (hasSealedInterfaceHandler) {
          // Single sealed interface handler is valid
          continue;
        }

        // Check if all permitted subclasses have handlers
        List<String> missingHandlers = new ArrayList<>();
        for (TypeRefDef subclass : permittedSubclasses) {
          String subclassName = subclass.getRawQualifiedName();
          boolean hasHandler =
              handlerParamTypes.stream()
                  .anyMatch(t -> t.getRawQualifiedName().equals(subclassName));
          if (!hasHandler) {
            missingHandlers.add("missing an event handler for '" + subclassName + "'.");
          }
        }

        if (!missingHandlers.isEmpty()) {
          errors.addAll(missingHandlers);
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that all necessary handlers are present for sealed event types and state
   * subscriptions.
   *
   * @param typeDef the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  public static Validation missingHandlerValidations(TypeDef typeDef, String effectTypeName) {
    Validation kveValidation = validateKeyValueEntitySubscriptionHandlers(typeDef, effectTypeName);
    Validation workflowValidation = validateWorkflowSubscriptionHandlers(typeDef, effectTypeName);
    Validation esValidation =
        validateEventSourcedEntitySubscriptionHandlers(typeDef, effectTypeName);

    return kveValidation.combine(workflowValidation).combine(esValidation);
  }

  /** Validates that a Consumer with KeyValueEntity subscription has appropriate handlers. */
  private static Validation validateKeyValueEntitySubscriptionHandlers(
      TypeDef typeDef, String effectTypeName) {
    if (!hasKeyValueEntitySubscription(typeDef)) {
      return Validation.Valid.instance();
    }

    // Get the subscribed entity class from @Consume.FromKeyValueEntity
    Optional<AnnotationDef> subscriptionAnnotation =
        typeDef.findAnnotation("akka.javasdk.annotations.Consume.FromKeyValueEntity");
    if (subscriptionAnnotation.isEmpty()) {
      return Validation.Valid.instance();
    }

    Optional<TypeRefDef> entityClassRef = subscriptionAnnotation.get().getClassValue("value");
    if (entityClassRef.isEmpty()) {
      return Validation.Valid.instance();
    }

    // Resolve the entity class to get its TypeDef
    Optional<TypeDef> entityClassDef = entityClassRef.get().resolveTypeDef();
    if (entityClassDef.isEmpty()) {
      return Validation.Valid.instance();
    }

    // Extract the state type from KeyValueEntity<State>
    List<TypeRefDef> typeArgs = entityClassDef.get().getSuperclassTypeArguments();
    if (typeArgs.isEmpty()) {
      return Validation.Valid.instance();
    }
    String expectedStateType = typeArgs.getFirst().getQualifiedName();

    // Check for handlers with the correct state type, delete handler, or raw handler
    boolean hasCorrectStateHandler = false;
    boolean hasDeleteHandler = false;
    boolean hasRawHandler = false;

    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.equals(effectTypeName)) {
        if (hasHandleDeletes(method)) {
          hasDeleteHandler = true;
        } else if (!method.getParameters().isEmpty()) {
          String paramType = method.getParameters().getFirst().getType().getQualifiedName();
          if (paramType.equals("byte[]")) {
            hasRawHandler = true;
          } else if (paramType.equals(expectedStateType)) {
            hasCorrectStateHandler = true;
          }
        }
      }
    }

    if (!hasCorrectStateHandler && !hasDeleteHandler && !hasRawHandler) {
      return Validation.of(
          Validations.errorMessage(
              typeDef,
              "missing handlers. The class must have one public handler with '"
                  + expectedStateType
                  + "' parameter and/or one parameterless method annotated with"
                  + " '@DeleteHandler'."));
    }

    return Validation.Valid.instance();
  }

  /** Validates that a Consumer with Workflow subscription has appropriate handlers. */
  private static Validation validateWorkflowSubscriptionHandlers(
      TypeDef typeDef, String effectTypeName) {
    if (!hasWorkflowSubscription(typeDef)) {
      return Validation.Valid.instance();
    }

    // Get the subscribed workflow class from @Consume.FromWorkflow
    Optional<AnnotationDef> subscriptionAnnotation =
        typeDef.findAnnotation("akka.javasdk.annotations.Consume.FromWorkflow");
    if (subscriptionAnnotation.isEmpty()) {
      return Validation.Valid.instance();
    }

    Optional<TypeRefDef> workflowClassRef = subscriptionAnnotation.get().getClassValue("value");
    if (workflowClassRef.isEmpty()) {
      return Validation.Valid.instance();
    }

    // Resolve the workflow class to get its TypeDef
    Optional<TypeDef> workflowClassDef = workflowClassRef.get().resolveTypeDef();
    if (workflowClassDef.isEmpty()) {
      return Validation.Valid.instance();
    }

    // Extract the state type from Workflow<State>
    List<TypeRefDef> typeArgs = workflowClassDef.get().getSuperclassTypeArguments();
    if (typeArgs.isEmpty()) {
      return Validation.Valid.instance();
    }
    String expectedStateType = typeArgs.getFirst().getQualifiedName();

    // Check for handlers with the correct state type, delete handler, or raw handler
    boolean hasCorrectStateHandler = false;
    boolean hasDeleteHandler = false;
    boolean hasRawHandler = false;

    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.equals(effectTypeName)) {
        if (hasHandleDeletes(method)) {
          hasDeleteHandler = true;
        } else if (!method.getParameters().isEmpty()) {
          String paramType = method.getParameters().getFirst().getType().getQualifiedName();
          if (paramType.equals("byte[]")) {
            hasRawHandler = true;
          } else if (paramType.equals(expectedStateType)) {
            hasCorrectStateHandler = true;
          }
        }
      }
    }

    if (!hasCorrectStateHandler && !hasDeleteHandler && !hasRawHandler) {
      return Validation.of(
          Validations.errorMessage(
              typeDef,
              "missing handlers. The class must have one handler with '"
                  + expectedStateType
                  + "' parameter and/or one parameterless method annotated with"
                  + " '@DeleteHandler'."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that a Consumer/View with EventSourcedEntity subscription has handlers for event
   * types.
   */
  private static Validation validateEventSourcedEntitySubscriptionHandlers(
      TypeDef typeDef, String effectTypeName) {
    if (!hasEventSourcedEntitySubscription(typeDef)) {
      return Validation.Valid.instance();
    }

    // Get the subscribed entity class from @Consume.FromEventSourcedEntity
    Optional<AnnotationDef> subscriptionAnnotation =
        typeDef.findAnnotation("akka.javasdk.annotations.Consume.FromEventSourcedEntity");
    if (subscriptionAnnotation.isEmpty()) {
      return Validation.Valid.instance();
    }

    // Check if ignoreUnknown is true
    Optional<Boolean> ignoreUnknown = subscriptionAnnotation.get().getBooleanValue("ignoreUnknown");
    if (ignoreUnknown.isPresent() && ignoreUnknown.get()) {
      return Validation.Valid.instance();
    }

    // Check if there's a raw event handler
    boolean hasRawHandler = false;
    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if ((returnTypeName.equals(effectTypeName) || returnTypeName.startsWith(effectTypeName + "<"))
          && !method.getParameters().isEmpty()) {
        String paramType = method.getParameters().getFirst().getType().getQualifiedName();
        if (paramType.equals("byte[]")) {
          hasRawHandler = true;
          break;
        }
      }
    }

    if (hasRawHandler) {
      return Validation.Valid.instance();
    }

    // Get the entity class and extract the event type
    Optional<TypeRefDef> entityClassRef = subscriptionAnnotation.get().getClassValue("value");
    if (entityClassRef.isEmpty()) {
      return Validation.Valid.instance();
    }

    Optional<TypeDef> entityClassDef = entityClassRef.get().resolveTypeDef();
    if (entityClassDef.isEmpty()) {
      return Validation.Valid.instance();
    }

    // Extract the event type from EventSourcedEntity<State, Event> (second type argument)
    List<TypeRefDef> typeArgs = entityClassDef.get().getSuperclassTypeArguments();
    if (typeArgs.size() < 2) {
      return Validation.Valid.instance();
    }

    // Check if the event type is sealed - if not, we don't validate
    TypeRefDef eventTypeRef = typeArgs.get(1);
    Optional<TypeDef> eventTypeDef = eventTypeRef.resolveTypeDef();
    if (eventTypeDef.isEmpty() || !eventTypeDef.get().isSealed()) {
      return Validation.Valid.instance();
    }

    // Collect all handler parameter types (excluding raw handler)
    List<String> handlerParamTypes = getHandlerParamTypes(typeDef, effectTypeName);

    // Check if there's a handler for the sealed interface itself
    String eventTypeName = eventTypeRef.getQualifiedName();
    if (handlerParamTypes.contains(eventTypeName)) {
      // Single sealed interface handler is valid
      return Validation.Valid.instance();
    }

    // Get all permitted subclasses and check for missing handlers
    List<TypeRefDef> permittedSubclasses = eventTypeDef.get().getPermittedSubclasses();
    List<String> missingHandlers = new ArrayList<>();

    for (TypeRefDef subclass : permittedSubclasses) {
      String subclassName = subclass.getRawQualifiedName();
      if (!handlerParamTypes.contains(subclassName)) {
        missingHandlers.add("missing an event handler for '" + subclassName + "'.");
      }
    }

    return Validation.of(missingHandlers);
  }

  private static List<String> getHandlerParamTypes(TypeDef typeDef, String effectTypeName) {
    List<String> handlerParamTypes = new ArrayList<>();
    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.equals(effectTypeName)
          || returnTypeName.startsWith(effectTypeName + "<")) {
        if (!method.getParameters().isEmpty()) {
          String paramType = method.getParameters().getFirst().getType().getQualifiedName();
          if (!paramType.equals("byte[]")) {
            handlerParamTypes.add(paramType);
          }
        }
      }
    }
    return handlerParamTypes;
  }

  /**
   * Validates that the component class is public.
   *
   * @param typeDef the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation componentMustBePublic(TypeDef typeDef) {
    if (typeDef.isPublic()) {
      return Validation.Valid.instance();
    } else {
      return Validation.of(
          errorMessage(
              typeDef,
              typeDef.getSimpleName()
                  + " is not marked with `public` modifier. Components must be public."));
    }
  }

  /**
   * Validates that a component has a valid component ID. Checks for: - Presence of both @Component
   * and deprecated @ComponentId (error) - Non-empty component ID - No pipe character '|' in
   * component ID
   *
   * @param typeDef the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation mustHaveValidComponentId(TypeDef typeDef) {
    Optional<AnnotationDef> componentAnn =
        typeDef.findAnnotation("akka.javasdk.annotations.Component");
    Optional<AnnotationDef> componentIdAnn =
        typeDef.findAnnotation("akka.javasdk.annotations.ComponentId");

    if (componentAnn.isPresent() && componentIdAnn.isPresent()) {
      return Validation.of(
          errorMessage(
              typeDef,
              "Component class '"
                  + typeDef.getQualifiedName()
                  + "' has both @Component and deprecated @ComponentId annotations. Please remove"
                  + " @ComponentId and use only @Component."));
    } else if (componentAnn.isPresent()) {
      Optional<String> componentId = componentAnn.get().getStringValue("id");
      if (componentId.isEmpty() || componentId.get().isBlank()) {
        return Validation.of(
            errorMessage(typeDef, "@Component id is empty, must be a non-empty string."));
      } else if (componentId.get().contains("|")) {
        return Validation.of(
            errorMessage(typeDef, "@Component id must not contain the pipe character '|'."));
      } else {
        return Validation.Valid.instance();
      }
    } else if (componentIdAnn.isPresent()) {
      Optional<String> componentId = componentIdAnn.get().getStringValue("value");
      if (componentId.isEmpty() || componentId.get().isBlank()) {
        return Validation.of(
            errorMessage(typeDef, "@ComponentId is empty, must be a non-empty string."));
      } else if (componentId.get().contains("|")) {
        return Validation.of(
            errorMessage(typeDef, "@ComponentId must not contain the pipe character '|'."));
      } else {
        return Validation.Valid.instance();
      }
    } else {
      // A missing annotation means that the component is disabled
      return Validation.Valid.instance();
    }
  }

  /**
   * Validates that a component has at least one method returning one of the specified effect types.
   *
   * @param typeDef the component class to validate
   * @param effectTypeNames the fully qualified effect type names
   * @return a Validation result indicating success or failure
   */
  public static Validation hasEffectMethod(TypeDef typeDef, String... effectTypeNames) {
    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (Arrays.stream(effectTypeNames).anyMatch(returnTypeName::startsWith)) {
        return Validation.Valid.instance();
      }
    }

    var names = String.join(", ", effectTypeNames);
    return Validation.of(
        "No method returning " + names + " found in " + typeDef.getQualifiedName());
  }

  /**
   * Validates that strictly public command handlers have zero or one the parameter.
   *
   * @param typeDef the component class to validate
   * @param effectTypeNames the fully qualified effect type names
   * @return a Validation result indicating success or failure
   */
  public static Validation strictlyPublicCommandHandlerArityShouldBeZeroOrOne(
      TypeDef typeDef, String... effectTypeNames) {
    return commandHandlerArityShouldBeZeroOrOne(
        typeDef, typeDef.getPublicMethods(), effectTypeNames);
  }

  /**
   * Validates that command handlers have zero or one the parameter. We mostly validate again public
   * method, but workflows may have private StepEffect methods
   *
   * @param typeDef the component class to validate
   * @param effectTypeNames the fully qualified effect type names
   * @return a Validation result indicating success or failure
   */
  public static Validation commandHandlerArityShouldBeZeroOrOne(
      TypeDef typeDef, String... effectTypeNames) {
    return commandHandlerArityShouldBeZeroOrOne(typeDef, typeDef.getMethods(), effectTypeNames);
  }

  private static Validation commandHandlerArityShouldBeZeroOrOne(
      TypeDef typeDef, List<MethodDef> methods, String... effectTypeNames) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : methods) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (Arrays.stream(effectTypeNames).anyMatch(returnTypeName::startsWith)) {
        int paramCount = method.getParameters().size();
        if (paramCount > 1) {
          errors.add(
              errorMessage(
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
   * Validates that @FunctionTool is not used on private methods.
   *
   * <p>This validation applies to all components except Agents. Agents have their own validation
   * logic that allows @FunctionTool on private methods but not on command handlers.
   *
   * @param typeDef the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation functionToolMustNotBeOnPrivateMethods(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getMethods()) {
      if (!method.isPublic() && method.hasAnnotation("akka.javasdk.annotations.FunctionTool")) {
        errors.add(
            errorMessage(
                method,
                "Methods annotated with @FunctionTool must be public. Private methods cannot be"
                    + " annotated with @FunctionTool."));
      }
    }

    return Validation.of(errors);
  }

  /**
   * Creates a formatted error message for a type.
   *
   * @param typeDef the type to create an error message for
   * @param message the error message
   * @return a formatted error message string
   */
  public static String errorMessage(TypeDef typeDef, String message) {
    return "On '" + typeDef.getQualifiedName() + "': " + message;
  }

  /**
   * Creates a formatted error message for a method.
   *
   * @param method the method to create an error message for
   * @param message the error message
   * @return a formatted error message string
   */
  public static String errorMessage(MethodDef method, String message) {
    return "On method '" + method.getName() + "': " + message;
  }
}
