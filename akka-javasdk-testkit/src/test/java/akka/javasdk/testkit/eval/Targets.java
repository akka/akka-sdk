/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.Failure;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.ledger.InteractionMetadata;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.ModelConfig;
import akka.javasdk.ledger.ModelResponse;
import akka.javasdk.ledger.ToolCall;
import akka.javasdk.testkit.TestLedgerClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Scripted targets, ledger records and bindings for the runner's and the gate's tests. */
final class Targets {

  private Targets() {}

  static final String AGENT = "support-agent";
  static final String QUALITY = "quality";
  static final String TONE = "tone";
  static final Instant STARTED = Instant.parse("2026-10-01T10:15:30Z");

  /** Bindings of the agent to the given evaluators, fully sampled, not on failure. */
  static AgentBindings bindings(String... evaluators) {
    return new AgentBindings(
        AGENT,
        Arrays.stream(evaluators).map(e -> new AgentBindings.Binding(e, 1.0, false)).toList());
  }

  /** The interaction one turn recorded, with the reply and the tool calls. */
  static InteractionRecord interaction(
      String interactionId, String sessionId, String reply, List<ToolCall> toolCalls) {
    return interaction(interactionId, sessionId, reply, toolCalls, Optional.empty(), 120, 30, 800);
  }

  static InteractionRecord interaction(
      String interactionId,
      String sessionId,
      String reply,
      List<ToolCall> toolCalls,
      Optional<Failure> failure,
      int inputTokens,
      int outputTokens,
      long latencyMs) {
    var responses = new ArrayList<ModelResponse>();
    if (!toolCalls.isEmpty()) {
      responses.add(
          new ModelResponse("m1", "", new Agent.TokenUsage(inputTokens, 0), "", toolCalls));
    }
    responses.add(
        new ModelResponse(
            "m2",
            reply,
            new Agent.TokenUsage(toolCalls.isEmpty() ? inputTokens : 0, outputTokens),
            "",
            List.of()));
    return new InteractionRecord(
        interactionId,
        sessionId,
        AGENT,
        Optional.empty(),
        new InteractionMetadata(
            new ModelConfig("openai", "gpt-4o", "", -1.0, -1.0, -1, -1),
            Map.of(),
            STARTED,
            STARTED.plusMillis(latencyMs),
            InteractionMetadata.FinishReason.STOP),
        "You are a support agent.",
        List.of(new MessageContent.TextMessageContent("Where is o_42?")),
        responses,
        List.of(),
        Optional.empty(),
        failure,
        STARTED.plusMillis(latencyMs));
  }

  static ToolCall toolCall(String name, String arguments, String response) {
    return new ToolCall("call-" + name, name, arguments, response);
  }

  static GuardrailDecisionRecord guardrail(
      InteractionRecord interaction,
      String name,
      GuardrailDecisionRecord.Decision decision,
      String explanation) {
    return new GuardrailDecisionRecord(
        "guardrail-" + interaction.interactionId() + "-" + name,
        AGENT,
        interaction.interactionId(),
        interaction.sessionId(),
        Optional.empty(),
        name,
        Optional.empty(),
        "policy",
        GuardrailDecisionRecord.Boundary.BEFORE_AGENT_RESPONSE,
        decision,
        false,
        explanation,
        Duration.ofMillis(5),
        Optional.empty(),
        Optional.empty(),
        GuardrailDecisionRecord.ToolOrigin.UNSPECIFIED,
        Optional.empty(),
        STARTED.plusMillis(700));
  }

  static EvaluationRecord verdict(String interactionId, String evaluator, Evaluation evaluation) {
    return evaluation(interactionId, evaluator, new EvaluationRecord.Outcome.Verdict(evaluation));
  }

  static EvaluationRecord inconclusive(String interactionId, String evaluator, String reason) {
    return evaluation(interactionId, evaluator, new EvaluationRecord.Outcome.Inconclusive(reason));
  }

  static EvaluationRecord evaluation(
      String interactionId, String evaluator, EvaluationRecord.Outcome outcome) {
    return new EvaluationRecord(
        "evaluation-" + interactionId + "-" + evaluator,
        evaluator,
        Optional.empty(),
        EvaluationRecord.Trigger.ON_INTERACTION,
        interactionId,
        AGENT,
        outcome,
        STARTED.plusSeconds(1));
  }

  /**
   * A target that, per turn, seeds the ledger through {@code seed} and answers with the interaction
   * id: the case id followed by the call number, counted from 1 over all cases and runs.
   */
  static EvalTarget<String> scripted(BiConsumer<EvalTarget.Turn<String>, String> seed) {
    var calls = new AtomicInteger();
    return turn -> {
      var interactionId = turn.caseId() + "-" + calls.incrementAndGet();
      seed.accept(turn, interactionId);
      return EvalTarget.Outcome.answered(interactionId);
    };
  }

  /** A target that records a reply and a passing verdict of every given evaluator. */
  static EvalTarget<String> passing(TestLedgerClient ledger, String reply, String... evaluators) {
    return scripted(
        (turn, interactionId) -> {
          ledger.seed(interaction(interactionId, turn.sessionId(), reply, List.of()));
          for (var evaluator : evaluators) {
            ledger.seed(verdict(interactionId, evaluator, Evaluation.passed("fine")));
          }
        });
  }

  /**
   * A target that records the case id as the reply and a verdict of the evaluator that passes when
   * {@code passes} holds for the call number, counted from 1 over all cases and runs, and the case
   * id.
   */
  static EvalTarget<String> passingWhen(
      TestLedgerClient ledger, String evaluator, BiFunction<Integer, String, Boolean> passes) {
    var calls = new AtomicInteger();
    return scripted(
        (turn, interactionId) -> {
          var passed = passes.apply(calls.incrementAndGet(), turn.caseId());
          ledger.seed(interaction(interactionId, turn.sessionId(), turn.caseId(), List.of()));
          ledger.seed(
              verdict(interactionId, evaluator, Evaluation.of(passed, passed ? "fine" : "wrong")));
        });
  }
}
