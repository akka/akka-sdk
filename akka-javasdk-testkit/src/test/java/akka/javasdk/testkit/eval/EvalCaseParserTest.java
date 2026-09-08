/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import akka.javasdk.testkit.ModelCall;
import akka.javasdk.testkit.ToolCall;
import akka.javasdk.testkit.eval.Evaluator.EvalResult.Verdict;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalCaseParserTest {

  record Customer(String id, String name) {}

  private static final String CAPTURE =
      """
      {"id":"c1","input":"Is cust_1 still a customer?","toolCalls":[\
      {"name":"getCustomer","arguments":{"customerId":"cust_1"},\
      "result":{"id":"cust_1","name":"Ada Lovelace"}}],\
      "output":"Yes, Ada Lovelace is an active customer."}
      {"id":"c2","input":"hi there!","toolCalls":[]}
      """;

  @Test
  void loadsTheRecordedResultIntoTheBoundStub(@TempDir Path dir) throws IOException {
    var file = Files.writeString(dir.resolve("captures.jsonl"), CAPTURE);
    var canned = new HashMap<String, Customer>();
    var bindings =
        ToolBindings.builder()
            .bind(
                "getCustomer",
                call ->
                    canned.put((String) call.argument("customerId"), call.resultAs(Customer.class)))
            .build();

    var cases = EvalCaseParser.parse(file, bindings);

    assertThat(cases).extracting(EvalCase::id).containsExactly("c1", "c2");

    cases.get(0).setup().run();
    assertThat(canned).containsEntry("cust_1", new Customer("cust_1", "Ada Lovelace"));

    // The baseline: the recorded tool, its order and its argument, as three evaluators.
    var asRecorded =
        new Interaction(
            "done", List.of(new ToolCall("getCustomer", Map.of("customerId", "cust_1"))));
    assertThat(verdicts(cases.get(0), asRecorded))
        .containsExactly(
            entry(Evaluators.TOOLS, Verdict.PASS),
            entry(Evaluators.TOOL_ORDER, Verdict.PASS),
            entry(Evaluators.TOOL_ARGUMENTS, Verdict.PASS));
    var otherCustomer =
        new Interaction(
            "done", List.of(new ToolCall("getCustomer", Map.of("customerId", "cust_2"))));
    assertThat(verdicts(cases.get(0), otherCustomer))
        .containsEntry(Evaluators.TOOL_ARGUMENTS, Verdict.FAIL);
    assertThat(verdicts(cases.get(0), Interaction.of("done")))
        .containsEntry(Evaluators.TOOLS, Verdict.FAIL);

    cases.get(1).setup().run();
    assertThat(cases.get(1).evaluators()).isEmpty();
  }

  @Test
  void recordedSpendBecomesBudgetsWithSlackOnTokensAndLatency(@TempDir Path dir)
      throws IOException {
    var file =
        Files.writeString(
            dir.resolve("captures.jsonl"),
            """
            {"id":"c1","input":"hi","toolCalls":[],"modelCalls":2,"tokens":{"input":100,"output":10},"latencyMs":1000}
            {"id":"c2","input":"hi","toolCalls":[],"tokens":300}
            {"id":"c3","input":"hi","toolCalls":[]}
            """);

    var cases = EvalCaseParser.parse(file, ToolBindings.builder().build());

    // c1: the model call count as recorded, tokens and latency with 1.5 slack.
    var spent = cases.get(0);
    assertThat(verdicts(spent, traced(2, 165, Duration.ofMillis(1500))))
        .containsExactly(
            entry(Evaluators.MODEL_CALL_BUDGET, Verdict.PASS),
            entry(Evaluators.TOKEN_BUDGET, Verdict.PASS),
            entry(Evaluators.LATENCY_BUDGET, Verdict.PASS));
    assertThat(verdicts(spent, traced(3, 166, Duration.ofMillis(1501))))
        .containsExactly(
            entry(Evaluators.MODEL_CALL_BUDGET, Verdict.FAIL),
            entry(Evaluators.TOKEN_BUDGET, Verdict.FAIL),
            entry(Evaluators.LATENCY_BUDGET, Verdict.FAIL));

    // c2: tokens only.
    var tokensOnly = cases.get(1);
    assertThat(verdicts(tokensOnly, traced(1, 450, Duration.ofMillis(1))))
        .containsExactly(entry(Evaluators.TOKEN_BUDGET, Verdict.PASS));
    assertThat(verdicts(tokensOnly, traced(1, 451, Duration.ofMillis(1))))
        .containsExactly(entry(Evaluators.TOKEN_BUDGET, Verdict.FAIL));

    assertThat(cases.get(2).evaluators()).isEmpty();

    // Slack 1.0 holds a case to exactly what was recorded.
    var exact = EvalCaseParser.parse(file, ToolBindings.builder().build(), 1.0).get(0);
    assertThat(verdicts(exact, traced(2, 110, Duration.ofMillis(1000))))
        .containsEntry(Evaluators.TOKEN_BUDGET, Verdict.PASS)
        .containsEntry(Evaluators.LATENCY_BUDGET, Verdict.PASS);
    assertThat(verdicts(exact, traced(2, 111, Duration.ofMillis(1001))))
        .containsEntry(Evaluators.TOKEN_BUDGET, Verdict.FAIL)
        .containsEntry(Evaluators.LATENCY_BUDGET, Verdict.FAIL);
  }

  @Test
  void refusesASpendFigureThatIsNotACount(@TempDir Path dir) throws IOException {
    var file =
        Files.writeString(
            dir.resolve("captures.jsonl"),
            """
            {"id":"c1","input":"hi","toolCalls":[],"modelCalls":0}
            {"id":"c2","input":"hi","toolCalls":[],"tokens":"many"}
            {"id":"c3","input":"hi","toolCalls":[],"latencyMs":-5}
            """);

    assertThatThrownBy(() -> EvalCaseParser.parse(file, ToolBindings.builder().build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("line 1: modelCalls")
        .hasMessageContaining("line 2: tokens")
        .hasMessageContaining("line 3: latencyMs");
    assertThatThrownBy(() -> EvalCaseParser.parse(file, ToolBindings.builder().build(), 0.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("slack");
  }

  @Test
  void refusesAnUnboundToolAtLoadTimeNamingTheLine(@TempDir Path dir) throws IOException {
    var file = Files.writeString(dir.resolve("captures.jsonl"), CAPTURE);
    var bindings = ToolBindings.builder().bind("somethingElse", call -> {}).build();

    assertThatThrownBy(() -> EvalCaseParser.parse(file, bindings))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("line 1")
        .hasMessageContaining("no binding for tool getCustomer");
  }

  @Test
  void parsesWithoutBindingsWhenNoToolIsNamed(@TempDir Path dir) throws IOException {
    var file =
        Files.writeString(
            dir.resolve("replies.jsonl"),
            "{\"id\":\"greeting\",\"input\":\"hi\",\"output\":\"Hello.\"}\n"
                + "{\"input\":\"thanks\",\"toolCalls\":[]}\n");

    var cases = EvalCaseParser.parse(file);

    assertThat(cases).hasSize(2);
    assertThat(cases.get(0).id()).isEqualTo("greeting");
    assertThat(cases.get(0).userMessage()).isEqualTo("hi");
    assertThat(cases.get(0).evaluators()).isEmpty();
    assertThat(cases.get(1).id()).isEqualTo("replay-2");
  }

  @Test
  void refusesAToolWhenParsedWithoutBindings(@TempDir Path dir) throws IOException {
    var file = Files.writeString(dir.resolve("captures.jsonl"), CAPTURE);

    assertThatThrownBy(() -> EvalCaseParser.parse(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("line 1")
        .hasMessageContaining("no binding for tool getCustomer");
  }

  @Test
  void reportsEveryProblemWithItsLineNumber(@TempDir Path dir) throws IOException {
    var file = Files.writeString(dir.resolve("captures.jsonl"), "not json\n\n{\"id\":\"x\"}\n");

    assertThatThrownBy(() -> EvalCaseParser.parse(file, ToolBindings.builder().build()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("line 1")
        .hasMessageContaining("line 3: no input");
  }

  @Test
  void recordedCallReadsResultIntoTheStubsType() {
    var call =
        new ToolBindings.RecordedCall(
            "getCustomer",
            Map.of("customerId", "cust_1"),
            "{\"id\":\"cust_1\",\"name\":\"Ada Lovelace\"}");

    assertThat(call.resultAs(Customer.class)).isEqualTo(new Customer("cust_1", "Ada Lovelace"));
    assertThatThrownBy(() -> call.resultAs(List.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("getCustomer");
  }

  /** The verdict of each of the case's evaluators over the evidence, in evaluator order. */
  private static Map<String, Verdict> verdicts(EvalCase evalCase, Interaction interaction) {
    var byName = new LinkedHashMap<String, Verdict>();
    for (var evaluator : evalCase.evaluators()) {
      var result = evaluator.evaluate(evalCase, interaction);
      byName.put(evaluator.name(), result.verdict());
    }
    return byName;
  }

  /** Evidence with model calls, the given total tokens and the given latency. */
  private static Interaction traced(int modelCalls, long tokens, Duration latency) {
    var calls =
        java.util.stream.IntStream.range(0, modelCalls)
            .mapToObj(
                i ->
                    new ModelCall(
                        "test",
                        "custom",
                        List.of("STOP"),
                        i == 0 ? tokens : 0,
                        0,
                        Duration.ofMillis(1),
                        "",
                        ""))
            .toList();
    return new Interaction("done", List.of(), calls, List.of(), latency, "done");
  }
}
