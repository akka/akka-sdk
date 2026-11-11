/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.impl.evaluation.AgentEvaluatorBindings;
import com.typesafe.config.Config;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The evaluators bound to the agent under test.
 *
 * @param agentComponentId the component id of the agent
 * @param bindings the enabled bindings, sorted by evaluator component id
 */
record EvaluatorBindings(String agentComponentId, List<Binding> bindings) {

  /**
   * @param evaluator the evaluator component id
   * @param samplingRatio the share of the agent's interactions the evaluator evaluates
   * @param triggerOnFailure whether the evaluator also evaluates a failed interaction
   */
  record Binding(String evaluator, double samplingRatio, boolean triggerOnFailure) {}

  EvaluatorBindings {
    if (agentComponentId == null || agentComponentId.isBlank())
      throw new IllegalArgumentException("agent component id required");
    bindings = List.copyOf(bindings);
  }

  /** The bindings of the agent a method reference belongs to, from the application config. */
  static EvaluatorBindings of(Config config, Object methodRef) {
    var agentClass = AgentEvaluatorBindings.agentClass(methodRef);
    return new EvaluatorBindings(
        AgentEvaluatorBindings.agentComponentId(agentClass),
        AgentEvaluatorBindings.bindings(config, agentClass).stream()
            .map(
                b -> new Binding(b.evaluatorComponentId(), b.samplingRatio(), b.triggerOnFailure()))
            .toList());
  }

  /**
   * The evaluators that evaluate an interaction of the agent, in binding order. Only the ones that
   * also evaluate a failed interaction when {@code failed} is true.
   */
  List<String> expectedEvaluators(boolean failed) {
    return bindings.stream()
        .filter(binding -> !failed || binding.triggerOnFailure())
        .map(Binding::evaluator)
        .toList();
  }

  // The runner waits for one evaluation per bound evaluator, which a sampled binding may never
  // record.
  void requireFullSampling() {
    var sampled =
        bindings.stream()
            .filter(binding -> binding.samplingRatio() < 1.0)
            .map(
                binding ->
                    binding.evaluator()
                        + " ("
                        + String.format(Locale.ROOT, "%.2f", binding.samplingRatio())
                        + ")")
            .collect(Collectors.joining(", "));
    if (!sampled.isEmpty()) {
      throw new IllegalArgumentException(
          "Evaluators bound to agent ["
              + agentComponentId
              + "] with a sampling ratio below 1.0: "
              + sampled
              + ". An experiment expects every bound evaluator to evaluate every attempt, so set"
              + " sampling-ratio = 1.0 on these bindings in the test configuration, or disable"
              + " them with enabled = false.");
    }
  }

  // The runner's own results are named target, guardrail and expected, so an evaluator with one of
  // these ids would merge with them in the report.
  void requireNoRunnerName() {
    for (var binding : bindings) {
      if (binding.evaluator().equals(ExperimentRunner.TARGET))
        throw reservedName(binding.evaluator(), "its own result on a failed agent call");
      if (binding.evaluator().equals(ExperimentRunner.GUARDRAIL))
        throw reservedName(
            binding.evaluator(), "its own result on a denial that stopped the interaction");
      if (binding.evaluator().equals(ExperimentRunner.EXPECTED))
        throw reservedName(
            binding.evaluator(), "the comparison of the reply with the expected reply");
    }
  }

  private IllegalArgumentException reservedName(String evaluator, String use) {
    return new IllegalArgumentException(
        "An evaluator bound to agent ["
            + agentComponentId
            + "] has the component id ["
            + evaluator
            + "], which the runner uses for "
            + use
            + ". Give the evaluator another id.");
  }

  // A binding to an evaluator id without a component never records an evaluation, so every attempt
  // would wait for the timeout and end inconclusive.
  void requireEvaluatorComponents(List<String> evaluatorComponentIds) {
    var unknown =
        bindings.stream()
            .map(Binding::evaluator)
            .filter(evaluator -> !evaluatorComponentIds.contains(evaluator))
            .toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "Evaluators bound to agent ["
              + agentComponentId
              + "] without an evaluator component: "
              + unknown
              + ". The evaluator components of the service are "
              + evaluatorComponentIds
              + ". Bind the agent under the component id of an evaluator, or add the evaluator"
              + " to the service.");
    }
  }

  /**
   * Why no evaluator is bound, with the agents the configuration does bind. Empty when an evaluator
   * is bound.
   *
   * @param graded whether the cases carry an expected reply, so the comparison judges the attempts
   */
  Optional<String> describeUnbound(List<String> boundAgentComponentIds, boolean graded) {
    if (!bindings.isEmpty()) return Optional.empty();
    var consequence =
        graded
            ? "so only the comparison with the expected reply judges every attempt. "
            : "so every attempt is inconclusive unless the gate reads only the guardrail"
                + " decisions or the agent call. ";
    var others =
        boundAgentComponentIds.isEmpty()
            ? "The configuration binds no evaluator to any agent under"
                + " akka.javasdk.evaluation.evaluators."
            : "The configuration binds evaluators to the agents "
                + boundAgentComponentIds
                + ", under akka.javasdk.evaluation.evaluators.<evaluator>.agents.";
    return Optional.of(
        "No evaluator is bound to agent [" + agentComponentId + "], " + consequence + others);
  }
}
