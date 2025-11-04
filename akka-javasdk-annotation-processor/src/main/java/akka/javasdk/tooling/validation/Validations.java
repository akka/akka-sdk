/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Contains validation logic for component classes. This class encapsulates all validation rules
 * that are applied during annotation processing.
 */
public class Validations {

  /**
   * Validates a component class.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validateComponent(TypeElement element) {
    return componentMustBePublic(element)
        .combine(mustHaveValidComponentId(element))
        .combine(TimedActionValidations.validate(element))
        .combine(ConsumerValidations.validate(element))
        .combine(AgentValidations.validate(element))
        .combine(EventSourcedEntityValidations.validate(element))
        .combine(KeyValueEntityValidations.validate(element))
        .combine(WorkflowValidations.validate(element))
        .combine(ViewValidations.validate(element));
  }

  /**
   * Validates that the component class is public.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation componentMustBePublic(TypeElement element) {
    if (element.getModifiers().contains(Modifier.PUBLIC)) {
      return Validation.Valid.instance();
    } else {
      return Validation.of(
          errorMessage(
              element,
              element.getSimpleName()
                  + " is not marked with `public` modifier. Components must be public."));
    }
  }

  /**
   * Validates that a component has a valid component ID. Checks for: - Presence of both @Component
   * and deprecated @ComponentId (error) - Non-empty component ID - No pipe character '|' in
   * component ID
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation mustHaveValidComponentId(TypeElement element) {
    AnnotationMirror componentAnn = findAnnotation(element, "akka.javasdk.annotations.Component");
    AnnotationMirror componentIdAnn =
        findAnnotation(element, "akka.javasdk.annotations.ComponentId");

    if (componentAnn != null && componentIdAnn != null) {
      return Validation.of(
          errorMessage(
              element,
              "Component class '"
                  + element.getQualifiedName()
                  + "' has both @Component and deprecated @ComponentId annotations. Please remove"
                  + " @ComponentId and use only @Component."));
    } else if (componentAnn != null) {
      String componentId = getAnnotationValue(componentAnn, "id");
      if (componentId == null || componentId.isBlank()) {
        return Validation.of(
            errorMessage(element, "@Component id is empty, must be a non-empty string."));
      } else if (componentId.contains("|")) {
        return Validation.of(
            errorMessage(element, "@Component id must not contain the pipe character '|'."));
      } else {
        return Validation.Valid.instance();
      }
    } else if (componentIdAnn != null) {
      String componentId = getAnnotationValue(componentIdAnn, "value");
      if (componentId == null || componentId.isBlank()) {
        return Validation.of(
            errorMessage(element, "@ComponentId is empty, must be a non-empty string."));
      } else if (componentId.contains("|")) {
        return Validation.of(
            errorMessage(element, "@ComponentId must not contain the pipe character '|'."));
      } else {
        return Validation.Valid.instance();
      }
    } else {
      // A missing annotation means that the component is disabled
      return Validation.Valid.instance();
    }
  }

  /**
   * Checks if a component extends a specific class.
   *
   * @param element the component class to check
   * @param className the fully qualified class name to check for
   * @return true if the component extends the specified class
   */
  static boolean extendsClass(TypeElement element, String className) {
    TypeMirror superclass = element.getSuperclass();
    if (superclass == null) {
      return false;
    }

    String superclassName = superclass.toString();
    // Handle generic types by checking if the superclass name starts with the expected class name
    // e.g., "akka.javasdk.eventsourcedentity.EventSourcedEntity<String,Event>" should match
    // "akka.javasdk.eventsourcedentity.EventSourcedEntity"
    if (superclassName.equals(className) || superclassName.startsWith(className + "<")) {
      return true;
    }

    // Check recursively up the hierarchy
    if (superclass instanceof DeclaredType declaredType) {
      Element superElement = declaredType.asElement();
      if (superElement instanceof TypeElement superType) {
        return extendsClass(superType, className);
      }
    }

    return false;
  }

  /**
   * Validates that a component has at least one method returning the one of specified effect types.
   *
   * @param element the component class to validate
   * @param effectTypeNames the fully qualified effect type names
   * @return a Validation result indicating success or failure
   */
  static Validation hasEffectMethod(TypeElement element, String... effectTypeNames) {

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if (Arrays.stream(effectTypeNames).anyMatch(returnTypeName::startsWith)) {
          return Validation.Valid.instance();
        }
      }
    }

    var names = String.join(", ", effectTypeNames);
    return Validation.of(
        "No method returning " + names + " found in " + element.getQualifiedName());
  }

  static Validation commandHandlerArityShouldBeZeroOrOne(
      TypeElement element, String... effectTypeNames) {
    List<String> errors = new ArrayList<>();
    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if (Arrays.stream(effectTypeNames).anyMatch(returnTypeName::startsWith)) {
          int paramCount = method.getParameters().size();
          if (paramCount > 1) {
            errors.add(
                errorMessage(
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
   * Finds an annotation on an element by its fully qualified name.
   *
   * @param element the element to search
   * @param annotationName the fully qualified annotation name
   * @return the AnnotationMirror if found, null otherwise
   */
  static AnnotationMirror findAnnotation(Element element, String annotationName) {
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      if (mirror.getAnnotationType().toString().equals(annotationName)) {
        return mirror;
      }
    }
    return null;
  }

  /**
   * Gets the value of an annotation attribute.
   *
   * @param annotation the annotation mirror
   * @param attributeName the attribute name
   * @return the attribute value as a String, or null if not found
   */
  static String getAnnotationValue(AnnotationMirror annotation, String attributeName) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotation.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().toString().equals(attributeName)) {
        Object value = entry.getValue().getValue();
        return value != null ? value.toString() : null;
      }
    }
    return null;
  }

  /**
   * Creates a formatted error message for an element.
   *
   * @param element the element to create an error message for
   * @param message the error message
   * @return a formatted error message string
   */
  static String errorMessage(Element element, String message) {
    String elementStr;
    if (element instanceof TypeElement typeElement) {
      elementStr = typeElement.getQualifiedName().toString();
    } else {
      elementStr = element.toString();
    }
    return "On '" + elementStr + "': " + message;
  }

  // ==================== Subscription Validation Methods ====================

  /**
   * Validates that subscription methods have exactly one parameter (unless marked
   * with @DeleteHandler).
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  static Validation subscriptionMethodMustHaveOneParameter(
      TypeElement element, String effectTypeName) {
    if (!hasSubscription(element)) {
      return Validation.Valid.instance();
    }

    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
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
                errorMessage(
                    method,
                    "Subscription method must have exactly one parameter, unless it's marked with"
                        + " @DeleteHandler."));
          }
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that subscription methods with ACL annotations are not allowed.
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  static Validation noSubscriptionMethodWithAcl(TypeElement element, String effectTypeName) {
    if (!hasSubscription(element)) {
      return Validation.Valid.instance();
    }

    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        // Use startsWith to handle generic types like Effect<T>
        if ((returnTypeName.equals(effectTypeName)
                || returnTypeName.startsWith(effectTypeName + "<"))
            && findAnnotation(method, "akka.javasdk.annotations.Acl") != null) {
          errors.add(
              errorMessage(
                  method,
                  "Methods from classes annotated with Akka @Consume annotations are for internal"
                      + " use only and cannot be annotated with ACL annotations."));
        }
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates ambiguous handlers for subscriptions. Checks that there are no multiple handlers for
   * the same input type.
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  static Validation ambiguousHandlerValidations(TypeElement element, String effectTypeName) {
    if (!hasSubscription(element)) {
      return Validation.Valid.instance();
    }

    // Group handlers by their last parameter type
    java.util.Map<String, List<ExecutableElement>> handlersByType = new java.util.HashMap<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        // Use startsWith to handle generic types like Effect<T>
        if (returnTypeName.equals(effectTypeName)
            || returnTypeName.startsWith(effectTypeName + "<")) {
          // Get the last parameter type (or empty string for parameterless methods)
          String paramType = "";
          if (!method.getParameters().isEmpty()) {
            paramType =
                method.getParameters().get(method.getParameters().size() - 1).asType().toString();
          }

          handlersByType.computeIfAbsent(paramType, k -> new ArrayList<>()).add(method);
        }
      }
    }

    List<String> errors = new ArrayList<>();

    // Check for ambiguous handlers
    for (java.util.Map.Entry<String, List<ExecutableElement>> entry : handlersByType.entrySet()) {
      if (entry.getValue().size() > 1) {
        String paramType = entry.getKey();
        List<String> methodNames =
            entry.getValue().stream().map(m -> m.getSimpleName().toString()).sorted().toList();

        if (paramType.isEmpty()) {
          // Multiple delete handlers
          errors.add(
              errorMessage(
                  element, "Ambiguous delete handlers: [" + String.join(", ", methodNames) + "]."));
        } else {
          // Multiple handlers for the same type
          errors.add(
              errorMessage(
                  element,
                  "Ambiguous handlers for "
                      + paramType
                      + ", methods: ["
                      + String.join(", ", methodNames)
                      + "] consume the same type."));
        }
      }
    }

    return Validation.of(errors);
  }

  // ==================== Subscription Detection Helpers ====================

  /**
   * Checks if a component has any subscription annotation.
   *
   * @param element the component class to check
   * @return true if the component has any @Consume annotation
   */
  static boolean hasSubscription(TypeElement element) {
    // Check for any annotation that starts with "akka.javasdk.annotations.Consume"
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      String annotationType = mirror.getAnnotationType().toString();
      if (annotationType.startsWith("akka.javasdk.annotations.Consume")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if a method has a @DeleteHandler annotation.
   *
   * @param method the method to check
   * @return true if the method has @DeleteHandler
   */
  static boolean hasHandleDeletes(ExecutableElement method) {
    return findAnnotation(method, "akka.javasdk.annotations.DeleteHandler") != null;
  }

  /**
   * Validates that subscriptions have handlers for the correct types.
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  static Validation missingHandlerValidations(TypeElement element, String effectTypeName) {
    return validateKeyValueEntitySubscriptionHandlers(element, effectTypeName)
        .combine(validateWorkflowSubscriptionHandlers(element, effectTypeName))
        .combine(validateSubscriptionHandlersShared(element, effectTypeName));
  }

  /**
   * Checks if a component has a KeyValueEntity subscription.
   *
   * @param element the component class to check
   * @return true if the component has @Consume.FromKeyValueEntity
   */
  static boolean hasKeyValueEntitySubscription(TypeElement element) {
    return Validations.findAnnotation(
            element, "akka.javasdk.annotations.Consume.FromKeyValueEntity")
        != null;
  }

  /**
   * Checks if a component has a Workflow subscription.
   *
   * @param element the component class to check
   * @return true if the component has @Consume.FromWorkflow
   */
  static boolean hasWorkflowSubscription(TypeElement element) {
    return Validations.findAnnotation(element, "akka.javasdk.annotations.Consume.FromWorkflow")
        != null;
  }

  /**
   * Checks if a component has an EventSourcedEntity subscription.
   *
   * @param element the component class to check
   * @return true if the component has @Consume.FromEventSourcedEntity
   */
  static boolean hasEventSourcedEntitySubscription(TypeElement element) {
    return Validations.findAnnotation(
            element, "akka.javasdk.annotations.Consume.FromEventSourcedEntity")
        != null;
  }

  /**
   * Gets the class value from an annotation attribute.
   *
   * @param annotation the annotation mirror
   * @param attributeName the attribute name
   * @return the TypeMirror representing the class value, or null if not found
   */
  private static TypeMirror getAnnotationClassValue(
      AnnotationMirror annotation, String attributeName) {
    // First check explicit values
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotation.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().toString().equals(attributeName)) {
        Object value = entry.getValue().getValue();
        if (value instanceof TypeMirror) {
          return (TypeMirror) value;
        }
      }
    }

    // If not found in explicit values, check all annotation elements (including defaults)
    for (ExecutableElement method :
        annotation.getAnnotationType().asElement().getEnclosedElements().stream()
            .filter(e -> e instanceof ExecutableElement)
            .map(e -> (ExecutableElement) e)
            .toList()) {
      if (method.getSimpleName().toString().equals(attributeName)) {
        AnnotationValue value = annotation.getElementValues().get(method);
        if (value != null && value.getValue() instanceof TypeMirror) {
          return (TypeMirror) value.getValue();
        }
      }
    }

    return null;
  }

  /**
   * Extracts the event type parameter from an EventSourcedEntity class.
   *
   * @param entityClassMirror the TypeMirror representing the entity class
   * @return the TypeMirror representing the event type, or null if not found
   */
  private static TypeMirror extractEventTypeFromEventSourcedEntity(TypeMirror entityClassMirror) {
    if (!(entityClassMirror instanceof DeclaredType)) {
      return null;
    }

    Element element = ((DeclaredType) entityClassMirror).asElement();
    if (!(element instanceof TypeElement typeElement)) {
      return null;
    }

    // Look for the superclass EventSourcedEntity<State, Event>
    // Event is the second type parameter (index 1)
    TypeMirror superclass = typeElement.getSuperclass();
    if (superclass instanceof DeclaredType declaredType) {
      String superclassName = declaredType.asElement().toString();
      if (superclassName.equals("akka.javasdk.eventsourcedentity.EventSourcedEntity")) {
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (typeArgs.size() >= 2) {
          return typeArgs.get(1); // Second parameter is Event
        }
      }
      // Recursively check parent classes
      if (declaredType.asElement() instanceof TypeElement parentType) {
        return extractEventTypeFromEventSourcedEntity(parentType.asType());
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

    // Look for the superclass KeyValueEntity<State>
    return extractSingleTypeParameter(typeElement, "akka.javasdk.keyvalueentity.KeyValueEntity");
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
          return typeArgs.get(0);
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
   * Validates that a Consumer with KeyValueEntity subscription has a handler for the state type.
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation validateKeyValueEntitySubscriptionHandlers(
      TypeElement element, String effectTypeName) {

    if (hasKeyValueEntitySubscription(element)) {

      // Get the subscribed entity class from @Consume.FromKeyValueEntity(value = EntityClass.class)
      AnnotationMirror subscriptionAnnotation =
          Validations.findAnnotation(
              element, "akka.javasdk.annotations.Consume.FromKeyValueEntity");
      if (subscriptionAnnotation == null) {
        return Validation.Valid.instance();
      }

      TypeMirror entityClass = getAnnotationClassValue(subscriptionAnnotation, "value");
      if (entityClass == null) {
        return Validation.Valid.instance();
      }

      // Extract the state type from KeyValueEntity<State>
      TypeMirror stateType = extractStateTypeFromKeyValueEntity(entityClass);
      if (stateType == null) {
        return Validation.Valid.instance();
      }

      // Check if there's a handler for the state type or a delete handler
      boolean hasStateHandler = false;
      boolean hasDeleteHandler = false;
      boolean hasRawHandler = false;

      for (Element enclosed : element.getEnclosedElements()) {
        if (enclosed instanceof ExecutableElement method) {
          String returnTypeName = method.getReturnType().toString();
          if (returnTypeName.equals(effectTypeName)) {
            if (Validations.hasHandleDeletes(method)) {
              hasDeleteHandler = true;
            } else if (!method.getParameters().isEmpty()) {
              TypeMirror paramType = method.getParameters().get(0).asType();
              if (paramType.toString().equals(stateType.toString())) {
                hasStateHandler = true;
              } else if ("byte[]".equals(paramType.toString())) {
                hasRawHandler = true;
              }
            }
          }
        }
      }

      if (!hasStateHandler && !hasDeleteHandler && !hasRawHandler) {
        return Validation.of(
            Validations.errorMessage(
                element,
                "missing handlers. The class must have one handler with '"
                    + stateType
                    + "' parameter and/or one parameterless method annotated with"
                    + " '@DeleteHandler'."));
      }
    }
    return Validation.Valid.instance();
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

    // Look for the superclass Workflow<State>
    return extractSingleTypeParameter(typeElement, "akka.javasdk.workflow.Workflow");
  }

  /**
   * Validates that a Consumer with Workflow subscription has a handler for the state type.
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation validateWorkflowSubscriptionHandlers(
      TypeElement element, String effectTypeName) {

    if (hasWorkflowSubscription(element)) {

      // Get the subscribed workflow class from @Consume.FromWorkflow(value = WorkflowClass.class)
      AnnotationMirror subscriptionAnnotation =
          Validations.findAnnotation(element, "akka.javasdk.annotations.Consume.FromWorkflow");
      if (subscriptionAnnotation == null) {
        return Validation.Valid.instance();
      }

      TypeMirror workflowClass = getAnnotationClassValue(subscriptionAnnotation, "value");
      if (workflowClass == null) {
        return Validation.Valid.instance();
      }

      // Extract the state type from Workflow<State>
      TypeMirror stateType = extractStateTypeFromWorkflow(workflowClass);
      if (stateType == null) {
        return Validation.Valid.instance();
      }

      // Check if there's a handler for the state type or a delete handler
      boolean hasStateHandler = false;
      boolean hasDeleteHandler = false;
      boolean hasRawHandler = false;

      for (Element enclosed : element.getEnclosedElements()) {
        if (enclosed instanceof ExecutableElement method) {
          String returnTypeName = method.getReturnType().toString();
          if (returnTypeName.equals(effectTypeName)) {
            if (Validations.hasHandleDeletes(method)) {
              hasDeleteHandler = true;
            } else if (!method.getParameters().isEmpty()) {
              TypeMirror paramType = method.getParameters().get(0).asType();
              if (paramType.toString().equals(stateType.toString())) {
                hasStateHandler = true;
              } else if ("byte[]".equals(paramType.toString())) {
                hasRawHandler = true;
              }
            }
          }
        }
      }

      if (!hasStateHandler && !hasDeleteHandler && !hasRawHandler) {
        return Validation.of(
            Validations.errorMessage(
                element,
                "missing handlers. The class must have one handler with '"
                    + stateType
                    + "' parameter and/or one parameterless method annotated with"
                    + " '@DeleteHandler'."));
      }
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that a Consumer/View with EventSourcedEntity subscription has handlers for the event
   * types. This method is package-private for sharing with View validations.
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  static Validation validateSubscriptionHandlersShared(TypeElement element, String effectTypeName) {

    if (hasEventSourcedEntitySubscription(element)) {

      // Get the subscribed entity class from @Consume.FromEventSourcedEntity
      AnnotationMirror subscriptionAnnotation =
          Validations.findAnnotation(
              element, "akka.javasdk.annotations.Consume.FromEventSourcedEntity");
      if (subscriptionAnnotation == null) {
        return Validation.Valid.instance();
      }

      // Check if ignoreUnknown is true
      String ignoreUnknown =
          Validations.getAnnotationValue(subscriptionAnnotation, "ignoreUnknown");
      if ("true".equals(ignoreUnknown)) {
        // If ignoreUnknown is true, we don't validate missing handlers
        return Validation.Valid.instance();
      }

      // Check if there's a raw event handler (byte[] parameter)
      boolean hasRawHandler = hasRawEventHandler(element, effectTypeName);
      if (hasRawHandler) {
        // If there's a raw event handler, we don't validate missing handlers
        return Validation.Valid.instance();
      }

      TypeMirror entityClass = getAnnotationClassValue(subscriptionAnnotation, "value");
      if (entityClass == null) {
        return Validation.Valid.instance();
      }

      // Extract the event type from EventSourcedEntity<State, Event>
      TypeMirror eventType = extractEventTypeFromEventSourcedEntity(entityClass);
      if (eventType == null || !(eventType instanceof DeclaredType)) {
        return Validation.Valid.instance();
      }

      Element eventElement = ((DeclaredType) eventType).asElement();
      if (!(eventElement instanceof TypeElement eventTypeElement)) {
        return Validation.Valid.instance();
      }

      // Only validate if the event type is sealed
      if (!eventTypeElement.getModifiers().contains(Modifier.SEALED)) {
        return Validation.Valid.instance();
      }

      // Collect all handler parameter types
      List<String> handlerTypes = new ArrayList<>();
      for (Element enclosed : element.getEnclosedElements()) {
        if (enclosed instanceof ExecutableElement method) {
          String returnTypeName = method.getReturnType().toString();
          // Use startsWith to handle generic types like Effect<T>
          if ((returnTypeName.equals(effectTypeName)
                  || returnTypeName.startsWith(effectTypeName + "<"))
              && !method.getParameters().isEmpty()) {
            TypeMirror paramType = method.getParameters().get(0).asType();
            handlerTypes.add(paramType.toString());
          }
        }
      }

      // Check if there's a handler for the sealed interface itself
      if (handlerTypes.contains(eventType.toString())) {
        // Single sealed interface handler - valid
        return Validation.Valid.instance();
      }

      // Get all permitted subclasses and check for missing handlers
      List<? extends TypeMirror> permittedSubclasses = eventTypeElement.getPermittedSubclasses();
      List<String> missingHandlers = new ArrayList<>();

      for (TypeMirror subclass : permittedSubclasses) {
        String subclassName = subclass.toString();
        if (!handlerTypes.contains(subclassName)) {
          missingHandlers.add("missing an event handler for '" + subclassName + "'.");
        }
      }

      if (!missingHandlers.isEmpty()) {
        return Validation.of(missingHandlers);
      }
    }

    return Validation.Valid.instance();
  }

  /**
   * Checks if a component has a raw event handler (a method that takes byte[] as the first
   * parameter).
   *
   * @param element the component class to check
   * @param effectTypeName the effect type name to identify subscription methods
   * @return true if the component has at least one method with byte[] as the first parameter
   */
  static boolean hasRawEventHandler(TypeElement element, String effectTypeName) {
    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if ((returnTypeName.equals(effectTypeName)
                || returnTypeName.startsWith(effectTypeName + "<"))
            && !method.getParameters().isEmpty()) {
          String firstParamType = method.getParameters().getFirst().asType().toString();
          if ("byte[]".equals(firstParamType)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
