/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  record Route(String tool) {}

  @Test
  void readsExpectedRepliesWithTheReplyType() {
    var strings =
        EvalCases.parse(
            """
            {"id": "top", "command": "Which five customers ordered the most?", "expected": "SELECT 1"}
            """,
            "cases",
            String.class,
            String.class,
            true);
    assertThat(strings)
        .containsExactly(EvalCase.of("top", "Which five customers ordered the most?", "SELECT 1"));

    var routes =
        EvalCases.parse(
            """
            {"id": "refund", "command": {"customerId": "c7", "text": "money back"}, "expected": {"tool": "open-refund"}}
            """,
            "cases",
            Ask.class,
            Route.class,
            true);
    assertThat(routes)
        .containsExactly(
            EvalCase.of("refund", new Ask("c7", "money back"), new Route("open-refund")));
  }

  @Test
  void readsAFileWithExpectedReplies(@TempDir Path dir) throws Exception {
    var file = dir.resolve("graded-cases.jsonl");
    Files.writeString(
        file,
        """
        {"id": "refund", "command": "I want my money back", "expected": {"tool": "open-refund"}}
        {"id": "status", "command": "Where is o_42?", "expected": {"tool": "track-order"}}
        """);

    assertThat(EvalCases.readJsonLines(file, String.class, Route.class))
        .containsExactly(
            EvalCase.of("refund", "I want my money back", new Route("open-refund")),
            EvalCase.of("status", "Where is o_42?", new Route("track-order")));
  }

  @Test
  void aCollectionReplyTypeIsRefused(@TempDir Path dir) {
    var file = dir.resolve("graded-cases.jsonl");
    for (var replyType : List.of(List.class, Set.class, Map.class)) {
      assertThatThrownBy(() -> EvalCases.readJsonLines(file, String.class, replyType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageStartingWith("The reply type " + replyType.getName() + " is a collection");
      assertThatThrownBy(
              () ->
                  EvalCases.readJsonLinesResource(
                      "akka/javasdk/testkit/eval/graded-cases.jsonl", String.class, replyType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageStartingWith("The reply type " + replyType.getName() + " is a collection");
    }
  }

  @Test
  void aFileWithAnExpectedReplyIsRefusedWithoutAReplyType(@TempDir Path dir) throws Exception {
    var file = dir.resolve("graded-cases.jsonl");
    Files.writeString(file, "{\"id\": \"c\", \"command\": \"q\", \"expected\": \"r\"}\n");

    assertThatThrownBy(() -> EvalCases.readJsonLines(file, String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("has an expected reply, but the cases are read without a reply type");
  }

  @Test
  void readsAClasspathResourceWithExpectedReplies() {
    var cases =
        EvalCases.readJsonLinesResource(
            "akka/javasdk/testkit/eval/graded-cases.jsonl", String.class, String.class);

    assertThat(cases)
        .extracting(c -> c.expected().orElseThrow())
        .containsExactly("Order o_42 is shipped.", "Goodbye.");
  }

  @Test
  void aLineWithAnExpectedReplyIsRefusedWithoutAReplyType() {
    assertThatThrownBy(
            () ->
                EvalCases.parse(
                    "{\"id\": \"c\", \"command\": \"q\", \"expected\": \"r\"}",
                    "cases",
                    String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "cases line 1 has an expected reply, but the cases are read without a reply type.");
  }

  @Test
  void aLineWithoutAnExpectedReplyIsRefusedWithAReplyType() {
    assertThatThrownBy(
            () ->
                EvalCases.parse(
                    "{\"id\": \"c\", \"command\": \"q\", \"expected\": \"r\"}\n"
                        + "{\"id\": \"d\", \"command\": \"q\"}",
                    "cases",
                    String.class,
                    String.class,
                    true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cases line 2 has no expected reply");
  }

  @Test
  void anExpectedReplyThatDoesNotDecodeIntoTheTypeIsRefused() {
    assertThatThrownBy(
            () ->
                EvalCases.parse(
                    "{\"id\": \"c\", \"command\": \"q\", \"expected\": \"text\"}",
                    "cases",
                    String.class,
                    Route.class,
                    true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "cases line 1 has an expected reply that does not decode into "
                + Route.class.getName()
                + ": ");
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
