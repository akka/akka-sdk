/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import akka.javasdk.annotations.Component;
import java.time.Duration;

/**
 * Example workflow evaluator used to validate the API ergonomics and drive the unit test: a
 * multi-step evaluation that fetches the transcript, then judges it, and finishes with a verdict or
 * an inconclusive report.
 */
@Component(id = "transcript-quality-evaluator")
public class TranscriptQualityEvaluator
    extends WorkflowEvaluator<TranscriptQualityEvaluator.State> {

  public record State(String transcript) {}

  public record JudgeInstruction(int minWords) {}

  /** Interaction id used by tests to exercise the inconclusive path. */
  public static final String EMPTY_INTERACTION_ID = "empty-interaction";

  @Override
  public Settings settings() {
    return Settings.defaults().withEvaluationTimeout(Duration.ofMinutes(10)).withMaxStepRetries(2);
  }

  @Override
  public Effect onEvaluation(EvaluationContext context) {
    return effects().transitionTo(TranscriptQualityEvaluator::fetchTranscript);
  }

  private Effect fetchTranscript() {
    // stand-in for fetching the interaction records of the subject, e.g. via the ledger client
    var interactionId = ((Subject.Interaction) evaluationContext().subject()).interactionId();
    var transcript =
        interactionId.equals(EMPTY_INTERACTION_ID)
            ? ""
            : "user: hi there\nassistant: hello, how can I help you today?";

    if (transcript.isBlank()) {
      return effects().inconclusive("no transcript for interaction " + interactionId);
    }
    return effects()
        .updateState(new State(transcript))
        .transitionTo(TranscriptQualityEvaluator::judge)
        .withInput(new JudgeInstruction(5));
  }

  private Effect judge(JudgeInstruction instruction) {
    // stand-in for an LLM-as-judge call, e.g. via the component client
    var wordCount = currentState().transcript().split("\\s+").length;
    var passed = wordCount >= instruction.minWords();
    return effects()
        .complete(
            Evaluation.of(passed, passed ? "transcript detailed enough" : "transcript too short")
                .withScore(Math.min(1.0, wordCount / 10.0)));
  }
}
