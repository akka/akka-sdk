/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static akka.javasdk.testkit.eval.Targets.AGENT;
import static akka.javasdk.testkit.eval.Targets.QUALITY;
import static akka.javasdk.testkit.eval.Targets.TONE;
import static akka.javasdk.testkit.eval.Targets.bindings;
import static akka.javasdk.testkit.eval.Targets.interaction;
import static akka.javasdk.testkit.eval.Targets.toolCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.Failure;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.testkit.TestLedgerClient;
import akka.javasdk.testkit.eval.ExperimentRunner.CaseResult;
import akka.javasdk.testkit.eval.ExperimentRunner.Outcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The runner over a scripted target and an in-memory ledger, with no runtime. */
class ExperimentRunnerTest {

  private static final Duration SHORT = Duration.ofMillis(50);

  private final TestLedgerClient ledger = TestLedgerClient.create();

  /** The interaction every turn of {@link #answering} records. */
  private InteractionRecord answered(String interactionId, String sessionId) {
    return interaction(
        interactionId,
        sessionId,
        "Order o_42 shipped on Monday.",
        List.of(toolCall("OrderTools_getOrder", "{\"orderId\":\"o_42\"}", "shipped")));
  }

  /** A target that records the answered interaction and no evaluations. */
  private EvalTarget<String> answering() {
    return Targets.scripted(
        (turn, interactionId) -> ledger.seed(answered(interactionId, turn.sessionId())));
  }

  /** The cases against a scripted target and the ledger, ready to run with a short timeout. */
  @SafeVarargs
  private Experiment experiment(
      EvalTarget<String> target, AgentBindings agent, EvalCase<String>... cases) {
    return ExperimentRunner.against(
        new ExperimentRunner().cases(List.of(cases)), target, ledger, agent, SHORT);
  }

  /** Runs one case with the quality and tone evaluators bound, and reads its result. */
  private CaseResult single(EvalTarget<String> target) {
    return single(target, bindings(QUALITY, TONE));
  }

  private CaseResult single(EvalTarget<String> target, AgentBindings agent) {
    return experiment(target, agent, EvalCase.of("c", "Where is o_42?")).run().results().getFirst();
  }

  private static EvalResult resultOf(CaseResult result, String evaluator) {
    return result.evalResults().stream()
        .filter(r -> r.evaluator().equals(evaluator))
        .findFirst()
        .orElseThrow(() -> new AssertionError(evaluator + " did not report"));
  }

  private void verdict(String interactionId, String evaluator, Evaluation evaluation) {
    ledger.seed(Targets.verdict(interactionId, evaluator, evaluation));
  }

  @Test
  void readsTheVerdictOfEveryBoundEvaluatorFromTheLedger() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              ledger.seed(answered(interactionId, turn.sessionId()));
              verdict(interactionId, QUALITY, Evaluation.passed("names the order").withScore(0.9));
              verdict(interactionId, TONE, Evaluation.failed("too curt"));
            });

    var result = single(target);

    assertThat(result.failed()).isTrue();
    assertThat(result.evidence().interaction().orElseThrow().interactionId()).isEqualTo("c-1");
    assertThat(result.evalResults())
        .containsExactly(
            EvalResult.pass(QUALITY, "names the order (score 0.90)"),
            EvalResult.fail(TONE, "too curt"));
  }

  @Test
  void aVerdictWithoutAnExplanationShowsTheScoreAlone() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              ledger.seed(answered(interactionId, turn.sessionId()));
              verdict(interactionId, QUALITY, Evaluation.of(true, "").withScore(1));
            });

    assertThat(resultOf(single(target, bindings(QUALITY)), QUALITY).detail())
        .isEqualTo("score 1.00");
  }

  @Test
  void anInconclusiveEvaluationLeavesTheAttemptInconclusive() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              ledger.seed(answered(interactionId, turn.sessionId()));
              ledger.seed(Targets.inconclusive(interactionId, QUALITY, "no criterion"));
              verdict(interactionId, TONE, Evaluation.passed("polite"));
            });

    var result = single(target);

    assertThat(result.inconclusive()).isTrue();
    assertThat(result.passed()).isFalse();
    assertThat(resultOf(result, QUALITY))
        .isEqualTo(EvalResult.inconclusive(QUALITY, "no criterion"));
  }

  @Test
  void aFailedEvaluationFailsTheAttempt() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              ledger.seed(answered(interactionId, turn.sessionId()));
              ledger.seed(
                  Targets.evaluation(
                      interactionId,
                      QUALITY,
                      new EvaluationRecord.Outcome.Failed("judge timed out")));
            });

    var result = single(target, bindings(QUALITY));

    assertThat(result.failed()).isTrue();
    assertThat(resultOf(result, QUALITY))
        .isEqualTo(EvalResult.fail(QUALITY, "the evaluator failed: judge timed out"));
  }

  @Test
  void anEvaluatorWithoutAnEvaluationIsInconclusiveAndTheOthersStillReport() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              ledger.seed(answered(interactionId, turn.sessionId()));
              verdict(interactionId, TONE, Evaluation.passed("polite"));
            });

    var result = single(target);

    assertThat(result.inconclusive()).isTrue();
    assertThat(resultOf(result, QUALITY))
        .isEqualTo(
            EvalResult.inconclusive(
                QUALITY,
                "no evaluation recorded within 50 ms; the evaluator did not run, or did not finish"
                    + " in time"));
    assertThat(resultOf(result, TONE).verdict()).isEqualTo(EvalResult.Verdict.PASS);
  }

  @Test
  void theNewestEvaluationOfAnEvaluatorDecides() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              ledger.seed(answered(interactionId, turn.sessionId()));
              var older = Targets.verdict(interactionId, QUALITY, Evaluation.failed("first try"));
              ledger.seed(older);
              ledger.seed(
                  new EvaluationRecord(
                      "evaluation-later",
                      QUALITY,
                      Optional.empty(),
                      EvaluationRecord.Trigger.ON_INTERACTION,
                      interactionId,
                      AGENT,
                      new EvaluationRecord.Outcome.Verdict(Evaluation.passed("second try")),
                      older.timestamp().plusSeconds(5)));
            });

    assertThat(resultOf(single(target, bindings(QUALITY)), QUALITY))
        .isEqualTo(EvalResult.pass(QUALITY, "second try"));
  }

  @Test
  void aCaseWithoutABoundEvaluatorIsInconclusive() {
    var result = single(answering(), bindings());

    assertThat(result.evalResults()).isEmpty();
    assertThat(result.inconclusive()).isTrue();
    assertThat(result.describe()).contains("results: no evaluator bound");
  }

  @Test
  void anAnsweredTurnWithoutAnInteractionIdHasNoEvidence() {
    EvalTarget<String> target = turn -> EvalTarget.Outcome.answered(Optional.empty());

    var result = single(target);

    assertThat(result.evidence()).isEqualTo(Evidence.none("Where is o_42?"));
    assertThat(result.evalResults()).isEmpty();
  }

  @Test
  void aThrownTargetIsAFailedCaseWithoutEvidence() {
    EvalTarget<String> throwing =
        turn -> {
          throw new IllegalStateException("model unavailable");
        };

    var result = single(throwing);

    assertThat(result.failed()).isTrue();
    assertThat(result.evidence()).isEqualTo(Evidence.none("Where is o_42?"));
    assertThat(result.evalResults())
        .containsExactly(
            EvalResult.fail(ExperimentRunner.TARGET, "IllegalStateException: model unavailable"));
    assertThat(result.describe()).contains("interaction: none recorded");
  }

  @Test
  void aFailedTurnReadsTheInteractionOfItsSessionAndAwaitsOnlyTheEvaluatorsThatRunOnFailure() {
    EvalTarget<String> target =
        turn -> {
          ledger.seed(
              interaction(
                  "i-43",
                  turn.sessionId(),
                  "",
                  List.of(),
                  Optional.of(new Failure(Failure.FailureReason.MODEL, "rate limited")),
                  50,
                  0,
                  300));
          ledger.seed(Targets.verdict("i-43", TONE, Evaluation.passed("no reply to judge")));
          return EvalTarget.Outcome.failed("ModelException: rate limited");
        };
    var agent =
        new AgentBindings(
            AGENT,
            List.of(
                new AgentBindings.Binding(QUALITY, 1.0, false),
                new AgentBindings.Binding(TONE, 1.0, true)));

    var result = single(target, agent);

    assertThat(result.failed()).isTrue();
    assertThat(result.evidence().interaction().orElseThrow().interactionId()).isEqualTo("i-43");
    assertThat(result.evalResults())
        .containsExactly(
            EvalResult.fail(ExperimentRunner.TARGET, "ModelException: rate limited"),
            EvalResult.pass(TONE, "no reply to judge"));
    assertThat(result.describe()).contains("failure: MODEL: rate limited");
  }

  @Test
  void aFailedCaseDescribesTheEvidenceItWasJudgedOn() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              var interaction = answered(interactionId, turn.sessionId());
              ledger.seed(interaction);
              ledger.seed(
                  Targets.guardrail(
                      interaction, "no-refunds", GuardrailDecisionRecord.Decision.DENY, "refund"));
              ledger.seed(
                  Targets.guardrail(
                      interaction, "pii", GuardrailDecisionRecord.Decision.ALLOW, ""));
              verdict(interactionId, QUALITY, Evaluation.failed("vague"));
            });

    var description = single(target, bindings(QUALITY)).describe();

    assertThat(description)
        .isEqualTo(
            """
            case c run 1 FAILED
              reply: Order o_42 shipped on Monday.
              tools: OrderTools_getOrder{"orderId":"o_42"}
              model: 2 responses, 120 tokens in, 30 out, 800 ms
              guardrails: no-refunds DENIED: refund, pii allowed
              FAIL quality: vague
            """);
  }

  @Test
  void guardrailDecisionsOfOtherInteractionsInTheSessionAreLeftOut() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              var interaction = answered(interactionId, turn.sessionId());
              var other = answered("other", turn.sessionId());
              ledger.seed(interaction);
              ledger.seed(
                  Targets.guardrail(
                      other, "no-refunds", GuardrailDecisionRecord.Decision.DENY, "x"));
              verdict(interactionId, QUALITY, Evaluation.passed("fine"));
            });

    assertThat(single(target, bindings(QUALITY)).evidence().guardrails()).isEmpty();
  }

  @Test
  void theReportSumsWhatTheRunSpentOverTheAttemptsWithAnInteraction() {
    var report =
        experiment(
                Targets.passing(ledger, "fine", QUALITY),
                bindings(QUALITY),
                EvalCase.of("c1", "q"),
                EvalCase.of("c2", "q"))
            .gate(Gate.passRateShouldBeAtLeast(0.5))
            .run();

    assertThat(report.render())
        .contains("2 cases, 1 run: 2/2 attempts passed (100%)")
        .contains("gate: passed — pass rate 1.00 over 2 attempts (2 cases, 1 run), required 0.50")
        .contains("  quality 2/2")
        .contains(
            "spend: 2 model responses, 240 tokens in, 60 out, 1600 ms in total, slowest c1 run 1 at"
                + " 800 ms, over 2/2 attempts with a recorded interaction");
  }

  @Test
  void theReportHasNoSpendLineWithoutAnInteraction() {
    EvalTarget<String> throwing =
        turn -> {
          throw new IllegalStateException("down");
        };

    var report = experiment(throwing, bindings(QUALITY), EvalCase.of("c", "q")).run();

    assertThat(report.render()).doesNotContain("spend:");
  }

  @Test
  void aRunWithoutAGateFailsWhenACaseDoes() {
    var report =
        experiment(
                Targets.passingWhen(ledger, QUALITY, (call, caseId) -> !caseId.equals("c2")),
                bindings(QUALITY),
                EvalCase.of("c1", "q"),
                EvalCase.of("c2", "q"))
            .run();

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("gate: FAILED — failed cases [c2 (run 1)]");
  }

  record Ask(String customerId, String question) {}

  @Test
  void sendsTheCommandTypeOfTheCaseToTheAgentAndRendersItAsJson() {
    var commands = new ArrayList<Ask>();
    EvalTarget<Ask> target =
        turn -> {
          commands.add(turn.command());
          return EvalTarget.Outcome.answered(Optional.empty());
        };

    var report =
        ExperimentRunner.against(
                new ExperimentRunner().cases(EvalCase.of("c", new Ask("cust_1", "Which tier?"))),
                target,
                ledger,
                bindings(),
                SHORT)
            .run();

    assertThat(commands).containsExactly(new Ask("cust_1", "Which tier?"));
    assertThat(report.results().getFirst().evidence().input())
        .isEqualTo("{\"customerId\":\"cust_1\",\"question\":\"Which tier?\"}");
  }

  @Test
  void aCaseWithoutACommandIsRejected() {
    assertThatThrownBy(() -> EvalCase.of("c", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("command required");
    assertThatThrownBy(() -> EvalCase.of(" ", "q"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("case id required");
  }

  @Test
  void anExperimentNeedsAtLeastOneCase() {
    assertThatThrownBy(() -> new ExperimentRunner().cases(List.<EvalCase<String>>of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("at least one case required");
  }

  @Test
  void twoCasesWithTheSameIdAreRefused() {
    assertThatThrownBy(
            () -> new ExperimentRunner().cases(EvalCase.of("c", "q"), EvalCase.of("c", "q2")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Two cases have the id c");
  }

  @Test
  void aSampledBindingIsRefusedBeforeAnyCaseRuns() {
    EvalTarget<String> target =
        turn -> {
          throw new AssertionError("no case must run");
        };
    var agent =
        new AgentBindings(
            AGENT,
            List.of(
                new AgentBindings.Binding(QUALITY, 1.0, false),
                new AgentBindings.Binding(TONE, 0.25, false)));

    assertThatThrownBy(
            () ->
                ExperimentRunner.against(
                    new ExperimentRunner().cases(EvalCase.of("c", "q")),
                    target,
                    ledger,
                    agent,
                    SHORT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Evaluators bound to agent [support-agent] with a sampling ratio below 1.0: tone"
                + " (0.25). An experiment expects every bound evaluator to evaluate every attempt,"
                + " so set sampling-ratio = 1.0 on these bindings in the test configuration, or"
                + " disable them with enabled = false.");
  }

  // ---- repeated runs

  @Test
  void everyCaseRunsTheGivenNumberOfTimesEachInAFreshSession() {
    var sessions = new ArrayList<String>();
    var passing = Targets.passing(ledger, "fine", QUALITY);
    EvalTarget<String> target =
        turn -> {
          sessions.add(turn.sessionId());
          return passing.call(turn);
        };

    var report =
        experiment(target, bindings(QUALITY), EvalCase.of("c1", "q"), EvalCase.of("c2", "q"))
            .repeat(3)
            .run();

    assertThat(report.runs()).isEqualTo(3);
    assertThat(report.results())
        .extracting(CaseResult::caseId)
        .containsExactly("c1", "c1", "c1", "c2", "c2", "c2");
    assertThat(report.results()).extracting(CaseResult::run).containsExactly(1, 2, 3, 1, 2, 3);
    assertThat(sessions).hasSize(6).doesNotHaveDuplicates();
    assertThat(report.render()).contains("2 cases, 3 runs: 6/6 attempts passed (100%)");
  }

  @Test
  void aCaseThatPassesInSomeRunsAndFailsInOthersIsInconsistent() {
    // Calls 1 and 3 are c1 in runs 1 and 2; call 3 fails.
    var report =
        experiment(
                Targets.passingWhen(ledger, QUALITY, (call, caseId) -> call != 3),
                bindings(QUALITY),
                EvalCase.of("c1", "q"),
                EvalCase.of("c2", "q"))
            .repeat(2)
            .run();

    var c1 = report.cases().getFirst();
    assertThat(c1.outcome()).isEqualTo(Outcome.INCONSISTENT);
    assertThat(c1.passedRuns()).isEqualTo(1);
    assertThat(c1.failedRuns()).isEqualTo(1);
    assertThat(report.cases().get(1).outcome()).isEqualTo(Outcome.PASSED);
    assertThat(c1.requirements()).hasSize(1);
    assertThat(c1.requirements().getFirst().outcome()).isEqualTo(Outcome.INCONSISTENT);
    assertThat(report.render())
        .contains("inconsistent across runs:\n  c1 quality: passed in run 1, failed in run 2");
  }

  @Test
  void aCaseThatFailsInEveryRunIsNotInconsistent() {
    var report =
        experiment(
                Targets.passingWhen(ledger, QUALITY, (call, caseId) -> false),
                bindings(QUALITY),
                EvalCase.of("c1", "q"))
            .repeat(2)
            .run();

    assertThat(report.cases().getFirst().outcome()).isEqualTo(Outcome.FAILED);
    assertThat(report.render()).doesNotContain("inconsistent across runs");
  }

  @Test
  void repeatNeedsAtLeastOne() {
    var experiment = experiment(answering(), bindings(), EvalCase.of("c", "q"));

    assertThatThrownBy(() -> experiment.repeat(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("repeat needs at least 1, was 0");
  }

  // ---- the report file

  private static JsonNode json(ExperimentRunner.EvalReport report) throws Exception {
    return new ObjectMapper().readTree(Files.readString(report.reportFile().orElseThrow()));
  }

  /** Records a denied interaction with a passing quality verdict and an inconclusive tone. */
  private EvalTarget<String> deniedWithMixedVerdicts() {
    return Targets.scripted(
        (turn, interactionId) -> {
          var interaction = answered(interactionId, turn.sessionId());
          ledger.seed(interaction);
          ledger.seed(
              Targets.guardrail(
                  interaction, "no-refunds", GuardrailDecisionRecord.Decision.DENY, "refund"));
          verdict(interactionId, QUALITY, Evaluation.passed("names the order").withScore(0.9));
          ledger.seed(Targets.inconclusive(interactionId, TONE, "no tone"));
        });
  }

  @Test
  void theJsonReportCarriesTheVerdictTheRatesTheSpendAndEveryCase(@TempDir Path dir)
      throws Exception {
    var json =
        json(
            experiment(
                    deniedWithMixedVerdicts(),
                    bindings(QUALITY, TONE),
                    EvalCase.of("shipped", "Where is o_42?"))
                .name("support-agent-quality")
                .gate(Gate.passRateShouldBeAtLeast(0.9))
                .reportDirectory(dir)
                .run());

    assertThat(json.get("format").asText()).isEqualTo("akka-eval-report");
    assertThat(json.get("formatVersion").asInt()).isEqualTo(3);
    assertThat(json.get("name").asText()).isEqualTo("support-agent-quality");
    assertThat(json.get("gate").get("passed").asBoolean()).isFalse();
    assertThat(json.get("summary").get("inconclusiveAttempts").asInt()).isEqualTo(1);
    assertThat(json.get("evaluators")).hasSize(2);
    assertThat(json.get("evaluators").get(1).get("evaluator").asText()).isEqualTo(TONE);
    assertThat(json.get("evaluators").get(1).get("inconclusive").asInt()).isEqualTo(1);
    assertThat(json.get("spend").get("modelResponses").asInt()).isEqualTo(2);
    assertThat(json.get("spend").get("latencyMs").asLong()).isEqualTo(800);
    var attempt = json.get("attempts").get(0);
    assertThat(attempt.get("verdict").asText()).isEqualTo("INCONCLUSIVE");
    var evidence = attempt.get("evidence");
    assertThat(evidence.get("interactionId").asText()).isEqualTo("shipped-1");
    assertThat(evidence.get("reply").asText()).isEqualTo("Order o_42 shipped on Monday.");
    assertThat(evidence.get("denied").asBoolean()).isTrue();
    assertThat(evidence.get("toolCalls").get(0).get("name").asText())
        .isEqualTo("OrderTools_getOrder");
    assertThat(evidence.get("toolCalls").get(0).get("arguments").asText())
        .isEqualTo("{\"orderId\":\"o_42\"}");
    assertThat(evidence.get("guardrails").get(0).get("decision").asText()).isEqualTo("DENY");
    assertThat(attempt.get("results").get(0).get("detail").asText())
        .isEqualTo("names the order (score 0.90)");
    assertThat(json.get("cases").get(0).get("requirements")).hasSize(2);
  }

  @Test
  void theReportFileReadsBackAsTheDocumentTheReportExposes(@TempDir Path dir) {
    var report =
        experiment(
                Targets.passing(ledger, "fine", QUALITY), bindings(QUALITY), EvalCase.of("c", "q"))
            .reportDirectory(dir)
            .run();

    assertThat(ReportDocument.read(report.reportFile().orElseThrow())).isEqualTo(report.document());
  }

  @Test
  void theReportFileCarriesTheNameAndTheStartTime(@TempDir Path dir) {
    var report =
        experiment(
                Targets.passing(ledger, "fine", QUALITY), bindings(QUALITY), EvalCase.of("c", "q"))
            .name("quality")
            .reportDirectory(dir)
            .run();

    var file = report.reportFile().orElseThrow();
    assertThat(file.getParent()).isEqualTo(dir);
    assertThat(file.getFileName().toString())
        .matches(Pattern.compile("quality-\\d{8}-\\d{6}-\\d{3}\\.json"));
  }

  @Test
  void aRunWithoutANameIsNamedAfterTheTestMethod() {
    var report =
        experiment(
                Targets.passing(ledger, "fine", QUALITY), bindings(QUALITY), EvalCase.of("c", "q"))
            .run();

    assertThat(report.name())
        .isEqualTo("ExperimentRunnerTest.aRunWithoutANameIsNamedAfterTheTestMethod");
  }

  @Test
  void withoutAReportFileNothingIsWritten(@TempDir Path dir) throws Exception {
    var report =
        experiment(
                Targets.passing(ledger, "fine", QUALITY), bindings(QUALITY), EvalCase.of("c", "q"))
            .reportDirectory(dir)
            .withoutReportFile()
            .run();

    assertThat(report.reportFile()).isEmpty();
    try (var files = Files.list(dir)) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void aReportFileThatCannotBeWrittenLeavesTheReportWithoutOne(@TempDir Path dir) throws Exception {
    var notADirectory = dir.resolve("file");
    Files.writeString(notADirectory, "");

    var report =
        experiment(
                Targets.passing(ledger, "fine", QUALITY), bindings(QUALITY), EvalCase.of("c", "q"))
            .reportDirectory(notADirectory)
            .run();

    assertThat(report.reportFile()).isEmpty();
    assertThat(report.passed()).isTrue();
  }

  @Test
  void aNameThatIsNotAPortableFileNameIsRejected() {
    var experiment = experiment(answering(), bindings(), EvalCase.of("c", "q"));

    assertThatThrownBy(() -> experiment.name("a/b"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not contain '/'");
    assertThatThrownBy(() -> experiment.name(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("name required");
    assertThatThrownBy(() -> experiment.name("x".repeat(201)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at most 200 characters");
  }

  @Test
  void theJsonReportMatchesTheSchemaOnTheClasspath(@TempDir Path dir) throws Exception {
    JsonNode schema;
    try (var stream =
        ExperimentRunnerTest.class.getResourceAsStream(
            "/akka/javasdk/testkit/eval/eval-report.schema.json")) {
      schema = new ObjectMapper().readTree(stream);
    }
    var json =
        json(
            experiment(deniedWithMixedVerdicts(), bindings(QUALITY, TONE), EvalCase.of("c", "q"))
                .reportDirectory(dir)
                .repeat(2)
                .run());

    assertRequiredPresent(schema, schema, json);
  }

  // The test module has no JSON Schema validator; this checks the required properties, the enum
  // values and the nesting the schema declares, which is what a reader relies on.
  private static void assertRequiredPresent(JsonNode root, JsonNode schema, JsonNode value) {
    if (schema.has("$ref")) {
      var path = schema.get("$ref").asText().substring("#/".length()).split("/");
      var resolved = root;
      for (var segment : path) resolved = resolved.get(segment);
      assertRequiredPresent(root, resolved, value);
      return;
    }
    if (schema.has("required")) {
      for (var name : schema.get("required")) {
        assertThat(value.has(name.asText())).as("property %s", name.asText()).isTrue();
      }
    }
    if (schema.has("enum")) {
      var allowed = new ArrayList<String>();
      for (var constant : schema.get("enum")) allowed.add(constant.asText());
      assertThat(allowed).as("enum value %s", value.asText()).contains(value.asText());
    }
    if (schema.has("properties")) {
      schema
          .get("properties")
          .properties()
          .forEach(
              property -> {
                if (value.has(property.getKey())) {
                  assertRequiredPresent(root, property.getValue(), value.get(property.getKey()));
                }
              });
    }
    if (schema.has("items")) {
      for (var item : value) assertRequiredPresent(root, schema.get("items"), item);
    }
  }

  @Test
  void theExampleReportIsWhatTheRunnerProduces() throws Exception {
    var example =
        ReportDocument.read(
            Path.of(
                ExperimentRunnerTest.class
                    .getResource("/akka/javasdk/testkit/eval/eval-report.example.json")
                    .toURI()));

    var produced = exampleExperiment().run().document();

    assertThat(withTimesOf(produced, example)).isEqualTo(example);
  }

  /** The experiment behind the example report: three cases, one of them denied by a guardrail. */
  private Experiment exampleExperiment() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              switch (turn.caseId()) {
                case "shipped" -> {
                  ledger.seed(answered(interactionId, turn.sessionId()));
                  verdict(
                      interactionId, QUALITY, Evaluation.passed("names the order").withScore(0.9));
                  verdict(interactionId, TONE, Evaluation.passed("polite"));
                }
                case "refund" -> {
                  var interaction =
                      interaction(
                          interactionId,
                          turn.sessionId(),
                          "I cannot issue a refund here.",
                          List.of(
                              toolCall(
                                  "OrderTools_getOrder", "{\"orderId\":\"o_43\"}", "delivered")));
                  ledger.seed(interaction);
                  ledger.seed(
                      Targets.guardrail(
                          interaction,
                          "no-refunds",
                          GuardrailDecisionRecord.Decision.DENY,
                          "refund"));
                  verdict(
                      interactionId,
                      QUALITY,
                      Evaluation.failed("does not offer help").withScore(0.3));
                  verdict(interactionId, TONE, Evaluation.passed("polite"));
                }
                default -> {
                  ledger.seed(
                      interaction(interactionId, turn.sessionId(), "You are welcome.", List.of()));
                  verdict(interactionId, QUALITY, Evaluation.passed("closes").withScore(1));
                  ledger.seed(Targets.inconclusive(interactionId, TONE, "nothing to judge"));
                }
              }
            });
    return experiment(
            target,
            bindings(QUALITY, TONE),
            EvalCase.of("shipped", "Where is o_42?"),
            EvalCase.of("refund", "Refund o_43."),
            EvalCase.of("closing", "Thanks, that is all."))
        .name("support-agent-quality")
        .gate(Gate.passRateShouldBeAtLeast(0.9));
  }

  private static ReportDocument withTimesOf(ReportDocument document, ReportDocument times) {
    return new ReportDocument(
        document.format(),
        document.formatVersion(),
        document.name(),
        times.startedAt(),
        times.finishedAt(),
        document.runs(),
        document.gate(),
        document.summary(),
        document.evaluators(),
        document.spend(),
        document.cases(),
        document.attempts());
  }
}
