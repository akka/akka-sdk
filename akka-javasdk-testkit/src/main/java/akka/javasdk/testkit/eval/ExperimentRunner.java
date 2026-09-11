/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * <pre>{@code
 * var runner = new ExperimentRunner(testKit);
 *
 * var report = runner.cases(cases).agent(SupportAgent::ask).gate(Gate.passRateAtLeast(0.9)).run();
 * assertThat(report.passed()).withFailMessage(report::render).isTrue();
 * }</pre>
 */
public final class ExperimentRunner {

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
   * @param more further cases
   */
  public ExperimentCases cases(EvalCase first, EvalCase... more) {
    var all = new ArrayList<EvalCase>();
    all.add(first);
    all.addAll(List.of(more));
    return cases(all);
  }

  /**
   * The cases the experiment runs.
   *
   * @param cases at least one case
   */
  public ExperimentCases cases(List<EvalCase> cases) {
    if (cases == null || cases.isEmpty()) {
      throw new IllegalArgumentException("at least one case required");
    }
    if (cases.stream().anyMatch(c -> c == null)) {
      throw new IllegalArgumentException("case required");
    }
    return new Cases(testKit, List.copyOf(cases), List.of(), ToolBindings.none());
  }

  // For the runner's own tests: the cases run against a scripted target instead of an agent.
  static Experiment against(ExperimentCases cases, EvalTarget target) {
    return ((Cases) cases).target(target);
  }

  private record Cases(
      TestKit testKit, List<EvalCase> cases, List<Evaluator> evaluators, ToolBindings bindings)
      implements ExperimentCases {

    @Override
    public ExperimentCases evaluator(Evaluator evaluator) {
      if (evaluator == null) throw new IllegalArgumentException("evaluator required");
      Evaluators.requireLabel(evaluator.getClass());
      var next = new ArrayList<>(evaluators);
      next.add(evaluator);
      return new Cases(testKit, cases, List.copyOf(next), bindings);
    }

    @Override
    public ExperimentCases bindings(ToolBindings bindings) {
      if (bindings == null) throw new IllegalArgumentException("bindings required");
      return new Cases(testKit, cases, evaluators, bindings);
    }

    @Override
    public <A extends Agent, R> Experiment agent(Function2<A, String, Agent.Effect<R>> method) {
      return agent(method, Function.identity(), AgentTarget::asText);
    }

    @Override
    public <A extends Agent, C, R> Experiment agent(
        Function2<A, C, Agent.Effect<R>> method,
        Function<String, C> command,
        Function<R, String> replyText) {
      return target(new AgentTarget<>(testKit, method, command, replyText));
    }

    private Experiment target(EvalTarget target) {
      if (target == null) throw new IllegalArgumentException("target required");
      requireBindingsForRecordedTools();
      return new Ready(cases, evaluators, bindings, target, Gate.allCasesPass());
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

  private record Ready(
      List<EvalCase> cases,
      List<Evaluator> evaluators,
      ToolBindings bindings,
      EvalTarget target,
      Gate gate)
      implements Experiment {

    @Override
    public Experiment gate(Gate gate) {
      if (gate == null) throw new IllegalArgumentException("gate required");
      return new Ready(cases, evaluators, bindings, target, gate);
    }

    @Override
    public EvalReport run() {
      var results = cases.stream().map(this::evaluate).toList();
      return new Report(results, gate.check(results));
    }

    private CaseResult evaluate(EvalCase evalCase) {
      try {
        bindings.load(evalCase.recordedCalls());
      } catch (RuntimeException e) {
        return new CaseResult(
            evalCase.id(),
            Interaction.of(evalCase.userMessage(), ""),
            List.of(EvalResult.fail(describe(e)).attributedTo(Evaluators.SETUP)));
      }

      var turn =
          new EvalTarget.Turn(UUID.randomUUID().toString(), evalCase.id(), evalCase.userMessage());
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
                new Interaction(evalCase.userMessage(), "", failed.toolCalls()),
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
        Evaluator evaluator, EvalCase evalCase, Interaction interaction) {
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

    /** Whether the gate passed. */
    boolean passed();

    /** The share of cases with no failed result. */
    double passRate();

    /** One result per case, in the order the cases were given. */
    List<CaseResult> results();

    /** The run as text: the gate verdict, rates per evaluator, and failed cases with evidence. */
    String render();
  }

  private record Report(List<CaseResult> results, Gate.Verdict verdict) implements EvalReport {

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
      spend().ifPresent(line -> text.append(line).append('\n'));
      results.stream()
          .filter(result -> !result.passed())
          .forEach(result -> text.append(result.describe()));
      return text.toString();
    }

    /** Model calls, tokens and latency summed over the cases with model calls in the evidence. */
    private Optional<String> spend() {
      var traced = results.stream().filter(c -> !c.interaction().modelCalls().isEmpty()).toList();
      if (traced.isEmpty()) return Optional.empty();
      var modelCalls = traced.stream().mapToInt(c -> c.interaction().modelCalls().size()).sum();
      var tokensIn = traced.stream().mapToLong(c -> c.interaction().inputTokens()).sum();
      var tokensOut = traced.stream().mapToLong(c -> c.interaction().outputTokens()).sum();
      var latencyTotal = traced.stream().mapToLong(c -> c.interaction().latency().toMillis()).sum();
      var slowest =
          traced.stream().max(Comparator.comparing(c -> c.interaction().latency())).orElseThrow();
      return Optional.of(
          String.format(
              Locale.ROOT,
              "spend: %d model calls, %d tokens in, %d out, %d ms in total, slowest %s at %d ms,"
                  + " over %d/%d cases with evidence",
              modelCalls,
              tokensIn,
              tokensOut,
              latencyTotal,
              slowest.caseId(),
              slowest.interaction().latency().toMillis(),
              traced.size(),
              results.size()));
    }

    /** Pass counts per evaluator, over the cases where it was conclusive. */
    private Map<String, Rate> rates() {
      var rates = new LinkedHashMap<String, Rate>();
      for (var result : results) {
        for (var evalResult : result.evalResults()) {
          rates
              .computeIfAbsent(evalResult.evaluator(), label -> new Rate())
              .count(evalResult.verdict());
        }
      }
      return rates;
    }
  }

  private static final class Rate {
    private int passed;
    private int judged;
    private int inconclusive;

    private void count(EvalResult.Verdict verdict) {
      switch (verdict) {
        case PASS -> {
          passed++;
          judged++;
        }
        case FAIL -> judged++;
        case INCONCLUSIVE -> inconclusive++;
      }
    }

    private String render(String evaluator) {
      var undecided = inconclusive == 0 ? "" : " (" + inconclusive + " inconclusive)";
      return evaluator + " " + passed + "/" + judged + undecided;
    }
  }
}
