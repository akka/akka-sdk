/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalCasesTest {

  record Ask(String customerId, String text) {}

  @Test
  void readsStringCommands(@TempDir Path dir) throws Exception {
    var file = dir.resolve("cases.jsonl");
    Files.writeString(
        file,
        """
        {"id": "customer-lookup", "command": "Is cust_1 still one of our customers?"}

        {"id": "smalltalk", "command": "hi there!"}
        """);

    assertThat(EvalCases.readJsonLines(file, String.class))
        .containsExactly(
            EvalCase.of("customer-lookup", "Is cust_1 still one of our customers?"),
            EvalCase.of("smalltalk", "hi there!"));
  }

  @Test
  void readsCommandsOfTheHandlersOwnType() {
    var cases =
        EvalCases.parse(
            """
            {"id": "tickets", "command": {"customerId": "cust_7", "text": "What am I waiting on?"}}
            """,
            "cases",
            Ask.class);

    assertThat(cases)
        .containsExactly(EvalCase.of("tickets", new Ask("cust_7", "What am I waiting on?")));
  }

  @Test
  void readsAClasspathResource() {
    var cases =
        EvalCases.readJsonLinesResource("akka/javasdk/testkit/eval/cases.jsonl", String.class);

    assertThat(cases).extracting(EvalCase::id).containsExactly("shipped", "closing");
  }

  @Test
  void aMissingResourceIsRefused() {
    assertThatThrownBy(() -> EvalCases.readJsonLinesResource("no/such.jsonl", String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("no resource no/such.jsonl");
  }

  @Test
  void aLineThatIsNotACaseNamesItself() {
    assertThatThrownBy(() -> EvalCases.parse("{\"command\": \"q\"}", "cases", String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cases line 1 has no id");
    assertThatThrownBy(() -> EvalCases.parse("\n{\"id\": \"c\"}", "cases", String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cases line 2 has no command");
    assertThatThrownBy(() -> EvalCases.parse("[1]", "cases", String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cases line 1 is not a JSON object");
    assertThatThrownBy(() -> EvalCases.parse("{broken", "cases", String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("cases line 1 is not JSON: ");
    assertThatThrownBy(
            () -> EvalCases.parse("{\"id\": \"c\", \"command\": \" \"}", "cases", String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cases line 1: command required");
  }

  @Test
  void aCommandThatDoesNotDecodeIntoTheTypeIsRefused() {
    assertThatThrownBy(
            () -> EvalCases.parse("{\"id\": \"c\", \"command\": \"text\"}", "cases", Ask.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "cases line 1 has a command that does not decode into " + Ask.class.getName() + ": ");
  }

  @Test
  void aReusedIdIsRefused() {
    assertThatThrownBy(
            () ->
                EvalCases.parse(
                    "{\"id\": \"c\", \"command\": \"a\"}\n{\"id\": \"c\", \"command\": \"b\"}",
                    "cases",
                    String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cases line 2 reuses the id c, which a previous line uses");
  }
}
