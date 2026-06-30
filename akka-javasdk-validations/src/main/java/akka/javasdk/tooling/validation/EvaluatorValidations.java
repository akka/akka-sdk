/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.validation;

import static akka.javasdk.tooling.validation.Validations.hasEffectMethod;
import static akka.javasdk.tooling.validation.Validations.strictlyPublicCommandHandlerArityShouldBeZeroOrOne;

import akka.javasdk.validation.ast.TypeDef;

/** Contains validation logic specific to Evaluator components. */
public class EvaluatorValidations {

  private static final String EVALUATOR_TYPE = "akka.javasdk.evaluation.Evaluator";
  private static final String EFFECT_TYPE = "akka.javasdk.evaluation.Evaluator.Effect";
  private static final String EVALUATES_AGENT_ANNOTATION =
      "akka.javasdk.annotations.EvaluatesAgent";
  private static final String EVALUATES_AGENTS_ANNOTATION =
      "akka.javasdk.annotations.EvaluatesAgents";

  /**
   * Validates an Evaluator component.
   *
   * @param typeDef the Evaluator class to validate
   * @return a Validation result indicating success or failure with error messages
   */
  public static Validation validate(TypeDef typeDef) {
    if (!typeDef.extendsType(EVALUATOR_TYPE)) {
      return Validation.Valid.instance();
    }

    return hasEffectMethod(typeDef, EFFECT_TYPE)
        .combine(strictlyPublicCommandHandlerArityShouldBeZeroOrOne(typeDef, EFFECT_TYPE))
        .combine(mustEvaluateAtLeastOneAgent(typeDef));
  }

  /**
   * Validates that an Evaluator declares at least one binding. Currently the only binding is
   * {@code @EvaluatesAgent} — without it the evaluator can never be triggered.
   *
   * <p>TODO(governance): relax once non-agent / manually-triggered bindings are supported.
   *
   * @param typeDef the Evaluator class to validate
   * @return a Validation result indicating success or failure
   */
  private static Validation mustEvaluateAtLeastOneAgent(TypeDef typeDef) {
    // a single @EvaluatesAgent appears directly; repeated ones appear via the @EvaluatesAgents
    // container
    boolean hasBinding =
        typeDef.hasAnnotation(EVALUATES_AGENT_ANNOTATION)
            || typeDef.hasAnnotation(EVALUATES_AGENTS_ANNOTATION);

    if (hasBinding) {
      return Validation.Valid.instance();
    }

    return Validation.of(
        Validations.errorMessage(
            typeDef,
            "An Evaluator must evaluate at least one agent. Annotate it with @EvaluatesAgent."));
  }
}
