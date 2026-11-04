/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.MessageContent.TextMessageContent;
import akka.javasdk.evaluation.Evaluation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class FlowRecordTest {

  private static InteractionRecord interaction(
      String agentComponentId,
      String systemMessage,
      String input,
      List<ModelResponse> modelResponses,
      List<ToolCallResponse> toolCallResponses,
      Optional<TaskContext> taskContext) {
    return new InteractionRecord(
        "interaction-" + input.hashCode(),
        "session-1",
        agentComponentId,
        Optional.of("flow-1"),
        new InteractionMetadata(
            new ModelConfig("openai", "gpt-4", "", 0.7, 1.0, 0, 1024),
            Map.of(),
            Instant.EPOCH,
            Instant.EPOCH,
            InteractionMetadata.FinishReason.STOP),
        systemMessage,
        List.of(TextMessageContent.from(input)),
        modelResponses,
        toolCallResponses,
        taskContext,
        Optional.empty(),
        Instant.EPOCH);
  }

  private static InteractionRecord interaction(
      String agentComponentId, String systemMessage, String input) {
    return interaction(
        agentComponentId, systemMessage, input, List.of(), List.of(), Optional.empty());
  }

  private static ModelResponse response(String content) {
    return new ModelResponse("m1", content, 10, 5, "", List.of());
  }

  private static GuardrailDecisionRecord decision(
      String name,
      GuardrailDecisionRecord.Boundary boundary,
      Optional<String> toolName,
      GuardrailDecisionRecord.Decision decision,
      String explanation,
      boolean reportOnly,
      GuardrailDecisionRecord.FailMode failMode) {
    return new GuardrailDecisionRecord(
        "guardrail-" + name,
        "coordinator-agent",
        "interaction-1",
        "session-1",
        Optional.of("flow-1"),
        name,
        Optional.empty(),
        "policy",
        boundary,
        decision,
        explanation,
        reportOnly,
        failMode,
        Duration.ZERO,
        toolName.map(
            tool ->
                new GuardrailDecisionRecord.ToolCall(
                    Optional.empty(), tool, new GuardrailDecisionRecord.ToolOrigin.FunctionTool())),
        Instant.EPOCH);
  }

  private static ActivityRecord activity(ActivityRecord.Event... events) {
    return new ActivityRecord(
        "activity-1",
        "coordinator-agent",
        "coordinator-1",
        "flow-1",
        List.of(events),
        Instant.EPOCH);
  }

  @Test
  public void rendersTheBlocksInListOrderAndLeavesOutEvaluations() {
    var coordinator =
        interaction(
            "coordinator-agent",
            "You coordinate research.",
            "Research quantum computing",
            List.of(response("Delegating.")),
            List.of(),
            Optional.of(
                new TaskContext(
                    "coordinator-1",
                    "task-1",
                    "research",
                    "Research quantum computing",
                    "ResearchResult",
                    1,
                    5)));
    var worker =
        interaction(
            "worker-agent",
            "You research.",
            "Research quantum computing fundamentals",
            List.of(response("Qubits enable parallel computation.")),
            List.of(),
            Optional.empty());
    var decision =
        decision(
            "no-refunds",
            GuardrailDecisionRecord.Boundary.BEFORE_TOOL_CALL,
            Optional.of("RefundTools_refund"),
            GuardrailDecisionRecord.Decision.DENY,
            "refunds are not allowed",
            false,
            GuardrailDecisionRecord.FailMode.FAIL_CLOSED);
    var evaluation =
        new EvaluationRecord(
            "evaluation-1",
            "quality-evaluator",
            Optional.empty(),
            EvaluationRecord.Trigger.ON_INTERACTION,
            coordinator.interactionId(),
            "coordinator-agent",
            new EvaluationRecord.Outcome.Verdict(Evaluation.of(false, "verdict of another judge")),
            Instant.EPOCH);
    var delegation =
        activity(
            new ActivityRecord.Event.DelegationStarted(
                List.of("worker-agent"), 1, List.of("subtask-1"), List.of("worker-1")));

    var transcript =
        FlowRecord.transcript(List.of(coordinator, delegation, decision, evaluation, worker));

    assertThat(transcript)
        .isEqualTo(
            """
            Interaction of agent coordinator-agent, task research, iteration 1:
            System: You coordinate research.
            Input: Research quantum computing
            Response: Delegating.

            Activity of agent coordinator-agent, instance coordinator-1: started 1 delegation \
            to [worker-agent (worker-1)], subtasks [subtask-1]

            Guardrail no-refunds, boundary BEFORE_TOOL_CALL, tool RefundTools_refund, \
            decision DENY: refunds are not allowed

            Interaction of agent worker-agent:
            System: You research.
            Input: Research quantum computing fundamentals
            Response: Qubits enable parallel computation.
            """);
  }

  @Test
  public void writesTheSystemMessageOfAnAgentOnlyWhenItChanges() {
    var transcript =
        FlowRecord.transcript(
            List.of(
                interaction("agent-a", "S1", "first"),
                interaction("agent-a", "S1", "second"),
                interaction("agent-a", "S2", "third"),
                interaction("agent-b", "S1", "fourth"),
                interaction("agent-a", "S1", "fifth")));

    assertThat(transcript)
        .isEqualTo(
            """
            Interaction of agent agent-a:
            System: S1
            Input: first

            Interaction of agent agent-a:
            Input: second

            Interaction of agent agent-a:
            System: S2
            Input: third

            Interaction of agent agent-b:
            System: S1
            Input: fourth

            Interaction of agent agent-a:
            System: S1
            Input: fifth
            """);
  }

  @Test
  public void writesTheFieldsOfATypedEventAndTheKindOfAnOtherEvent() {
    var record =
        activity(
            new ActivityRecord.Event.TaskAssigned("task-1"),
            new ActivityRecord.Event.TaskStarted("task-1", "Research quantum computing", 5),
            new ActivityRecord.Event.TaskCompleted(
                "task-1", "Qubits enable parallel computation.", 3),
            new ActivityRecord.Event.TaskFailed("task-2", "iteration limit reached", 1),
            new ActivityRecord.Event.TaskCancelled("task-3", "dependency failed", 0),
            new ActivityRecord.Event.TaskResultRejected("task-4", "title missing", 2),
            new ActivityRecord.Event.IterationFailed(
                "rate limited", Optional.of("task-5"), Optional.of(4)),
            new ActivityRecord.Event.IterationFailed(
                "model unavailable", Optional.empty(), Optional.empty()),
            new ActivityRecord.Event.DelegationStarted(
                List.of("worker-agent", "reviewer-agent"),
                2,
                List.of("subtask-1", "subtask-2"),
                List.of("worker-1", "reviewer-1")),
            new ActivityRecord.Event.DelegationResolved(
                2, 1, List.of("subtask-1", "subtask-2"), List.of("subtask-3")),
            // a delegation to a request-based agent, as the ledger records it
            new ActivityRecord.Event.DelegationStarted(List.of(), 1, List.of(), List.of()),
            new ActivityRecord.Event.DelegationResolved(0, 1, List.of(), List.of()),
            new ActivityRecord.Event.HandoffStarted("task-6", "specialist-agent"),
            new ActivityRecord.Event.HandoffReceived("task-7", "triage-agent", "triage-1"),
            new ActivityRecord.Event.Other("worker_task_received"));

    var prefix = "Activity of agent coordinator-agent, instance coordinator-1: ";
    assertThat(FlowRecord.transcript(List.of(record)).lines().toList())
        .containsExactly(
            prefix + "task task-1 assigned",
            prefix + "task task-1 started with at most 5 iterations: Research quantum computing",
            prefix
                + "task task-1 completed after 3 iterations: Qubits enable parallel computation.",
            prefix + "task task-2 failed after 1 iteration: iteration limit reached",
            prefix + "task task-3 cancelled after 0 iterations: dependency failed",
            prefix + "result of task task-4 rejected after 2 iterations: title missing",
            prefix + "iteration 4 of task task-5 failed: rate limited",
            prefix + "iteration failed: model unavailable",
            prefix
                + "started 2 delegations to [worker-agent (worker-1), reviewer-agent"
                + " (reviewer-1)], subtasks [subtask-1, subtask-2]",
            prefix
                + "delegations resolved, 2 succeeded [subtask-1, subtask-2], 1 failed [subtask-3]",
            prefix + "started 1 delegation",
            prefix + "delegations resolved, 0 succeeded, 1 failed",
            prefix + "task task-6 handed off to specialist-agent",
            prefix + "received task task-7 in a handoff from triage-agent (triage-1)",
            prefix + "event worker_task_received");
  }

  @Test
  public void writesWhetherAGuardrailDecisionOnlyReportsAndTheFailModeOfAFailure() {
    var transcript =
        FlowRecord.transcript(
            List.of(
                decision(
                    "no-refunds",
                    GuardrailDecisionRecord.Boundary.BEFORE_TOOL_CALL,
                    Optional.of("refund"),
                    GuardrailDecisionRecord.Decision.DENY,
                    "refunds are not allowed",
                    true,
                    GuardrailDecisionRecord.FailMode.FAIL_CLOSED),
                decision(
                    "pii",
                    GuardrailDecisionRecord.Boundary.BEFORE_MODEL_CALL,
                    Optional.empty(),
                    GuardrailDecisionRecord.Decision.FAIL,
                    "classifier unavailable",
                    false,
                    GuardrailDecisionRecord.FailMode.FAIL_OPEN),
                decision(
                    "pii",
                    GuardrailDecisionRecord.Boundary.BEFORE_MODEL_CALL,
                    Optional.empty(),
                    GuardrailDecisionRecord.Decision.FAIL,
                    "timeout",
                    true,
                    GuardrailDecisionRecord.FailMode.FAIL_CLOSED),
                decision(
                    "tone",
                    GuardrailDecisionRecord.Boundary.BEFORE_AGENT_RESPONSE,
                    Optional.empty(),
                    GuardrailDecisionRecord.Decision.ALLOW,
                    "",
                    false,
                    GuardrailDecisionRecord.FailMode.FAIL_CLOSED)));

    assertThat(transcript.lines().filter(line -> !line.isEmpty()).toList())
        .containsExactly(
            "Guardrail no-refunds, boundary BEFORE_TOOL_CALL, tool refund, decision DENY, report"
                + " only: refunds are not allowed",
            "Guardrail pii, boundary BEFORE_MODEL_CALL, decision FAIL, fail mode FAIL_OPEN:"
                + " classifier unavailable",
            "Guardrail pii, boundary BEFORE_MODEL_CALL, decision FAIL, report only: timeout",
            "Guardrail tone, boundary BEFORE_AGENT_RESPONSE, decision ALLOW");
  }

  @Test
  public void keepsTheTranscriptOfAnInteraction() {
    var record =
        interaction(
            "math-agent",
            "You are a calculator.",
            "What is 2+2?",
            List.of(
                new ModelResponse(
                    "m1",
                    "",
                    10,
                    5,
                    "let me think",
                    List.of(new ToolCall("t1", "calc", "{\"expr\":\"2+2\"}", "4"))),
                response("The answer is 4.")),
            List.of(
                new ToolCallResponse(
                    "t1", "calc", List.<MessageContent>of(TextMessageContent.from("4")))),
            Optional.empty());

    assertThat(record.transcript())
        .isEqualTo(
            """
            System: You are a calculator.
            Input: What is 2+2?
            Thinking: let me think
            Tool call calc({"expr":"2+2"}) -> 4
            Response: The answer is 4.
            Tool response calc: 4
            """);
  }

  @Test
  public void rendersAnEmptyListAsAnEmptyString() {
    assertThat(FlowRecord.transcript(List.of())).isEmpty();
  }
}
