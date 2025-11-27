/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import static akka.javasdk.tooling.validation.Validations.ambiguousHandlerValidations;
import static akka.javasdk.tooling.validation.Validations.hasEffectMethod;
import static akka.javasdk.tooling.validation.Validations.missingHandlerValidations;
import static akka.javasdk.tooling.validation.Validations.noSubscriptionMethodWithAcl;
import static akka.javasdk.tooling.validation.Validations.strictlyPublicCommandHandlerArityShouldBeZeroOrOne;
import static akka.javasdk.tooling.validation.Validations.subscriptionMethodMustHaveOneParameter;

import akka.javasdk.validation.ast.AnnotationDef;
import akka.javasdk.validation.ast.MethodDef;
import akka.javasdk.validation.ast.TypeDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Contains validation logic specific to Consumer components. */
public class ConsumerValidations {

  /**
   * Validates a Consumer component.
   *
   * @param typeDef the Consumer class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType("akka.javasdk.consumer.Consumer")) {
      return Validation.Valid.instance();
    }

    String effectType = "akka.javasdk.consumer.Consumer.Effect";
    return hasEffectMethod(typeDef, effectType)
        .combine(hasConsumeAnnotation(typeDef))
        .combine(typeLevelSubscriptionValidation(typeDef))
        .combine(valueEntitySubscriptionValidations(typeDef, effectType))
        .combine(workflowSubscriptionValidations(typeDef, effectType))
        .combine(topicPublicationValidations(typeDef))
        .combine(publishStreamIdMustBeFilled(typeDef))
        .combine(ambiguousHandlerValidations(typeDef, effectType))
        .combine(strictlyPublicCommandHandlerArityShouldBeZeroOrOne(typeDef, effectType))
        .combine(missingHandlerValidations(typeDef, effectType))
        .combine(noSubscriptionMethodWithAcl(typeDef, effectType))
        .combine(subscriptionMethodMustHaveOneParameter(typeDef, effectType))
        .combine(consumerCannotHaveFunctionTools(typeDef));
  }

  /**
   * Validates that a Consumer component has a @Consume annotation.
   *
   * @param typeDef the component class to validate
   * @return a Validation result indicating success or failure
   */
  static Validation hasConsumeAnnotation(TypeDef typeDef) {
    // Check for any annotation that starts with "akka.javasdk.annotations.Consume"
    if (typeDef.hasAnnotationStartingWith("akka.javasdk.annotations.Consume")) {
      return Validation.Valid.instance();
    }

    return Validation.of(
        Validations.errorMessage(
            typeDef, "A Consumer must be annotated with `@Consume` annotation."));
  }

  /**
   * Validates that a Consumer has only one type of subscription at type level.
   *
   * @param typeDef the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation typeLevelSubscriptionValidation(TypeDef typeDef) {
    int subscriptionCount = 0;
    if (Validations.hasKeyValueEntitySubscription(typeDef)) subscriptionCount++;
    if (Validations.hasWorkflowSubscription(typeDef)) subscriptionCount++;
    if (Validations.hasEventSourcedEntitySubscription(typeDef)) subscriptionCount++;
    if (hasStreamSubscription(typeDef)) subscriptionCount++;
    if (hasTopicSubscription(typeDef)) subscriptionCount++;

    if (subscriptionCount > 1) {
      return Validation.of(
          Validations.errorMessage(
              typeDef, "Only one subscription type is allowed on a type level."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that topic publication requires a subscription source.
   *
   * @param typeDef the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation topicPublicationValidations(TypeDef typeDef) {
    if (hasTopicPublication(typeDef) && Validations.doesNotHaveSubscription(typeDef)) {
      return Validation.of(
          Validations.errorMessage(
              typeDef,
              "You must select a source for @Produce.ToTopic. Annotate this class with one a"
                  + " @Consume annotation."));
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates that @Produce.ServiceStream has a non-empty id.
   *
   * @param typeDef the component class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation publishStreamIdMustBeFilled(TypeDef typeDef) {
    Optional<AnnotationDef> serviceStreamAnn =
        typeDef.findAnnotation("akka.javasdk.annotations.Produce.ServiceStream");

    if (serviceStreamAnn.isPresent()) {
      Optional<String> streamId = serviceStreamAnn.get().getStringValue("id");
      if (streamId.isEmpty() || streamId.get().isBlank()) {
        return Validation.of("@Produce.ServiceStream id can not be an empty string");
      }
    }

    return Validation.Valid.instance();
  }

  /**
   * Validates KeyValueEntity subscription-specific rules.
   *
   * @param typeDef the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation valueEntitySubscriptionValidations(
      TypeDef typeDef, String effectTypeName) {
    if (!Validations.hasKeyValueEntitySubscription(typeDef)) {
      return Validation.Valid.instance();
    }

    return commonStateSubscriptionValidation(typeDef, effectTypeName);
  }

  /**
   * Validates Workflow subscription-specific rules.
   *
   * @param typeDef the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation workflowSubscriptionValidations(
      TypeDef typeDef, String effectTypeName) {
    if (!Validations.hasWorkflowSubscription(typeDef)) {
      return Validation.Valid.instance();
    }

    return commonStateSubscriptionValidation(typeDef, effectTypeName);
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
   * @param typeDef the component class to validate
   * @param effectTypeName the effect type name to identify subscription methods
   * @return a Validation result indicating success or failure
   */
  private static Validation commonStateSubscriptionValidation(
      TypeDef typeDef, String effectTypeName) {
    List<MethodDef> updateMethods = new ArrayList<>();
    List<MethodDef> deleteHandlers = new ArrayList<>();
    List<MethodDef> deleteHandlersWithParams = new ArrayList<>();

    // Collect subscription methods
    for (MethodDef method : typeDef.getPublicMethods()) {
      String returnTypeName = method.getReturnType().getQualifiedName();
      if (returnTypeName.equals(effectTypeName)) {

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

    List<String> errors = new ArrayList<>();

    // Validate delete handlers must have zero arity
    for (MethodDef method : deleteHandlersWithParams) {
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
      List<String> methodNames = updateMethods.stream().map(MethodDef::getName).toList();
      errors.add(
          Validations.errorMessage(
              typeDef,
              "Duplicated update methods ["
                  + String.join(", ", methodNames)
                  + "] for state subscription are not allowed."));
    }

    // Validate only one delete handler is allowed
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
   * Checks if a component has a Stream subscription.
   *
   * @param typeDef the component class to check
   * @return true if the component has @Consume.FromServiceStream
   */
  private static boolean hasStreamSubscription(TypeDef typeDef) {
    return typeDef.hasAnnotation("akka.javasdk.annotations.Consume.FromServiceStream");
  }

  /**
   * Checks if a component has a Topic subscription.
   *
   * @param typeDef the component class to check
   * @return true if the component has @Consume.FromTopic
   */
  private static boolean hasTopicSubscription(TypeDef typeDef) {
    return typeDef.hasAnnotation("akka.javasdk.annotations.Consume.FromTopic");
  }

  /**
   * Checks if a component has a Topic publication annotation.
   *
   * @param typeDef the component class to check
   * @return true if the component has @Produce.ToTopic
   */
  private static boolean hasTopicPublication(TypeDef typeDef) {
    return typeDef.hasAnnotation("akka.javasdk.annotations.Produce.ToTopic");
  }

  /**
   * Validates that Consumer methods are not annotated with @FunctionTool.
   *
   * @param typeDef the Consumer class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation consumerCannotHaveFunctionTools(TypeDef typeDef) {
    List<String> errors = new ArrayList<>();

    for (MethodDef method : typeDef.getMethods()) {
      if (method.hasAnnotation("akka.javasdk.annotations.FunctionTool")) {
        errors.add(
            Validations.errorMessage(
                method, "Consumer methods cannot be annotated with @FunctionTool."));
      }
    }

    return Validation.of(errors);
  }
}
