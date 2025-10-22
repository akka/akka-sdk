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
        .combine(validateTimedAction(element))
        .combine(validateConsumer(element))
        .combine(validateAgent(element))
        .combine(validateEventSourcedEntity(element));
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
            errorMessage(element, "@ComponentId name is empty, must be a non-empty string."));
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
   * Validates a TimedAction component. Checks for: - At least one method returning
   * TimedAction.Effect - Command handlers (methods returning Effect) must have zero or one
   * parameter
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation validateTimedAction(TypeElement element) {

    if (isTimedAction(element)) {
      var effectType = "akka.javasdk.timedaction.TimedAction.Effect";
      return hasEffectMethod(element, effectType)
          .combine(commandHandlerArityShouldBeZeroOrOne(element, effectType));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates a Consumer component. Checks for: - Must have @Consume annotation - At least one
   * method returning Consumer.Effect - Command handlers must have zero or one parameter -
   * Subscription validation rules
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation validateConsumer(TypeElement element) {

    if (isConsumer(element)) {
      String effectType = "akka.javasdk.consumer.Consumer.Effect";
      return hasEffectMethod(element, effectType)
          .combine(hasConsumeAnnotation(element))
          .combine(commandHandlerArityShouldBeZeroOrOne(element, effectType))
          .combine(typeLevelSubscriptionValidation(element))
          .combine(ambiguousHandlerValidations(element, effectType))
          .combine(valueEntitySubscriptionValidations(element, effectType))
          .combine(workflowSubscriptionValidations(element, effectType))
          .combine(topicPublicationValidations(element))
          .combine(publishStreamIdMustBeFilled(element))
          .combine(noSubscriptionMethodWithAcl(element, effectType))
          .combine(subscriptionMethodMustHaveOneParameter(element, effectType));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates an Agent component. Checks for: - Must have exactly one command handler (method
   * returning Agent.Effect or Agent.StreamEffect) - Command handlers must have zero or one
   * parameter - Valid AgentDescription configuration
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation validateAgent(TypeElement element) {

    if (isAgent(element)) {

      var effectTypes =
          new String[] {"akka.javasdk.agent.Agent.Effect", "akka.javasdk.agent.Agent.StreamEffect"};

      return mustHaveValidAgentDescription(element)
          .combine(hasEffectMethod(element, effectTypes))
          .combine(agentCommandHandlersMustBeOne(element))
          .combine(commandHandlerArityShouldBeZeroOrOne(element, effectTypes));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates an EventSourcedEntity component. Checks for: - Event type must be sealed - At least
   * one method returning EventSourcedEntity.Effect - Command handlers must have unique names -
   * Command handlers must have zero or one parameter
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  public static Validation validateEventSourcedEntity(TypeElement element) {

    if (isEventSourcedEntity(element)) {
      String effectType = "akka.javasdk.eventsourcedentity.EventSourcedEntity.Effect";
      return eventSourcedEntityEventMustBeSealed(element)
          .combine(hasEffectMethod(element, effectType))
          .combine(eventSourcedCommandHandlersMustBeUnique(element))
          .combine(commandHandlerArityShouldBeZeroOrOne(element, effectType));
    }

    return Validation.Valid.instance();
  }

  /**
   * Checks if a component extends TimedAction.
   *
   * @param element the component class to check
   * @return true if the component extends TimedAction, false otherwise
   */
  private static boolean isTimedAction(TypeElement element) {
    return extendsClass(element, "akka.javasdk.timedaction.TimedAction");
  }

  /**
   * Checks if a component extends Consumer.
   *
   * @param element the component class to check
   * @return true if the component extends Consumer, false otherwise
   */
  private static boolean isConsumer(TypeElement element) {
    return extendsClass(element, "akka.javasdk.consumer.Consumer");
  }

  /**
   * Checks if a component extends Agent.
   *
   * @param element the component class to check
   * @return true if the component extends Agent, false otherwise
   */
  private static boolean isAgent(TypeElement element) {
    return extendsClass(element, "akka.javasdk.agent.Agent");
  }

  /**
   * Checks if a component extends EventSourcedEntity.
   *
   * @param element the component class to check
   * @return true if the component extends EventSourcedEntity, false otherwise
   */
  private static boolean isEventSourcedEntity(TypeElement element) {
    return extendsClass(element, "akka.javasdk.eventsourcedentity.EventSourcedEntity");
  }

  /**
   * Checks if a component extends a specific class.
   *
   * @param element the component class to check
   * @param className the fully qualified class name to check for
   * @return true if the component extends the specified class
   */
  private static boolean extendsClass(TypeElement element, String className) {
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
  private static Validation hasEffectMethod(TypeElement element, String... effectTypeNames) {

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

  private static Validation commandHandlerArityShouldBeZeroOrOne(
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
   * Validates that a Consumer component has a @Consume annotation.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation hasConsumeAnnotation(TypeElement element) {
    // Check for any annotation that starts with "akka.javasdk.annotations.Consume"
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      String annotationType = mirror.getAnnotationType().toString();
      if (annotationType.startsWith("akka.javasdk.annotations.Consume")) {
        return Validation.Valid.instance();
      }
    }

    return Validation.of(
        errorMessage(element, "A Consumer must be annotated with `@Consume` annotation."));
  }

  /**
   * Validates AgentDescription annotation configuration. Checks for conflicts with @Component
   * and @AgentRole annotations.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation mustHaveValidAgentDescription(TypeElement element) {
    AnnotationMirror agentDescAnn =
        findAnnotation(element, "akka.javasdk.annotations.AgentDescription");

    // If no @AgentDescription, validation passes (it's optional)
    if (agentDescAnn == null) {
      return Validation.Valid.instance();
    }

    List<String> errors = new ArrayList<>();

    AnnotationMirror componentAnn = findAnnotation(element, "akka.javasdk.annotations.Component");
    AnnotationMirror agentRoleAnn = findAnnotation(element, "akka.javasdk.annotations.AgentRole");

    // @AgentDescription being used together with @Component
    if (componentAnn != null) {
      String componentName = getAnnotationValue(componentAnn, "name");
      String componentDesc = getAnnotationValue(componentAnn, "description");

      if (componentName != null && !componentName.isEmpty()) {
        errors.add(
            errorMessage(
                element,
                "Both @AgentDescription.name and @Component.name are defined. "
                    + "Remove @AgentDescription.name and use only @Component.name."));
      }

      if (componentDesc != null && !componentDesc.isEmpty()) {
        errors.add(
            errorMessage(
                element,
                "Both @AgentDescription.description and @Component.description are defined. "
                    + "Remove @AgentDescription.description and use only @Component.description."));
      }
    } else {
      // @Component is not being used, check if @AgentDescription is properly configured
      String agentDescName = getAnnotationValue(agentDescAnn, "name");
      String agentDescDescription = getAnnotationValue(agentDescAnn, "description");

      if (agentDescName == null || agentDescName.isBlank()) {
        errors.add(
            errorMessage(
                element,
                "@AgentDescription.name is empty. "
                    + "Remove @AgentDescription annotation and use only @Component."));
      }

      if (agentDescDescription == null || agentDescDescription.isBlank()) {
        errors.add(
            errorMessage(
                element,
                "@AgentDescription.description is empty."
                    + "Remove @AgentDescription annotation and use only @Component."));
      }
    }

    // @AgentDescription being used together with @AgentRole
    if (agentRoleAnn != null) {
      String agentDescRole = getAnnotationValue(agentDescAnn, "role");
      if (agentDescRole != null && !agentDescRole.isBlank()) {
        errors.add(
            errorMessage(
                element,
                "Both @AgentDescription.role and @AgentRole are defined. "
                    + "Remove @AgentDescription.role and use only @AgentRole."));
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates that an Agent has exactly one command handler.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation agentCommandHandlersMustBeOne(TypeElement element) {
    int count = 0;

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        // Check for Agent.Effect or Agent.StreamEffect
        if (returnTypeName.startsWith("akka.javasdk.agent.Agent.Effect")
            || returnTypeName.startsWith("akka.javasdk.agent.Agent.StreamEffect")) {
          count++;
        }
      }
    }

    if (count == 1) {
      return Validation.Valid.instance();
    } else {
      return Validation.of(
          errorMessage(
              element,
              element.getSimpleName()
                  + " has "
                  + count
                  + " command handlers. There must be one public method returning Agent.Effect."));
    }
  }

  // ==================== EventSourcedEntity Validation Methods ====================

  /**
   * Validates that the EventSourcedEntity event type parameter is a sealed interface.
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
    java.util.Map<String, List<ExecutableElement>> handlersByName = new java.util.HashMap<>();

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
    for (java.util.Map.Entry<String, List<ExecutableElement>> entry : handlersByName.entrySet()) {
      if (entry.getValue().size() > 1) {
        String methodName = entry.getKey();
        int count = entry.getValue().size();
        errors.add(
            errorMessage(
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
   * Finds an annotation on an element by its fully qualified name.
   *
   * @param element the element to search
   * @param annotationName the fully qualified annotation name
   * @return the AnnotationMirror if found, null otherwise
   */
  private static AnnotationMirror findAnnotation(Element element, String annotationName) {
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
  private static String getAnnotationValue(AnnotationMirror annotation, String attributeName) {
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
  private static String errorMessage(Element element, String message) {
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
   * Validates that only one type-level subscription is present.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation typeLevelSubscriptionValidation(TypeElement element) {
    int subscriptionCount = 0;
    if (hasKeyValueEntitySubscription(element)) subscriptionCount++;
    if (hasWorkflowSubscription(element)) subscriptionCount++;
    if (hasEventSourcedEntitySubscription(element)) subscriptionCount++;
    if (hasStreamSubscription(element)) subscriptionCount++;
    if (hasTopicSubscription(element)) subscriptionCount++;

    if (subscriptionCount > 1) {
      return Validation.of(
          errorMessage(element, "Only one subscription type is allowed on a type level."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that subscription methods have exactly one parameter (unless marked
   * with @DeleteHandler).
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation subscriptionMethodMustHaveOneParameter(
      TypeElement element, String effectTypeName) {
    if (!hasSubscription(element)) {
      return Validation.Valid.instance();
    }

    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if (returnTypeName.equals(effectTypeName)) {
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
  private static Validation noSubscriptionMethodWithAcl(
      TypeElement element, String effectTypeName) {
    if (!hasSubscription(element)) {
      return Validation.Valid.instance();
    }

    List<String> errors = new ArrayList<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if (returnTypeName.equals(effectTypeName) && hasAcl(method)) {
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
   * Validates that topic publication requires a subscription source.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation topicPublicationValidations(TypeElement element) {
    if (hasTopicPublication(element) && !hasSubscription(element)) {
      return Validation.of(
          errorMessage(
              element,
              "You must select a source for @Produce.ToTopic. Annotate this class with one a"
                  + " @Consume annotation."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that @Produce.ServiceStream has a non-empty id.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation publishStreamIdMustBeFilled(TypeElement element) {
    AnnotationMirror serviceStreamAnn =
        findAnnotation(element, "akka.javasdk.annotations.Produce.ServiceStream");

    if (serviceStreamAnn != null) {
      String streamId = getAnnotationValue(serviceStreamAnn, "id");
      if (streamId == null || streamId.isBlank()) {
        return Validation.of("@Produce.ServiceStream id can not be an empty string");
      }
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates ambiguous handlers for subscriptions. Checks that there are no multiple handlers for
   * the same input type.
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation ambiguousHandlerValidations(
      TypeElement element, String effectTypeName) {
    if (!hasSubscription(element)) {
      return Validation.Valid.instance();
    }

    // Group handlers by their last parameter type
    java.util.Map<String, List<ExecutableElement>> handlersByType = new java.util.HashMap<>();

    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if (returnTypeName.equals(effectTypeName)) {
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

  /**
   * Validates state subscription rules for KeyValueEntity and Workflow subscriptions. Ensures: -
   * Delete handlers have zero parameters - Only one update method is allowed - Only one delete
   * handler is allowed
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation commonStateSubscriptionValidation(
      TypeElement element, String effectTypeName) {
    List<ExecutableElement> subscriptionMethods = new ArrayList<>();
    List<ExecutableElement> updateMethods = new ArrayList<>();
    List<ExecutableElement> deleteHandlers = new ArrayList<>();
    List<ExecutableElement> deleteHandlersWithParams = new ArrayList<>();

    // Collect subscription methods
    for (Element enclosed : element.getEnclosedElements()) {
      if (enclosed instanceof ExecutableElement method) {
        String returnTypeName = method.getReturnType().toString();
        if (returnTypeName.equals(effectTypeName)) {
          subscriptionMethods.add(method);

          if (hasHandleDeletes(method)) {
            if (method.getParameters().isEmpty()) {
              deleteHandlers.add(method);
            } else {
              deleteHandlersWithParams.add(method);
            }
          } else {
            updateMethods.add(method);
          }
        }
      }
    }

    List<String> errors = new ArrayList<>();

    // Validate delete handlers must have zero arity
    for (ExecutableElement method : deleteHandlersWithParams) {
      int numParams = method.getParameters().size();
      errors.add(
          errorMessage(
              method,
              "Method annotated with '@DeleteHandler' must not have parameters. Found "
                  + numParams
                  + " method parameters."));
    }

    // Validate only one update method is allowed
    if (updateMethods.size() >= 2) {
      List<String> methodNames =
          updateMethods.stream().map(m -> m.getSimpleName().toString()).toList();
      errors.add(
          errorMessage(
              element,
              "Duplicated update methods ["
                  + String.join(", ", methodNames)
                  + "] for state subscription are not allowed."));
    }

    // Validate only one delete handler is allowed
    if (deleteHandlers.size() >= 2) {
      for (ExecutableElement method : deleteHandlers) {
        errors.add(
            errorMessage(
                method, "Multiple methods annotated with @DeleteHandler are not allowed."));
      }
    }

    return Validation.of(errors);
  }

  /**
   * Validates KeyValueEntity subscription-specific rules.
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation valueEntitySubscriptionValidations(
      TypeElement element, String effectTypeName) {
    if (!hasKeyValueEntitySubscription(element)) {
      return Validation.Valid.instance();
    }

    return commonStateSubscriptionValidation(element, effectTypeName);
  }

  /**
   * Validates Workflow subscription-specific rules.
   *
   * @param element the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation workflowSubscriptionValidations(
      TypeElement element, String effectTypeName) {
    if (!hasWorkflowSubscription(element)) {
      return Validation.Valid.instance();
    }

    return commonStateSubscriptionValidation(element, effectTypeName);
  }

  // ==================== Subscription Detection Helpers ====================

  /**
   * Checks if a component has a KeyValueEntity subscription.
   *
   * @param element the component class to check
   * @return true if the component has @Consume.FromKeyValueEntity
   */
  private static boolean hasKeyValueEntitySubscription(TypeElement element) {
    return findAnnotation(element, "akka.javasdk.annotations.Consume.FromKeyValueEntity") != null;
  }

  /**
   * Checks if a component has a Workflow subscription.
   *
   * @param element the component class to check
   * @return true if the component has @Consume.FromWorkflow
   */
  private static boolean hasWorkflowSubscription(TypeElement element) {
    return findAnnotation(element, "akka.javasdk.annotations.Consume.FromWorkflow") != null;
  }

  /**
   * Checks if a component has an EventSourcedEntity subscription.
   *
   * @param element the component class to check
   * @return true if the component has @Consume.FromEventSourcedEntity
   */
  private static boolean hasEventSourcedEntitySubscription(TypeElement element) {
    return findAnnotation(element, "akka.javasdk.annotations.Consume.FromEventSourcedEntity")
        != null;
  }

  /**
   * Checks if a component has a Stream subscription.
   *
   * @param element the component class to check
   * @return true if the component has @Consume.FromServiceStream
   */
  private static boolean hasStreamSubscription(TypeElement element) {
    return findAnnotation(element, "akka.javasdk.annotations.Consume.FromServiceStream") != null;
  }

  /**
   * Checks if a component has a Topic subscription.
   *
   * @param element the component class to check
   * @return true if the component has @Consume.FromTopic
   */
  private static boolean hasTopicSubscription(TypeElement element) {
    return findAnnotation(element, "akka.javasdk.annotations.Consume.FromTopic") != null;
  }

  /**
   * Checks if a component has any subscription annotation.
   *
   * @param element the component class to check
   * @return true if the component has any @Consume annotation
   */
  private static boolean hasSubscription(TypeElement element) {
    return hasKeyValueEntitySubscription(element)
        || hasWorkflowSubscription(element)
        || hasEventSourcedEntitySubscription(element)
        || hasTopicSubscription(element)
        || hasStreamSubscription(element);
  }

  /**
   * Checks if a component has a Topic publication annotation.
   *
   * @param element the component class to check
   * @return true if the component has @Produce.ToTopic
   */
  private static boolean hasTopicPublication(TypeElement element) {
    return findAnnotation(element, "akka.javasdk.annotations.Produce.ToTopic") != null;
  }

  /**
   * Checks if a method has a @DeleteHandler annotation.
   *
   * @param method the method to check
   * @return true if the method has @DeleteHandler
   */
  private static boolean hasHandleDeletes(ExecutableElement method) {
    return findAnnotation(method, "akka.javasdk.annotations.DeleteHandler") != null;
  }

  /**
   * Checks if a method has an ACL annotation.
   *
   * @param method the method to check
   * @return true if the method has @Acl
   */
  private static boolean hasAcl(ExecutableElement method) {
    return findAnnotation(method, "akka.javasdk.annotations.Acl") != null;
  }
}
