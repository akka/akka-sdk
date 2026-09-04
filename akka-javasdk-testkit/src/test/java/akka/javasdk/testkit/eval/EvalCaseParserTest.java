/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
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
  void primesTheBoundStubWithTheRecordedResult(@TempDir Path dir) throws IOException {
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

    var baseline = cases.get(0).expectations();
    assertThat(baseline.expectedTools()).containsExactly("getCustomer");
    assertThat(baseline.expectedOrder()).containsExactly("getCustomer");
    assertThat(baseline.toolArguments())
        .containsExactly(new Expectations.ToolArgument("getCustomer", "customerId", "cust_1"));

    cases.get(1).setup().run();
    assertThat(cases.get(1).expectations().expectedTools()).isEmpty();
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

    var spent = cases.get(0).expectations().budgets();
    assertThat(spent.modelCalls()).hasValue(2);
    assertThat(spent.tokens()).hasValue(165);
    assertThat(spent.latency()).contains(Duration.ofMillis(1500));

    var tokensOnly = cases.get(1).expectations().budgets();
    assertThat(tokensOnly.modelCalls()).isEmpty();
    assertThat(tokensOnly.tokens()).hasValue(450);
    assertThat(tokensOnly.latency()).isEmpty();

    assertThat(cases.get(2).expectations().budgets().any()).isFalse();

    var exact = EvalCaseParser.parse(file, ToolBindings.builder().build(), 1.0);
    assertThat(exact.get(0).expectations().budgets().tokens()).hasValue(110);
    assertThat(exact.get(0).expectations().budgets().latency()).contains(Duration.ofMillis(1000));
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
}
