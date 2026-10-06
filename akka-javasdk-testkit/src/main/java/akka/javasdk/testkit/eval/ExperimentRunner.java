/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;
import akka.javasdk.testkit.AgentTrace;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs cases against an agent and collects the results. Sequential and in-process; the only output
 * is the report file written at the end of the run.
 *
 * <p>Per case: load the recorded tool calls, if any, into the bound stubs, call the agent in a
 * fresh session, read the evidence the runtime traced for that session, and evaluate the
 * expectations over it. The stubs a hand written case needs are prepared before the run, in the
 * test.
 *
 * <p>An experiment is built in steps, each step offering only what comes next: the cases with
 * optional evaluators and {@link ToolBindings}, the agent, an optional {@link Gate}, then {@link
 * Experiment#run}. Without a gate every case must pass, which suits a mocked model. With a real
 * model gate on rates instead.
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
    return new Cases<>(testKit, List.copyOf(cases), List.of(), ToolBindings.none());
  }

  // For the runner's own tests: the cases run against a scripted target instead of an agent, and
  // write no report file unless the test names a directory.
  static <C> Experiment against(ExperimentCases<C> cases, EvalTarget<C> target) {
    return ((Cases<C>) cases).target(target).withoutReportFile();
  }

  private record Cases<C>(
      TestKit testKit, List<EvalCase<C>> cases, List<Evaluator> evaluators, ToolBindings bindings)
      implements ExperimentCases<C> {

    @Override
    public ExperimentCases<C> evaluator(Evaluator evaluator) {
      if (evaluator == null) throw new IllegalArgumentException("evaluator required");
      Evaluators.label(evaluator.getClass());

      var next = new ArrayList<>(evaluators);
      next.add(evaluator);
      return new Cases<>(testKit, cases, List.copyOf(next), bindings);
    }

    @Override
    public ExperimentCases<C> bindings(ToolBindings bindings) {
      if (bindings == null) throw new IllegalArgumentException("bindings required");
      return new Cases<>(testKit, cases, evaluators, bindings);
    }

    @Override
    public <A extends Agent, R> Experiment agent(Function2<A, C, Agent.Effect<R>> method) {
      return agent(method, Interaction::asText);
    }

    @Override
    public <A extends Agent, R> Experiment agent(
        Function2<A, C, Agent.Effect<R>> method, Function<R, String> replyText) {
      return target(new AgentTarget<>(testKit, method, replyText));
    }

    private Experiment target(EvalTarget<C> target) {
      if (target == null) throw new IllegalArgumentException("target required");
      requireDistinctLabels();
      requireBindingsForRecordedTools();
      return new Ready<>(
          cases,
          evaluators,
          bindings,
          target,
          Gate.allCasesShouldPass(),
          "",
          Optional.of(DEFAULT_REPORT_DIRECTORY),
          1);
    }

    private void requireDistinctLabels() {
      var classByLabel = new LinkedHashMap<String, Class<? extends Evaluator>>();

      for (var evalCase : cases) {
        for (var evaluator : evalCase.evaluators()) requireDistinctLabel(classByLabel, evaluator);
      }

      for (var evaluator : evaluators) requireDistinctLabel(classByLabel, evaluator);
    }

    private static void requireDistinctLabel(
        Map<String, Class<? extends Evaluator>> classByLabel, Evaluator evaluator) {
      var type = evaluator.getClass();
      var previous = classByLabel.putIfAbsent(Evaluators.label(type), type);

      if (previous != null && previous != type) {
        throw new IllegalArgumentException(
            previous.getName()
                + " and "
                + type.getName()
                + " share the same @EvalLabel \""
                + type.getAnnotation(EvalLabel.class).value()
                + "\"; the report groups results by label. Give each evaluator a unique label.");
      }
    }

    // Before the first agent call, so a new tool in production cannot be replayed by accident
    // against a stub that does not know it.
    private void requireBindingsForRecordedTools() {
      var casesByUnboundTool = new LinkedHashMap<String, List<String>>();
      for (var evalCase : cases) {
        for (var call : evalCase.recordedCalls()) {
          if (!bindings.binds(call.tool())) {
            casesByUnboundTool
                .computeIfAbsent(call.tool(), t -> new ArrayList<>())
                .add(evalCase.id());
          }
        }
      }
      if (casesByUnboundTool.isEmpty()) return;
      var missing =
          casesByUnboundTool.entrySet().stream()
              .map(
                  e ->
                      e.getKey()
                          + (e.getValue().size() == 1 ? " (case " : " (cases ")
                          + String.join(", ", e.getValue())
                          + ")")
              .collect(Collectors.joining(", "));
      throw new IllegalArgumentException(
          "no binding for recorded tool "
              + missing
              + "; bound: "
              + bindings.toolNames()
              + ". Bind each tool a recording names with bindings(ToolBindings)");
    }
  }

  private record Ready<C>(
      List<EvalCase<C>> cases,
      List<Evaluator> evaluators,
      ToolBindings bindings,
      EvalTarget<C> target,
      Gate gate,
      String name,
      Optional<Path> reportDirectory,
      int runs)
      implements Experiment {

    @Override
    public Experiment gate(Gate gate) {
      if (gate == null) throw new IllegalArgumentException("gate required");
      return new Ready<>(cases, evaluators, bindings, target, gate, name, reportDirectory, runs);
    }

    @Override
    public Experiment name(String name) {
      requireFileName(name);
      return new Ready<>(cases, evaluators, bindings, target, gate, name, reportDirectory, runs);
    }

    @Override
    public Experiment reportDirectory(Path directory) {
      if (directory == null) throw new IllegalArgumentException("directory required");
      return new Ready<>(
          cases, evaluators, bindings, target, gate, name, Optional.of(directory), runs);
    }

    @Override
    public Experiment withoutReportFile() {
      return new Ready<>(cases, evaluators, bindings, target, gate, name, Optional.empty(), runs);
    }

    @Override
    public Experiment repeat(int times) {
      if (times < 1) throw new IllegalArgumentException("repeat needs at least 1, was " + times);
      return new Ready<>(cases, evaluators, bindings, target, gate, name, reportDirectory, times);
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
      try {
        bindings.load(evalCase.recordedCalls());
      } catch (RuntimeException e) {
        return new CaseResult(
            evalCase.id(),
            run,
            Interaction.of(evalCase.commandText(), ""),
            List.of(EvalResult.fail(describe(e)).attributedTo(Evaluators.SETUP)));
      }

      var turn =
          new EvalTarget.Turn<>(UUID.randomUUID().toString(), evalCase.id(), evalCase.command());
      EvalTarget.Outcome outcome;
      try {
        outcome = target.call(turn);
      } catch (RuntimeException e) {
        outcome = EvalTarget.Outcome.failed(e, AgentTrace.NONE);
      }

      return switch (outcome) {
        case EvalTarget.Outcome.Failed failed ->
            new CaseResult(
                evalCase.id(),
                run,
                new Interaction(evalCase.commandText(), "", failed.trace()),
                List.of(EvalResult.fail(failed.reason()).attributedTo(Evaluators.TARGET)));
        case EvalTarget.Outcome.Answered answered -> {
          var interaction = answered.interaction();
          var results = new ArrayList<EvalResult>();
          for (var evaluator : evalCase.evaluators()) {
            results.add(evaluate(evaluator, evalCase, interaction));
          }
          for (var evaluator : evaluators) {
            results.add(evaluate(evaluator, evalCase, interaction));
          }
          yield new CaseResult(evalCase.id(), run, interaction, List.copyOf(results));
        }
      };
    }

    // An evaluator that throws or returns nothing fails its own result, the other evaluators
    // still report.
    private static EvalResult evaluate(
        Evaluator evaluator, EvalCase<?> evalCase, Interaction interaction) {
      var label = Evaluators.label(evaluator.getClass());

      EvalResult result;
      try {
        result = evaluator.evaluate(evalCase, interaction);
      } catch (RuntimeException e) {
        return EvalResult.fail("the evaluator threw " + describe(e)).attributedTo(label);
      }
      if (result == null) {
        return EvalResult.fail("the evaluator returned no result").attributedTo(label);
      }
      return result.attributedTo(label);
    }
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

  private static String describe(RuntimeException e) {
    return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
  }

  /**
   * One attempt: one case's evidence and results from one run.
   *
   * @param run the run the attempt belongs to, from 1; always 1 without {@link Experiment#repeat}
   */
  public record CaseResult(
      String caseId, int run, Interaction interaction, List<EvalResult> evalResults) {

    public CaseResult {
      if (run < 1) throw new IllegalArgumentException("run must be at least 1, was " + run);
    }

    /** An attempt of the first run. */
    public CaseResult(String caseId, Interaction interaction, List<EvalResult> evalResults) {
      this(caseId, 1, interaction, evalResults);
    }

    /** No failed result. A failed load of the recorded calls or a failed agent call is one. */
    public boolean passed() {
      return evalResults.stream().noneMatch(f -> f.verdict() == EvalResult.Verdict.FAIL);
    }

    /** The evidence and the results as text, for a failed test's output. */
    public String describe() {
      var text = new StringBuilder();
      text.append("case ")
          .append(caseId)
          .append(" run ")
          .append(run)
          .append(passed() ? " passed" : " FAILED")
          .append('\n');
      text.append("  reply: ").append(oneLine(interaction.reply())).append('\n');
      if (!interaction.finalModelText().isEmpty()
          && !interaction.finalModelText().equals(interaction.reply())) {
        text.append("  model text: ").append(oneLine(interaction.finalModelText())).append('\n');
      }
      text.append("  tools: ").append(toolEvidence()).append('\n');
      if (!interaction.modelCalls().isEmpty()) {
        text.append("  model: ").append(modelEvidence()).append('\n');
      }
      if (!interaction.guardrails().isEmpty()) {
        text.append("  guardrails: ").append(guardrailEvidence()).append('\n');
      }
      if (evalResults.isEmpty()) {
        text.append("  results: none declared\n");
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
      if (interaction.toolCalls().isEmpty()) return "none called";
      return interaction.toolCalls().stream()
          .map(
              call ->
                  call.name()
                      + call.arguments()
                      + call.error().map(e -> " (failed: " + e + ")").orElse(""))
          .reduce((a, b) -> a + " → " + b)
          .orElse("");
    }

    private String modelEvidence() {
      return interaction.modelCalls().size()
          + " calls, "
          + interaction.inputTokens()
          + " tokens in, "
          + interaction.outputTokens()
          + " out, "
          + interaction.latency().toMillis()
          + " ms";
    }

    private String guardrailEvidence() {
      return interaction.guardrails().stream()
          .map(g -> g.name() + (g.passed() ? " passed" : " blocked: " + g.explanation()))
          .reduce((a, b) -> a + ", " + b)
          .orElse("");
    }

    private static String oneLine(String text) {
      var flat = text.replaceAll("\\s+", " ").trim();
      return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
    }
  }

  /** The outcome of a case or of a requirement over the runs. */
  public enum Outcome {
    /** Passed in every run; for a requirement, in every run where it was conclusive. */
    PASSED,
    /** Failed in every run; for a requirement, in every run where it was conclusive. */
    FAILED,
    /** Passed in some runs and failed in others. */
    INCONSISTENT,
    /** A requirement that was conclusive in no run. */
    INCONCLUSIVE
  }

  /**
   * One requirement of a case over all runs: one evaluator at one position in the results of the
   * case's attempts. Two requirements of a case may carry the same label, for example two {@link
   * Evaluators#shouldCallToolWith} expectations. The runner's {@link Evaluators#TARGET} and {@link
   * Evaluators#SETUP} results are requirements too, with no run passed.
   *
   * @param caseId the case
   * @param index the position of the result in the attempt, from 0
   * @param evaluator the evaluator label
   * @param passedIn the runs with the verdict PASS, ascending
   * @param failedIn the runs with the verdict FAIL, ascending
   * @param inconclusiveIn the runs with the verdict INCONCLUSIVE, ascending
   */
  public record RequirementSummary(
      String caseId,
      int index,
      String evaluator,
      List<Integer> passedIn,
      List<Integer> failedIn,
      List<Integer> inconclusiveIn) {

    public RequirementSummary {
      passedIn = List.copyOf(passedIn);
      failedIn = List.copyOf(failedIn);
      inconclusiveIn = List.copyOf(inconclusiveIn);
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
                      e.getValue().getOrDefault(EvalResult.Verdict.INCONCLUSIVE, List.of())))
          .toList();
    }

    private String render(boolean labelShared) {
      var name = labelShared ? evaluator + " #" + (index + 1) : evaluator;
      var parts = new ArrayList<String>();
      if (!passedIn.isEmpty()) parts.add("passed in " + describeRuns(passedIn));
      if (!failedIn.isEmpty()) parts.add("failed in " + describeRuns(failedIn));
      if (!inconclusiveIn.isEmpty()) parts.add("inconclusive in " + describeRuns(inconclusiveIn));
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

    /** PASSED in every run, FAILED in every run, or INCONSISTENT when both happened. */
    public Outcome outcome() {
      var passed = passedRuns();
      if (passed == attempts.size()) return Outcome.PASSED;
      if (passed == 0) return Outcome.FAILED;
      return Outcome.INCONSISTENT;
    }

    /** Runs with no failed result. */
    public int passedRuns() {
      return (int) attempts.stream().filter(CaseResult::passed).count();
    }

    /** Runs with a failed result. */
    public int failedRuns() {
      return attempts.size() - passedRuns();
    }

    /** The case's requirements over all runs, see {@link RequirementSummary}. */
    public List<RequirementSummary> requirements() {
      return RequirementSummary.of(attempts);
    }

    // One line per requirement that passed in some runs and failed in others, and one per target or
    // setup failure when the case itself is inconsistent.
    private List<String> renderInconsistencies() {
      var requirements = requirements();
      var labelCounts = new LinkedHashMap<String, Integer>();
      for (var requirement : requirements)
        labelCounts.merge(requirement.evaluator(), 1, Integer::sum);
      var lines = new ArrayList<String>();
      for (var requirement : requirements) {
        var runnerResult =
            requirement.evaluator().equals(Evaluators.TARGET)
                || requirement.evaluator().equals(Evaluators.SETUP);
        if (requirement.outcome() == Outcome.INCONSISTENT
            || (runnerResult && outcome() == Outcome.INCONSISTENT)) {
          lines.add(requirement.render(labelCounts.get(requirement.evaluator()) > 1));
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

    /** The share of attempts with no failed result. */
    double passRate();

    /**
     * One result per attempt: every case in the order the cases were given, its runs ascending. One
     * result per case without {@link Experiment#repeat}.
     */
    List<CaseResult> results();

    /** One summary per case, in the order the cases were given, with its outcome over all runs. */
    List<CaseSummary> cases();

    /**
     * The run as text: the gate verdict, rates per evaluator, the requirements that passed in some
     * runs and failed in others, and failed attempts with evidence.
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
      var text = new StringBuilder();
      text.append(
          String.format(
              Locale.ROOT,
              "%s, %s: %d/%d attempts passed (%.0f%%)%n",
              plural(cases.size(), "case"),
              plural(runs, "run"),
              passedAttempts,
              results.size(),
              passRate() * 100));
      text.append("gate: ")
          .append(verdict.passed() ? "passed" : "FAILED")
          .append(verdict.detail().isEmpty() ? "" : " — " + verdict.detail())
          .append('\n');
      rates(results)
          .forEach(
              (evaluator, rate) -> text.append("  ").append(rate.render(evaluator)).append('\n'));
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

    /**
     * Model calls, tokens and latency summed over the attempts with model calls in the evidence.
     */
    Spend spend() {
      var traced = results().stream().filter(c -> !c.interaction().modelCalls().isEmpty()).toList();
      if (traced.isEmpty()) return Spend.NONE;
      var slowest =
          traced.stream().max(Comparator.comparing(c -> c.interaction().latency())).orElseThrow();
      return new Spend(
          traced.size(),
          traced.stream().mapToInt(c -> c.interaction().modelCalls().size()).sum(),
          traced.stream().mapToLong(c -> c.interaction().inputTokens()).sum(),
          traced.stream().mapToLong(c -> c.interaction().outputTokens()).sum(),
          traced.stream().mapToLong(c -> c.interaction().latency().toMillis()).sum(),
          slowest.caseId(),
          slowest.run(),
          slowest.interaction().latency().toMillis());
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
   * The model calls, tokens and latency summed over the attempts whose evidence carries model
   * calls.
   */
  record Spend(
      int attemptsWithEvidence,
      int modelCalls,
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
          "spend: %d model calls, %d tokens in, %d out, %d ms in total, slowest %s run %d at %d ms,"
              + " over %d/%d attempts with evidence",
          modelCalls,
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

  /** One evaluator's verdict counts over the attempts. */
  static final class Rate {
    private int passed;
    private int failed;
    private int inconclusive;

    private void count(EvalResult.Verdict verdict) {
      switch (verdict) {
        case PASS -> passed++;
        case FAIL -> failed++;
        case INCONCLUSIVE -> inconclusive++;
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

    private String render(String evaluator) {
      var undecided = inconclusive == 0 ? "" : " (" + inconclusive + " inconclusive)";
      return evaluator + " " + passed + "/" + (passed + failed) + undecided;
    }
  }
}
