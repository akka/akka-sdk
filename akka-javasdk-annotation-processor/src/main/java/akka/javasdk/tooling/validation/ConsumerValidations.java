/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Contains validation logic specific to Consumer components. */
public class ConsumerValidations {

  /**
   * Validates a Consumer component.
   *
   * @param element the Consumer class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeElement element) {
    if (!Validations.extendsClass(element, "akka.javasdk.consumer.Consumer")) {
      return Validation.Valid.instance();
    }

    String effectType = "akka.javasdk.consumer.Consumer.Effect";
    return Validations.hasEffectMethod(element, effectType)
        .combine(hasConsumeAnnotation(element))
        .combine(typeLevelSubscriptionValidation(element))
        .combine(valueEntitySubscriptionValidations(element, effectType))
        .combine(workflowSubscriptionValidations(element, effectType))
        .combine(topicPublicationValidations(element))
        .combine(publishStreamIdMustBeFilled(element))
        .combine(Validations.ambiguousHandlerValidations(element, effectType))
        .combine(Validations.commandHandlerArityShouldBeZeroOrOne(element, effectType))
        .combine(Validations.missingHandlerValidations(element, effectType))
        .combine(Validations.noSubscriptionMethodWithAcl(element, effectType))
        .combine(Validations.subscriptionMethodMustHaveOneParameter(element, effectType));
  }

  /**
   * Validates that a Consumer component has a @Consume annotation.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  static Validation hasConsumeAnnotation(TypeElement element) {
    // Check for any annotation that starts with "akka.javasdk.annotations.Consume"
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      String annotationType = mirror.getAnnotationType().toString();
      if (annotationType.startsWith("akka.javasdk.annotations.Consume")) {
        return Validation.Valid.instance();
      }
    }

    return Validation.of(
        Validations.errorMessage(
            element, "A Consumer must be annotated with `@Consume` annotation."));
  }

  /**
   * Validates that a Consumer has only one type of subscription at type level.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation typeLevelSubscriptionValidation(TypeElement element) {
    int subscriptionCount = 0;
    if (Validations.hasKeyValueEntitySubscription(element)) subscriptionCount++;
    if (Validations.hasWorkflowSubscription(element)) subscriptionCount++;
    if (Validations.hasEventSourcedEntitySubscription(element)) subscriptionCount++;
    if (hasStreamSubscription(element)) subscriptionCount++;
    if (hasTopicSubscription(element)) subscriptionCount++;

    if (subscriptionCount > 1) {
      return Validation.of(
          Validations.errorMessage(
              element, "Only one subscription type is allowed on a type level."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that topic publication requires a subscription source.
   *
   * @param element the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation topicPublicationValidations(TypeElement element) {
    if (hasTopicPublication(element) && !Validations.hasSubscription(element)) {
      return Validation.of(
          Validations.errorMessage(
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
        Validations.findAnnotation(element, "akka.javasdk.annotations.Produce.ServiceStream");

    if (serviceStreamAnn != null) {
      String streamId = Validations.getAnnotationValue(serviceStreamAnn, "id");
      if (streamId == null || streamId.isBlank()) {
        return Validation.of("@Produce.ServiceStream id can not be an empty string");
      }
    }

    return Validation.Valid.instance();
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
    if (!Validations.hasKeyValueEntitySubscription(element)) {
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
    if (!Validations.hasWorkflowSubscription(element)) {
      return Validation.Valid.instance();
    }

    return commonStateSubscriptionValidation(element, effectTypeName);
  }

  /**
   * Validates state subscription rules for KeyValueEntity and Workflow subscriptions. Ensures:
   *
   * <ul>
   *   <li>Delete handlers have zero parameters
   *   <li>Only one update method is allowed
   *   <li>Only one delete handler is allowed
   * </ul>
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

          if (Validations.hasHandleDeletes(method)) {
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
          Validations.errorMessage(
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
          Validations.errorMessage(
              element,
              "Duplicated update methods ["
                  + String.join(", ", methodNames)
                  + "] for state subscription are not allowed."));
    }

    // Validate only one delete handler is allowed
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
   * Checks if a component has a Stream subscription.
   *
   * @param element the component class to check
   * @return true if the component has @Consume.FromServiceStream
   */
  private static boolean hasStreamSubscription(TypeElement element) {
    return Validations.findAnnotation(element, "akka.javasdk.annotations.Consume.FromServiceStream")
        != null;
  }

  /**
   * Checks if a component has a Topic subscription.
   *
   * @param element the component class to check
   * @return true if the component has @Consume.FromTopic
   */
  private static boolean hasTopicSubscription(TypeElement element) {
    return Validations.findAnnotation(element, "akka.javasdk.annotations.Consume.FromTopic")
        != null;
  }

  /**
   * Checks if a component has a Topic publication annotation.
   *
   * @param element the component class to check
   * @return true if the component has @Produce.ToTopic
   */
  private static boolean hasTopicPublication(TypeElement element) {
    return Validations.findAnnotation(element, "akka.javasdk.annotations.Produce.ToTopic") != null;
  }
}
