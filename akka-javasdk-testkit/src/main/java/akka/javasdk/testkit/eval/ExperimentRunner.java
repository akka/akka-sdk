/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs cases against an agent and collects the results. Sequential and in-process, nothing is
 * persisted.
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
 * <p>{@link Experiment#run} writes the report as JSON to {@code target/eval-reports/<name>.json},
 * see {@link EvalReport#reportFile}.
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
          Optional.of(DEFAULT_REPORT_DIRECTORY));
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
      Optional<Path> reportDirectory)
      implements Experiment {

    @Override
    public Experiment gate(Gate gate) {
      if (gate == null) throw new IllegalArgumentException("gate required");
      return new Ready<>(cases, evaluators, bindings, target, gate, name, reportDirectory);
    }

    @Override
    public Experiment name(String name) {
      if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
      return new Ready<>(cases, evaluators, bindings, target, gate, name, reportDirectory);
    }

    @Override
    public Experiment reportDirectory(Path directory) {
      if (directory == null) throw new IllegalArgumentException("directory required");
      return new Ready<>(cases, evaluators, bindings, target, gate, name, Optional.of(directory));
    }

    @Override
    public Experiment withoutReportFile() {
      return new Ready<>(cases, evaluators, bindings, target, gate, name, Optional.empty());
    }

    @Override
    public EvalReport run() {
      var startedAt = Instant.now();
      var reportName = name.isEmpty() ? defaultName(startedAt) : name;
      var results = cases.stream().map(this::evaluate).toList();
      var report =
          new Report(
              reportName, startedAt, Instant.now(), results, gate.check(results), Optional.empty());
      return reportDirectory
          .map(directory -> report.withReportFile(write(report, directory)))
          .orElse(report);
    }

    // A report file that cannot be written is logged, the report itself still returns.
    private static Optional<Path> write(Report report, Path directory) {
      var file = directory.resolve(report.name() + ".json");
      try {
        Files.createDirectories(directory);
        Files.writeString(file, EvalReportJson.render(report.document()));
        log.info("Eval report written to {}", file.toAbsolutePath());
        return Optional.of(file);
      } catch (IOException | RuntimeException e) {
        log.warn("Eval report could not be written to {}: {}", file.toAbsolutePath(), e.toString());
        return Optional.empty();
      }
    }

    // The test method is the first caller outside the runner that carries a test annotation;
    // without one, the first caller outside the runner, such as a helper in the test class.
    private static String defaultName(Instant startedAt) {
      var callers =
          StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
              .walk(frames -> frames.filter(f -> !isRunnerFrame(f)).limit(64).toList());
      var caller =
          callers.stream()
              .filter(Ready::isTestMethod)
              .findFirst()
              .or(() -> callers.stream().findFirst())
              .map(f -> f.getDeclaringClass().getSimpleName() + "." + f.getMethodName())
              .orElse("experiment");
      return caller + "-" + TIMESTAMP.format(startedAt);
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

    private CaseResult evaluate(EvalCase<C> evalCase) {
      try {
        bindings.load(evalCase.recordedCalls());
      } catch (RuntimeException e) {
        return new CaseResult(
            evalCase.id(),
            Interaction.of(evalCase.commandText(), ""),
            List.of(EvalResult.fail(describe(e)).attributedTo(Evaluators.SETUP)));
      }

      var turn =
          new EvalTarget.Turn<>(UUID.randomUUID().toString(), evalCase.id(), evalCase.command());
      EvalTarget.Outcome outcome;
      try {
        outcome = target.call(turn);
      } catch (RuntimeException e) {
        outcome = EvalTarget.Outcome.failed(e, List.of());
      }

      return switch (outcome) {
        case EvalTarget.Outcome.Failed failed ->
            new CaseResult(
                evalCase.id(),
                new Interaction(evalCase.commandText(), "", failed.toolCalls()),
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
          yield new CaseResult(evalCase.id(), interaction, List.copyOf(results));
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

  private static final Set<String> TEST_ANNOTATIONS =
      Set.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate");

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

  private static String describe(RuntimeException e) {
    return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
  }

  /** One case's evidence and results. */
  public record CaseResult(String caseId, Interaction interaction, List<EvalResult> evalResults) {

    /** No failed result. A failed load of the recorded calls or a failed agent call is one. */
    public boolean passed() {
      return evalResults.stream().noneMatch(f -> f.verdict() == EvalResult.Verdict.FAIL);
    }

    /** The evidence and the results as text, for a failed test's output. */
    public String describe() {
      var text = new StringBuilder();
      text.append("case ").append(caseId).append(passed() ? " passed" : " FAILED").append('\n');
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

  /** The batch outcome: per-case results and the gate's verdict. */
  public interface EvalReport {

    /**
     * The name given with {@link Experiment#name}, or the test method that ran the experiment and
     * the start time, for example {@code SupportAgentEvalTest.qualityGate-20261001-101530-123}.
     */
    String name();

    /**
     * The file {@link Experiment#run} wrote the report to: {@code <name>.json} in the report
     * directory, holding {@link #document()} as JSON. Empty when the experiment was run {@link
     * Experiment#withoutReportFile} or the file could not be written.
     */
    Optional<Path> reportFile();

    /**
     * The report as data, for tools that collect runs or render their own reports. This is what
     * {@link #reportFile()} holds, and what {@link ReportDocument#read} gives back.
     */
    ReportDocument document();

    /** Whether the gate passed. */
    boolean passed();

    /** The share of cases with no failed result. */
    double passRate();

    /** One result per case, in the order the cases were given. */
    List<CaseResult> results();

    /** The run as text: the gate verdict, rates per evaluator, and failed cases with evidence. */
    String render();
  }

  record Report(
      String name,
      Instant startedAt,
      Instant finishedAt,
      List<CaseResult> results,
      Gate.Verdict verdict,
      Optional<Path> reportFile)
      implements EvalReport {

    private Report withReportFile(Optional<Path> file) {
      return new Report(name, startedAt, finishedAt, results, verdict, file);
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
    public double passRate() {
      if (results.isEmpty()) return 0;
      return (double) results.stream().filter(CaseResult::passed).count() / results.size();
    }

    @Override
    public String render() {
      var passedCases = results.stream().filter(CaseResult::passed).count();
      var text = new StringBuilder();
      text.append(
          String.format(
              Locale.ROOT,
              "%d/%d cases passed (%.0f%%)%n",
              passedCases,
              results.size(),
              passRate() * 100));
      text.append("gate: ")
          .append(verdict.passed() ? "passed" : "FAILED")
          .append(verdict.detail().isEmpty() ? "" : " — " + verdict.detail())
          .append('\n');
      rates()
          .forEach(
              (evaluator, rate) -> text.append("  ").append(rate.render(evaluator)).append('\n'));
      var spend = spend();
      if (spend.casesWithEvidence() > 0) text.append(spend.render(results.size())).append('\n');
      results.stream()
          .filter(result -> !result.passed())
          .forEach(result -> text.append(result.describe()));
      return text.toString();
    }

    /** Model calls, tokens and latency summed over the cases with model calls in the evidence. */
    Spend spend() {
      var traced = results.stream().filter(c -> !c.interaction().modelCalls().isEmpty()).toList();
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
          slowest.interaction().latency().toMillis());
    }

    /** Pass counts per evaluator, over the cases where it was conclusive. */
    Map<String, Rate> rates() {
      var rates = new LinkedHashMap<String, Rate>();
      for (var result : results) {
        for (var evalResult : result.evalResults()) {
          rates
              .computeIfAbsent(evalResult.evaluator(), name -> new Rate())
              .count(evalResult.verdict());
        }
      }
      return rates;
    }
  }

  /**
   * The model calls, tokens and latency summed over the cases whose evidence carries model calls.
   */
  record Spend(
      int casesWithEvidence,
      int modelCalls,
      long inputTokens,
      long outputTokens,
      long latencyMs,
      String slowestCaseId,
      long slowestLatencyMs) {

    static final Spend NONE = new Spend(0, 0, 0, 0, 0, "", 0);

    private String render(int cases) {
      return String.format(
          Locale.ROOT,
          "spend: %d model calls, %d tokens in, %d out, %d ms in total, slowest %s at %d ms,"
              + " over %d/%d cases with evidence",
          modelCalls,
          inputTokens,
          outputTokens,
          latencyMs,
          slowestCaseId,
          slowestLatencyMs,
          casesWithEvidence,
          cases);
    }
  }

  /** One evaluator's verdict counts over the cases. */
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
