/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.impl.evaluation.AgentEvaluatorBindings;
import com.typesafe.config.Config;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The agent under test and the evaluators bound to it.
 *
 * @param agentComponentId the component id of the agent
 * @param bindings the enabled bindings, sorted by evaluator component id
 */
record AgentBindings(String agentComponentId, List<Binding> bindings) {

  /**
   * @param evaluator the evaluator component id
   * @param samplingRatio the share of the agent's interactions the evaluator evaluates
   * @param triggerOnFailure whether the evaluator also evaluates a failed interaction
   */
  record Binding(String evaluator, double samplingRatio, boolean triggerOnFailure) {}

  AgentBindings {
    if (agentComponentId == null || agentComponentId.isBlank())
      throw new IllegalArgumentException("agent component id required");
    bindings = List.copyOf(bindings);
  }

  /** The bindings of the agent a method reference belongs to, from the application config. */
  static AgentBindings of(Config config, Object methodRef) {
    var agentClass = AgentEvaluatorBindings.agentClass(methodRef);
    return new AgentBindings(
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
}
