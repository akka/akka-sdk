/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;
import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import akka.javasdk.testkit.TestKit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs cases against an agent and collects the verdicts of the evaluators bound to it. Sequential
 * and in-process; the only output is the report file written at the end of the run.
 *
 * <p>Per case: call the agent in a fresh session, read the interaction and the guardrail decisions
 * the runtime recorded in the ledger, and wait for the evaluation that every bound {@link
 * akka.javasdk.evaluation.Evaluator} records about the interaction. The evaluators and their
 * bindings come from the configuration the TestKit runs with, under {@code
 * akka.javasdk.evaluation.evaluators}; a test adds bindings for its own evaluators and disables
 * production ones with {@code TestKit.Settings.withAdditionalConfig}. The stubs the agent's tools
 * call are prepared before the run, in the test.
 *
 * <p>An experiment is built in steps, each step offering only what comes next: the cases, the
 * agent, an optional {@link Gate}, then {@link Experiment#run}. Without a gate every case must
 * pass, which suits a mocked model. With a real model gate on rates instead.
 *
 * <p>{@link Experiment#repeat} runs every case several times. One execution of one case is an
 * attempt, and the gate, the rates and the spend count attempts. The report names the requirements
 * that passed in some runs and failed in others.
 *
 * <p>{@link Experiment#run} writes the report as JSON to {@code target/eval-reports/<name>-<start
 * time>.json}, see {@link EvalReport#reportFile}.
 *
 * <p>The cases carry the command the agent's command handler takes, so the handler's own type is
 * passed through unchanged.
 *
 * <pre>{@code
 * var runner = new ExperimentRunner(testKit);
 *
 * var report =
 *     runner.cases(cases).agent(SupportAgent::ask).gate(Gate.passRateShouldBeAtLeast(0.9)).run();
 * assertThat(report.passed()).withFailMessage(report::render).isTrue();
 * }</pre>
 */
public final class ExperimentRunner {

  private static final Logger log = LoggerFactory.getLogger(ExperimentRunner.class);

  /** Where {@link Experiment#run} writes the report file unless the experiment names another. */
  public static final Path DEFAULT_REPORT_DIRECTORY = Path.of("target", "eval-reports");

  /**
   * How long an attempt waits for the ledger to hold its interaction and one evaluation per bound
   * evaluator. An evaluator without an evaluation at the timeout is inconclusive on that attempt.
   */
  static final Duration EVALUATION_TIMEOUT = Duration.ofSeconds(10);

  /**
   * The evaluator name of the result the runner reports when the agent call failed: the call threw
   * instead of replying.
   */
  public static final String TARGET = "target";

  private final TestKit testKit;

  /**
   * @param testKit the running TestKit the agent is called through
   */
  public ExperimentRunner(TestKit testKit) {
    if (testKit == null) throw new IllegalArgumentException("testKit required");
    this.testKit = testKit;
  }

  // For the runner's own tests: no TestKit, the cases are bound to a target with against(…).
  ExperimentRunner() {
    this.testKit = null;
  }

  /**
   * The cases the experiment runs.
   *
   * @param first the first case
   * @param more further cases, with the same command type
   */
  @SafeVarargs
  public final <C> ExperimentCases<C> cases(EvalCase<C> first, EvalCase<C>... more) {
    var all = new ArrayList<EvalCase<C>>();
    all.add(first);
    all.addAll(List.of(more));
    return cases(all);
  }

  /**
   * The cases the experiment runs.
   *
   * @param cases at least one case
   */
  public <C> ExperimentCases<C> cases(List<EvalCase<C>> cases) {
    if (cases == null || cases.isEmpty()) {
      throw new IllegalArgumentException("at least one case required");
    }
    if (cases.stream().anyMatch(c -> c == null)) {
      throw new IllegalArgumentException("case required");
    }
    var ids = new HashSet<String>();
    for (var evalCase : cases) {
      if (!ids.add(evalCase.id())) {
        throw new IllegalArgumentException(
            "Two cases have the id "
                + evalCase.id()
                + ". The report and the gate name a case by its id, so give each case a distinct"
                + " one");
      }
    }
    return new Cases<>(testKit, List.copyOf(cases));
  }

  // For the runner's own tests: the cases run against a scripted target instead of an agent, read
  // an in-memory ledger with the given timeout, and write no report file unless the test names a
  // directory.
  static <C> Experiment against(
      ExperimentCases<C> cases,
      EvalTarget<C> target,
      LedgerClient ledger,
      AgentBindings agent,
      Duration evaluationTimeout) {
    return ((Cases<C>) cases).target(target, ledger, agent, evaluationTimeout).withoutReportFile();
  }

  private record Cases<C>(TestKit testKit, List<EvalCase<C>> cases) implements ExperimentCases<C> {

    @Override
    public <A extends Agent, R> Experiment agent(Function2<A, C, Agent.Effect<R>> method) {
      if (method == null) throw new IllegalArgumentException("agent method required");
      return target(
          new AgentTarget<>(testKit.getComponentClient(), method),
          testKit.getLedgerClient(),
          AgentBindings.of(testKit.getApplicationConfig(), method),
          EVALUATION_TIMEOUT);
    }

    private Experiment target(
        EvalTarget<C> target,
        LedgerClient ledger,
        AgentBindings agent,
        Duration evaluationTimeout) {
      if (target == null) throw new IllegalArgumentException("target required");
      if (ledger == null) throw new IllegalArgumentException("ledger required");
      if (agent == null) throw new IllegalArgumentException("agent required");
      if (evaluationTimeout == null || evaluationTimeout.isNegative() || evaluationTimeout.isZero())
        throw new IllegalArgumentException("evaluation timeout must be positive");
      agent.requireFullSampling();
      return new Ready<>(
          cases,
          target,
          ledger,
          agent,
          Gate.allCasesShouldPass(),
          "",
          Optional.of(DEFAULT_REPORT_DIRECTORY),
          1,
          evaluationTimeout);
    }
  }

  private record Ready<C>(
      List<EvalCase<C>> cases,
      EvalTarget<C> target,
      LedgerClient ledger,
      AgentBindings agent,
      Gate gate,
      String name,
      Optional<Path> reportDirectory,
      int runs,
      Duration evaluationTimeout)
      implements Experiment {

    @Override
    public Experiment gate(Gate gate) {
      if (gate == null) throw new IllegalArgumentException("gate required");
      return with(gate, name, reportDirectory, runs);
    }

    @Override
    public Experiment name(String name) {
      requireFileName(name);
      return with(gate, name, reportDirectory, runs);
    }

    @Override
    public Experiment reportDirectory(Path directory) {
      if (directory == null) throw new IllegalArgumentException("directory required");
      return with(gate, name, Optional.of(directory), runs);
    }

    @Override
    public Experiment withoutReportFile() {
      return with(gate, name, Optional.empty(), runs);
    }

    @Override
    public Experiment repeat(int times) {
      if (times < 1) throw new IllegalArgumentException("repeat needs at least 1, was " + times);
      return with(gate, name, reportDirectory, times);
    }

    private Ready<C> with(Gate gate, String name, Optional<Path> reportDirectory, int runs) {
      return new Ready<>(
          cases, target, ledger, agent, gate, name, reportDirectory, runs, evaluationTimeout);
    }

    // Run-major, every case once and then every case again, so the attempts of one case are spread
    // over the batch instead of hitting the model within the same seconds.
    @Override
    public EvalReport run() {
      var startedAt = Instant.now();
      var reportName = name.isEmpty() ? defaultName() : name;
      var attemptsByCase = new LinkedHashMap<String, List<CaseResult>>();
      for (var run = 1; run <= runs; run++) {
        for (var i = 0; i < cases.size(); i++) {
          var evalCase = cases.get(i);
          log.info("Eval case {} ({}/{}) run {}/{}", evalCase.id(), i + 1, cases.size(), run, runs);
          attemptsByCase
              .computeIfAbsent(evalCase.id(), id -> new ArrayList<>())
              .add(evaluate(evalCase, run));
        }
      }
      var summaries = attemptsByCase.values().stream().map(CaseSummary::new).toList();
      var results = summaries.stream().flatMap(c -> c.attempts().stream()).toList();
      var report =
          new Report(
              reportName,
              startedAt,
              Instant.now(),
              runs,
              summaries,
              gate.check(results, runs),
              Optional.empty());
      return reportDirectory
          .map(directory -> report.withReportFile(write(report, directory)))
          .orElse(report);
    }

    // A report file that cannot be written is logged, the report itself still returns.
    private static Optional<Path> write(Report report, Path directory) {
      var fileName = report.name() + "-" + TIMESTAMP.format(report.startedAt()) + ".json";
      try {
        var file = directory.resolve(fileName);
        Files.createDirectories(directory);
        Files.writeString(file, EvalReportJson.render(report.document()));
        log.info("Eval report written to {}", file.toAbsolutePath());
        return Optional.of(file);
      } catch (IOException | RuntimeException e) {
        log.warn(
            "Eval report could not be written to {} in {}: {}",
            fileName,
            directory.toAbsolutePath(),
            e.toString());
        return Optional.empty();
      }
    }

    // The test method is the first caller outside the runner that carries a test annotation;
    // without one, the first caller outside the runner, such as a helper in the test class.
    private static String defaultName() {
      var callers =
          StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
              .walk(frames -> frames.filter(f -> !isRunnerFrame(f)).limit(64).toList());
      return callers.stream()
          .filter(Ready::isTestMethod)
          .findFirst()
          .or(() -> callers.stream().findFirst())
          .map(f -> f.getDeclaringClass().getSimpleName() + "." + f.getMethodName())
          .orElse("experiment");
    }

    private static boolean isRunnerFrame(StackWalker.StackFrame frame) {
      var runner = ExperimentRunner.class.getName();
      return frame.getClassName().equals(runner) || frame.getClassName().startsWith(runner + "$");
    }

    private static boolean isTestMethod(StackWalker.StackFrame frame) {
      for (var method : frame.getDeclaringClass().getDeclaredMethods()) {
        if (!method.getName().equals(frame.getMethodName())) continue;
        for (var annotation : method.getAnnotations()) {
          if (TEST_ANNOTATIONS.contains(annotation.annotationType().getSimpleName())) return true;
        }
      }
      return false;
    }

    private CaseResult evaluate(EvalCase<C> evalCase, int run) {
      var turn =
          new EvalTarget.Turn<>(UUID.randomUUID().toString(), evalCase.id(), evalCase.command());
      EvalTarget.Outcome outcome;
      try {
        outcome = target.call(turn);
      } catch (RuntimeException e) {
        outcome = EvalTarget.Outcome.failed(e);
      }

      var reader = new LedgerReader(ledger, evaluationTimeout);
      var results = new ArrayList<EvalResult>();
      Optional<InteractionRecord> interaction;
      switch (outcome) {
        case EvalTarget.Outcome.Answered answered ->
            interaction = answered.interactionId().flatMap(reader::interaction);
        case EvalTarget.Outcome.Failed failed -> {
          results.add(EvalResult.fail(TARGET, failed.reason()));
          interaction = reader.newestInteraction(agent.agentComponentId(), turn.sessionId());
        }
      }
      var guardrails = interaction.map(reader::guardrails).orElse(List.of());
      interaction.ifPresent(
          record -> {
            var expected = agent.expectedEvaluators(record.failed());
            if (!expected.isEmpty()) {
              var records = reader.awaitEvaluations(record.interactionId(), Set.copyOf(expected));
              for (var evaluator : expected) results.add(resultOf(evaluator, records));
            }
          });
      return new CaseResult(
          evalCase.id(),
          run,
          new Evidence(evalCase.commandText(), interaction, guardrails),
          List.copyOf(results));
    }

    // The newest record of the evaluator decides. An evaluator without a record within the timeout
    // is inconclusive, so the attempt cannot pass.
    private EvalResult resultOf(String evaluator, List<EvaluationRecord> records) {
      return records.stream()
          .filter(record -> record.evaluatorComponentId().equals(evaluator))
          .max(Comparator.comparing(EvaluationRecord::timestamp))
          .map(ExperimentRunner::resultOf)
          .orElseGet(
              () ->
                  EvalResult.inconclusive(
                      evaluator,
                      "no evaluation recorded within "
                          + evaluationTimeout.toMillis()
                          + " ms; the evaluator did not run, or did not finish in time"));
    }
  }

  /** The result an evaluation record stands for. */
  static EvalResult resultOf(EvaluationRecord record) {
    var evaluator = record.evaluatorComponentId();
    return switch (record.outcome()) {
      case EvaluationRecord.Outcome.Verdict verdict -> {
        var evaluation = verdict.evaluation();
        var detail = evaluation.explanation();
        var score =
            evaluation.score().map(s -> String.format(Locale.ROOT, "score %.2f", s)).orElse("");
        var text =
            detail.isEmpty() ? score : score.isEmpty() ? detail : detail + " (" + score + ")";
        yield evaluation.passed()
            ? EvalResult.pass(evaluator, text)
            : EvalResult.fail(evaluator, text);
      }
      case EvaluationRecord.Outcome.Inconclusive inconclusive ->
          EvalResult.inconclusive(evaluator, inconclusive.reason());
      case EvaluationRecord.Outcome.NotApplicable notApplicable ->
          EvalResult.notApplicable(evaluator, notApplicable.reason());
      case EvaluationRecord.Outcome.Failed failed ->
          EvalResult.fail(evaluator, "the evaluator failed: " + failed.reason());
    };
  }

  /** The characters no common file system accepts in a file name, and the control characters. */
  private static final Pattern NOT_IN_A_FILE_NAME = Pattern.compile("[<>:\"/\\\\|?*\\p{Cntrl}]");

  /** Leaves room for the start time and the extension within a 255 character file name. */
  private static final int MAX_NAME_LENGTH = 200;

  /** The name must be usable as a file name on every platform. */
  static void requireFileName(String name) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
    if (name.length() > MAX_NAME_LENGTH)
      throw new IllegalArgumentException(
          "name must be at most " + MAX_NAME_LENGTH + " characters, it names the report file");
    var illegal = NOT_IN_A_FILE_NAME.matcher(name);
    if (illegal.find())
      throw new IllegalArgumentException(
          "name must not contain "
              + describe(illegal.group())
              + ", it names the report file and < > : \" / \\ | ? * and control characters are"
              + " not portable: "
              + name);
  }

  private static String describe(String character) {
    var code = character.codePointAt(0);
    return Character.isISOControl(code)
        ? String.format(Locale.ROOT, "the control character U+%04X", code)
        : "'" + character + "'";
  }

  private static final Set<String> TEST_ANNOTATIONS =
      Set.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate");

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

  /**
   * One attempt: one case's evidence and results from one run.
   *
   * @param run the run the attempt belongs to, from 1; always 1 without {@link Experiment#repeat}
   * @param evidence what the attempt left in the ledger
   * @param evalResults one per expected evaluator, in evaluator order, after the {@link #TARGET}
   *     result when the agent call failed
   */
  public record CaseResult(
      String caseId, int run, Evidence evidence, List<EvalResult> evalResults) {

    public CaseResult {
      if (run < 1) throw new IllegalArgumentException("run must be at least 1, was " + run);
      evalResults = List.copyOf(evalResults);
    }

    /** An attempt of the first run. */
    public CaseResult(String caseId, Evidence evidence, List<EvalResult> evalResults) {
      this(caseId, 1, evidence, evalResults);
    }

    /**
     * FAIL when a result failed. Otherwise INCONCLUSIVE when a result is inconclusive, or when no
     * result passed: every result is not applicable, or there is no result. Otherwise PASS: at
     * least one result passed and the others are not applicable. A failed agent call is a failed
     * result.
     */
    public EvalResult.Verdict verdict() {
      var verdicts = evalResults.stream().map(EvalResult::verdict).collect(Collectors.toSet());
      if (verdicts.contains(EvalResult.Verdict.FAIL)) return EvalResult.Verdict.FAIL;
      if (verdicts.contains(EvalResult.Verdict.INCONCLUSIVE)
          || !verdicts.contains(EvalResult.Verdict.PASS)) {
        return EvalResult.Verdict.INCONCLUSIVE;
      }
      return EvalResult.Verdict.PASS;
    }

    /** At least one result passed, and no result failed or is inconclusive. */
    public boolean passed() {
      return verdict() == EvalResult.Verdict.PASS;
    }

    /** A failed result. */
    public boolean failed() {
      return verdict() == EvalResult.Verdict.FAIL;
    }

    /** No failed result, and an inconclusive result or no passed result. */
    public boolean inconclusive() {
      return verdict() == EvalResult.Verdict.INCONCLUSIVE;
    }

    /** The evidence and the results as text, for a failed test's output. */
    public String describe() {
      var text = new StringBuilder();
      text.append("case ")
          .append(caseId)
          .append(" run ")
          .append(run)
          .append(
              switch (verdict()) {
                case PASS -> " passed";
                case FAIL -> " FAILED";
                case INCONCLUSIVE, NOT_APPLICABLE -> " INCONCLUSIVE";
              })
          .append('\n');
      if (evidence.interaction().isEmpty()) {
        text.append("  interaction: none recorded\n");
      } else {
        text.append("  reply: ").append(oneLine(evidence.reply())).append('\n');
        evidence
            .failure()
            .ifPresent(failure -> text.append("  failure: ").append(oneLine(failure)).append('\n'));
        text.append("  tools: ").append(toolEvidence()).append('\n');
        text.append("  model: ").append(modelEvidence()).append('\n');
        if (!evidence.guardrails().isEmpty()) {
          text.append("  guardrails: ").append(guardrailEvidence()).append('\n');
        }
      }
      if (evalResults.isEmpty()) {
        text.append("  results: no evaluator bound\n");
      }
      for (var evalResult : evalResults) {
        text.append("  ")
            .append(evalResult.verdict())
            .append(' ')
            .append(evalResult.evaluator())
            .append(evalResult.detail().isEmpty() ? "" : ": " + evalResult.detail())
            .append('\n');
      }
      return text.toString();
    }

    private String toolEvidence() {
      if (evidence.toolCalls().isEmpty()) return "none called";
      return evidence.toolCalls().stream()
          .map(call -> call.name() + oneLine(call.arguments()))
          .collect(Collectors.joining(" → "));
    }

    private String modelEvidence() {
      return evidence.modelResponses()
          + " responses, "
          + evidence.inputTokens()
          + " tokens in, "
          + evidence.outputTokens()
          + " out, "
          + evidence.latency().toMillis()
          + " ms";
    }

    private String guardrailEvidence() {
      return evidence.guardrails().stream()
          .map(ExperimentRunner::describe)
          .collect(Collectors.joining(", "));
    }

    private static String oneLine(String text) {
      var flat = text.replaceAll("\\s+", " ").trim();
      return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
    }
  }

  private static String describe(GuardrailDecisionRecord decision) {
    var what =
        switch (decision.decision()) {
          case ALLOW -> " allowed";
          case DENY -> " DENIED";
          case FAIL -> " FAILED";
          case UNSPECIFIED -> " decided";
        };
    var explanation = decision.explanation().isEmpty() ? "" : ": " + decision.explanation();
    return decision.name() + what + (decision.reportOnly() ? " (report only)" : "") + explanation;
  }

  /** The outcome of a case over all its runs, or of a requirement over its conclusive runs. */
  public enum Outcome {
    /** Every run passed; for a requirement, every conclusive run. */
    PASSED,
    /** Every run failed; for a requirement, every conclusive run. */
    FAILED,
    /** The runs did not agree; for a requirement, it passed in some runs and failed in others. */
    INCONSISTENT,
    /** Every run was inconclusive; for a requirement, it was conclusive in no run. */
    INCONCLUSIVE
  }

  /**
   * One requirement of a case over all runs: one evaluator at one position in the results of the
   * case's attempts. The runner's {@link #TARGET} result is a requirement too, with no run passed.
   *
   * @param caseId the case
   * @param index the position of the result in the attempt, from 0
   * @param evaluator the evaluator component id, or {@link #TARGET}
   * @param passedIn the runs with the verdict PASS, ascending
   * @param failedIn the runs with the verdict FAIL, ascending
   * @param inconclusiveIn the runs with the verdict INCONCLUSIVE, ascending
   * @param notApplicableIn the runs with the verdict NOT_APPLICABLE, ascending
   */
  public record RequirementSummary(
      String caseId,
      int index,
      String evaluator,
      List<Integer> passedIn,
      List<Integer> failedIn,
      List<Integer> inconclusiveIn,
      List<Integer> notApplicableIn) {

    public RequirementSummary {
      passedIn = List.copyOf(passedIn);
      failedIn = List.copyOf(failedIn);
      inconclusiveIn = List.copyOf(inconclusiveIn);
      notApplicableIn = List.copyOf(notApplicableIn);
    }

    /** The outcome over the runs where the requirement was conclusive. */
    public Outcome outcome() {
      if (passedIn.isEmpty() && failedIn.isEmpty()) return Outcome.INCONCLUSIVE;
      if (failedIn.isEmpty()) return Outcome.PASSED;
      if (passedIn.isEmpty()) return Outcome.FAILED;
      return Outcome.INCONSISTENT;
    }

    private record Key(int index, String evaluator) {}

    /** The requirements of one case, in the order their results first appear in its attempts. */
    static List<RequirementSummary> of(List<CaseResult> attempts) {
      var byKey = new LinkedHashMap<Key, Map<EvalResult.Verdict, List<Integer>>>();
      for (var attempt : attempts) {
        var results = attempt.evalResults();
        for (var i = 0; i < results.size(); i++) {
          var result = results.get(i);
          byKey
              .computeIfAbsent(
                  new Key(i, result.evaluator()), k -> new EnumMap<>(EvalResult.Verdict.class))
              .computeIfAbsent(result.verdict(), v -> new ArrayList<>())
              .add(attempt.run());
        }
      }
      var caseId = attempts.getFirst().caseId();
      return byKey.entrySet().stream()
          .map(
              e ->
                  new RequirementSummary(
                      caseId,
                      e.getKey().index(),
                      e.getKey().evaluator(),
                      e.getValue().getOrDefault(EvalResult.Verdict.PASS, List.of()),
                      e.getValue().getOrDefault(EvalResult.Verdict.FAIL, List.of()),
                      e.getValue().getOrDefault(EvalResult.Verdict.INCONCLUSIVE, List.of()),
                      e.getValue().getOrDefault(EvalResult.Verdict.NOT_APPLICABLE, List.of())))
          .toList();
    }

    private String render(boolean nameShared) {
      var name = nameShared ? evaluator + " #" + (index + 1) : evaluator;
      var parts = new ArrayList<String>();
      if (!passedIn.isEmpty()) parts.add("passed in " + describeRuns(passedIn));
      if (!failedIn.isEmpty()) parts.add("failed in " + describeRuns(failedIn));
      if (!inconclusiveIn.isEmpty()) parts.add("inconclusive in " + describeRuns(inconclusiveIn));
      if (!notApplicableIn.isEmpty())
        parts.add("not applicable in " + describeRuns(notApplicableIn));
      return caseId + " " + name + ": " + String.join(", ", parts);
    }
  }

  /**
   * One case over all its runs.
   *
   * @param caseId the case id
   * @param attempts the case's results, one per run, runs ascending
   */
  public record CaseSummary(String caseId, List<CaseResult> attempts) {

    public CaseSummary {
      if (attempts == null || attempts.isEmpty())
        throw new IllegalArgumentException("at least one attempt required");
      attempts = List.copyOf(attempts);
    }

    CaseSummary(List<CaseResult> attempts) {
      this(attempts.getFirst().caseId(), attempts);
    }

    /** The outcome over all runs. */
    public Outcome outcome() {
      var runs = attempts.size();
      if (passedRuns() == runs) return Outcome.PASSED;
      if (failedRuns() == runs) return Outcome.FAILED;
      if (inconclusiveRuns() == runs) return Outcome.INCONCLUSIVE;
      return Outcome.INCONSISTENT;
    }

    /** Runs with the verdict PASS. */
    public int passedRuns() {
      return (int) attempts.stream().filter(CaseResult::passed).count();
    }

    /** Runs with the verdict FAIL. */
    public int failedRuns() {
      return (int) attempts.stream().filter(CaseResult::failed).count();
    }

    /** Runs with the verdict INCONCLUSIVE. */
    public int inconclusiveRuns() {
      return (int) attempts.stream().filter(CaseResult::inconclusive).count();
    }

    /** The case's requirements over all runs, see {@link RequirementSummary}. */
    public List<RequirementSummary> requirements() {
      return RequirementSummary.of(attempts);
    }

    // One line per requirement that passed in some runs and failed in others, and one per target
    // failure when the case itself is inconsistent.
    private List<String> renderInconsistencies() {
      var requirements = requirements();
      var nameCounts = new LinkedHashMap<String, Integer>();
      for (var requirement : requirements)
        nameCounts.merge(requirement.evaluator(), 1, Integer::sum);
      var lines = new ArrayList<String>();
      for (var requirement : requirements) {
        var runnerResult = requirement.evaluator().equals(TARGET);
        if (requirement.outcome() == Outcome.INCONSISTENT
            || (runnerResult && outcome() == Outcome.INCONSISTENT)) {
          lines.add(requirement.render(nameCounts.get(requirement.evaluator()) > 1));
        }
      }
      return lines;
    }
  }

  /** {@code run 2} or {@code runs 1, 3}. */
  static String describeRuns(List<Integer> runs) {
    return (runs.size() == 1 ? "run " : "runs ")
        + runs.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }

  /** {@code 1 case} or {@code 3 cases}. */
  static String plural(long n, String noun) {
    return n + " " + noun + (n == 1 ? "" : "s");
  }

  /** The batch outcome: the attempts, the cases over their runs, and the gate's verdict. */
  public interface EvalReport {

    /**
     * The name given with {@link Experiment#name}, or the test method that ran the experiment, for
     * example {@code SupportAgentEvalTest.qualityGate}.
     */
    String name();

    /** How many times every case ran, see {@link Experiment#repeat}. */
    int runs();

    /**
     * The file {@link Experiment#run} wrote the report to: {@code <name>-<start time>.json} in the
     * report directory, for example {@code
     * SupportAgentEvalTest.qualityGate-20261001-101530-123.json}, holding {@link #document()} as
     * JSON. Empty when the experiment was run {@link Experiment#withoutReportFile} or the file
     * could not be written.
     */
    Optional<Path> reportFile();

    /**
     * The report as data, for tools that collect runs or render their own reports. This is what
     * {@link #reportFile()} holds, and what {@link ReportDocument#read} gives back.
     */
    ReportDocument document();

    /** Whether the gate passed. */
    boolean passed();

    /** The share of attempts that passed. An inconclusive attempt counts against the rate. */
    double passRate();

    /**
     * One result per attempt: every case in the order the cases were given, its runs ascending. One
     * result per case without {@link Experiment#repeat}.
     */
    List<CaseResult> results();

    /** One summary per case, in the order the cases were given, with its outcome over all runs. */
    List<CaseSummary> cases();

    /**
     * The run as text: the gate verdict, rates per evaluator, decisions per guardrail, the
     * requirements that passed in some runs and failed in others, and the attempts that did not
     * pass with their evidence.
     */
    String render();
  }

  record Report(
      String name,
      Instant startedAt,
      Instant finishedAt,
      int runs,
      List<CaseSummary> cases,
      Gate.Verdict verdict,
      Optional<Path> reportFile)
      implements EvalReport {

    private Report withReportFile(Optional<Path> file) {
      return new Report(name, startedAt, finishedAt, runs, cases, verdict, file);
    }

    @Override
    public ReportDocument document() {
      return EvalReportJson.document(this);
    }

    @Override
    public boolean passed() {
      return verdict.passed();
    }

    @Override
    public List<CaseResult> results() {
      return cases.stream().flatMap(c -> c.attempts().stream()).toList();
    }

    @Override
    public double passRate() {
      var results = results();
      if (results.isEmpty()) return 0;
      return (double) results.stream().filter(CaseResult::passed).count() / results.size();
    }

    @Override
    public String render() {
      var results = results();
      var passedAttempts = results.stream().filter(CaseResult::passed).count();
      var inconclusiveAttempts = results.stream().filter(CaseResult::inconclusive).count();
      var text = new StringBuilder();
      text.append(
          String.format(
              Locale.ROOT,
              "%s, %s: %d/%d attempts passed (%.0f%%)",
              plural(cases.size(), "case"),
              plural(runs, "run"),
              passedAttempts,
              results.size(),
              passRate() * 100));
      if (inconclusiveAttempts > 0) {
        text.append(", ").append(inconclusiveAttempts).append(" inconclusive");
      }
      text.append('\n');
      text.append("gate: ")
          .append(verdict.passed() ? "passed" : "FAILED")
          .append(verdict.detail().isEmpty() ? "" : " — " + verdict.detail())
          .append('\n');
      rates(results)
          .forEach(
              (evaluator, rate) -> text.append("  ").append(rate.render(evaluator)).append('\n'));
      guardrailCounts(results)
          .forEach(
              (guardrail, counts) ->
                  text.append("  ").append(counts.render(guardrail)).append('\n'));
      var spend = spend();
      if (spend.attemptsWithEvidence() > 0) {
        text.append(spend.render(results.size())).append('\n');
      }
      var inconsistencies =
          cases.stream().flatMap(c -> c.renderInconsistencies().stream()).toList();
      if (!inconsistencies.isEmpty()) {
        text.append("inconsistent across runs:\n");
        inconsistencies.forEach(line -> text.append("  ").append(line).append('\n'));
      }
      results.stream()
          .filter(result -> !result.passed())
          .forEach(result -> text.append(result.describe()));
      return text.toString();
    }

    /** Model responses, tokens and latency summed over the attempts with a recorded interaction. */
    Spend spend() {
      var recorded =
          results().stream().filter(c -> c.evidence().interaction().isPresent()).toList();
      if (recorded.isEmpty()) return Spend.NONE;
      var slowest =
          recorded.stream().max(Comparator.comparing(c -> c.evidence().latency())).orElseThrow();
      return new Spend(
          recorded.size(),
          recorded.stream().mapToInt(c -> c.evidence().modelResponses()).sum(),
          recorded.stream().mapToLong(c -> c.evidence().inputTokens()).sum(),
          recorded.stream().mapToLong(c -> c.evidence().outputTokens()).sum(),
          recorded.stream().mapToLong(c -> c.evidence().latency().toMillis()).sum(),
          slowest.caseId(),
          slowest.run(),
          slowest.evidence().latency().toMillis());
    }

    /** Decision counts per guardrail, in the order the guardrails first appear in the attempts. */
    static Map<String, GuardrailCounts> guardrailCounts(List<CaseResult> attempts) {
      var counts = new LinkedHashMap<String, GuardrailCounts>();
      for (var attempt : attempts) {
        for (var decision : attempt.evidence().guardrails()) {
          counts
              .computeIfAbsent(decision.name(), name -> new GuardrailCounts())
              .count(decision.decision());
        }
      }
      return counts;
    }

    /** Pass counts per evaluator, over the attempts where it was conclusive. */
    static Map<String, Rate> rates(List<CaseResult> attempts) {
      var rates = new LinkedHashMap<String, Rate>();
      for (var attempt : attempts) {
        for (var evalResult : attempt.evalResults()) {
          rates
              .computeIfAbsent(evalResult.evaluator(), name -> new Rate())
              .count(evalResult.verdict());
        }
      }
      return rates;
    }
  }

  /**
   * The model responses, tokens and latency summed over the attempts with a recorded interaction.
   */
  record Spend(
      int attemptsWithEvidence,
      int modelResponses,
      long inputTokens,
      long outputTokens,
      long latencyMs,
      String slowestCaseId,
      int slowestRun,
      long slowestLatencyMs) {

    static final Spend NONE = new Spend(0, 0, 0, 0, 0, "", 1, 0);

    private String render(int attempts) {
      return String.format(
          Locale.ROOT,
          "spend: %d model responses, %d tokens in, %d out, %d ms in total, slowest %s run %d at"
              + " %d ms, over %d/%d attempts with a recorded interaction",
          modelResponses,
          inputTokens,
          outputTokens,
          latencyMs,
          slowestCaseId,
          slowestRun,
          slowestLatencyMs,
          attemptsWithEvidence,
          attempts);
    }
  }

  /** One guardrail's decision counts over the attempts. */
  static final class GuardrailCounts {
    private int allowed;
    private int denied;
    private int failed;

    private void count(GuardrailDecisionRecord.Decision decision) {
      switch (decision) {
        case ALLOW -> allowed++;
        case DENY -> denied++;
        case FAIL -> failed++;
        case UNSPECIFIED -> {}
      }
    }

    int allowed() {
      return allowed;
    }

    int denied() {
      return denied;
    }

    int failed() {
      return failed;
    }

    private String render(String guardrail) {
      var text = "guardrail " + guardrail + ": denied " + denied + ", allowed " + allowed;
      return failed == 0 ? text : text + ", failed " + failed;
    }
  }

  /** One evaluator's verdict counts over the attempts. */
  static final class Rate {
    private int passed;
    private int failed;
    private int inconclusive;
    private int notApplicable;

    private void count(EvalResult.Verdict verdict) {
      switch (verdict) {
        case PASS -> passed++;
        case FAIL -> failed++;
        case INCONCLUSIVE -> inconclusive++;
        case NOT_APPLICABLE -> notApplicable++;
      }
    }

    int passed() {
      return passed;
    }

    int failed() {
      return failed;
    }

    int inconclusive() {
      return inconclusive;
    }

    int notApplicable() {
      return notApplicable;
    }

    private String render(String evaluator) {
      var undecided = new ArrayList<String>();
      if (inconclusive > 0) undecided.add(inconclusive + " inconclusive");
      if (notApplicable > 0) undecided.add(notApplicable + " not applicable");
      var suffix = undecided.isEmpty() ? "" : " (" + String.join(", ", undecided) + ")";
      return evaluator + " " + passed + "/" + (passed + failed) + suffix;
    }
  }
}
