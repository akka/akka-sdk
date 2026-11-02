/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.JsonSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Reads cases from a JSON Lines file: one JSON object per line with {@code id}, {@code command} and
 * optionally {@code expected}. A String command is a JSON string. Any other command is a JSON
 * object, decoded into the command type of the agent's command handler with the SDK's Jackson
 * mapper. The expected reply follows the same rule with the reply type of the handler. Blank lines
 * are skipped.
 *
 * <pre>{@code
 * {"id": "customer-lookup", "command": "Is cust_1 still one of our customers?"}
 * {"id": "tickets-of-the-caller", "command": {"customerId": "cust_7", "text": "What am I waiting on?"}}
 * }</pre>
 *
 * <pre>{@code
 * {"id": "top-customers", "command": "Which five customers ordered the most?", "expected": "SELECT ..."}
 * {"id": "refund", "command": {"customerId": "cust_7", "text": "I want my money back"}, "expected": {"tool": "open-refund"}}
 * }</pre>
 *
 * <p>The readers without a reply type refuse a line with {@code expected}, so a file with expected
 * replies is not run without comparing them. Within one file, either every line has {@code
 * expected} or none has.
 */
public final class EvalCases {

  private EvalCases() {}

  /**
   * Reads the cases of a file without expected replies.
   *
   * @param file the JSON Lines file
   * @param commandType the command type of the agent's command handler, {@code String.class} for a
   *     handler that takes the user message
   * @return the cases in file order
   * @throws IllegalArgumentException when a line is not a case: no id, no command, a command that
   *     does not decode into the type, an id a previous line uses, or an {@code expected} field
   * @throws UncheckedIOException when the file cannot be read
   */
  public static <C> List<EvalCase<C, Object>> readJsonLines(Path file, Class<C> commandType) {
    return readJsonLines(file, commandType, Object.class, false);
  }

  /**
   * Reads the cases of a file with expected replies.
   *
   * @param file the JSON Lines file
   * @param commandType the command type of the agent's command handler, {@code String.class} for a
   *     handler that takes the user message
   * @param replyType the reply type of the agent's command handler, {@code String.class} for a
   *     handler that replies with the model text
   * @return the cases in file order
   * @throws IllegalArgumentException when a line is not a case: no id, no command, no expected
   *     reply, a command or an expected reply that does not decode into its type, or an id a
   *     previous line uses
   * @throws UncheckedIOException when the file cannot be read
   */
  public static <C, V> List<EvalCase<C, V>> readJsonLines(
      Path file, Class<C> commandType, Class<V> replyType) {
    if (replyType == null) throw new IllegalArgumentException("replyType required");
    return readJsonLines(file, commandType, replyType, true);
  }

  private static <C, V> List<EvalCase<C, V>> readJsonLines(
      Path file, Class<C> commandType, Class<V> replyType, boolean expected) {
    if (file == null) throw new IllegalArgumentException("file required");
    try {
      return parse(Files.readString(file), file.toString(), commandType, replyType, expected);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the cases in " + file, e);
    }
  }

  /**
   * Reads the cases of a classpath resource without expected replies, for example a file under
   * {@code src/test/resources}.
   *
   * @param resource the resource name, as {@link ClassLoader#getResourceAsStream} takes it
   * @param commandType the command type of the agent's command handler, {@code String.class} for a
   *     handler that takes the user message
   * @return the cases in file order
   * @throws IllegalArgumentException when the resource does not exist or a line is not a case
   * @throws UncheckedIOException when the resource cannot be read
   */
  public static <C> List<EvalCase<C, Object>> readJsonLinesResource(
      String resource, Class<C> commandType) {
    return readJsonLinesResource(resource, commandType, Object.class, false);
  }

  /**
   * Reads the cases of a classpath resource with expected replies, for example a file under {@code
   * src/test/resources}.
   *
   * @param resource the resource name, as {@link ClassLoader#getResourceAsStream} takes it
   * @param commandType the command type of the agent's command handler, {@code String.class} for a
   *     handler that takes the user message
   * @param replyType the reply type of the agent's command handler, {@code String.class} for a
   *     handler that replies with the model text
   * @return the cases in file order
   * @throws IllegalArgumentException when the resource does not exist or a line is not a case
   * @throws UncheckedIOException when the resource cannot be read
   */
  public static <C, V> List<EvalCase<C, V>> readJsonLinesResource(
      String resource, Class<C> commandType, Class<V> replyType) {
    if (replyType == null) throw new IllegalArgumentException("replyType required");
    return readJsonLinesResource(resource, commandType, replyType, true);
  }

  private static <C, V> List<EvalCase<C, V>> readJsonLinesResource(
      String resource, Class<C> commandType, Class<V> replyType, boolean expected) {
    if (resource == null || resource.isBlank())
      throw new IllegalArgumentException("resource required");
    var loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) loader = EvalCases.class.getClassLoader();
    try (InputStream stream = loader.getResourceAsStream(resource)) {
      if (stream == null) throw new IllegalArgumentException("no resource " + resource);
      return parse(
          new String(stream.readAllBytes(), StandardCharsets.UTF_8),
          resource,
          commandType,
          replyType,
          expected);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the cases in " + resource, e);
    }
  }

  static <C> List<EvalCase<C, Object>> parse(String content, String source, Class<C> commandType) {
    return parse(content, source, commandType, Object.class, false);
  }

  /**
   * @param expected whether every line must carry an expected reply; without it, a line with one is
   *     refused
   */
  static <C, V> List<EvalCase<C, V>> parse(
      String content, String source, Class<C> commandType, Class<V> replyType, boolean expected) {
    if (commandType == null) throw new IllegalArgumentException("commandType required");
    var mapper = JsonSupport.getObjectMapper();
    var cases = new ArrayList<EvalCase<C, V>>();
    var ids = new HashSet<String>();
    var lines = content.split("\\R");
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      if (line.isBlank()) continue;
      var where = source + " line " + (i + 1);
      JsonNode node;
      try {
        node = mapper.readTree(line);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(where + " is not JSON: " + e.getOriginalMessage(), e);
      }
      if (!node.isObject()) throw new IllegalArgumentException(where + " is not a JSON object");
      var id = node.get("id");
      if (id == null || !id.isTextual() || id.asText().isBlank())
        throw new IllegalArgumentException(where + " has no id");
      var command = node.get("command");
      if (command == null || command.isNull())
        throw new IllegalArgumentException(where + " has no command");
      var expectedNode = node.get("expected");
      var hasExpected = expectedNode != null && !expectedNode.isNull();
      if (hasExpected && !expected)
        throw new IllegalArgumentException(
            where
                + " has an expected reply, but the cases are read without a reply type. Read them"
                + " with the reply type of the agent's command handler to compare the replies, or"
                + " remove expected from the file.");
      if (!hasExpected && expected)
        throw new IllegalArgumentException(where + " has no expected reply");
      if (!ids.add(id.asText()))
        throw new IllegalArgumentException(
            where + " reuses the id " + id.asText() + ", which a previous line uses");
      var decodedCommand = decode(mapper, command, commandType, where, "a command");
      Optional<V> decodedExpected =
          hasExpected
              ? Optional.of(decode(mapper, expectedNode, replyType, where, "an expected reply"))
              : Optional.empty();
      try {
        cases.add(new EvalCase<>(id.asText(), decodedCommand, decodedExpected));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
      }
    }
    return List.copyOf(cases);
  }

  private static <T> T decode(
      ObjectMapper mapper, JsonNode node, Class<T> type, String where, String what) {
    try {
      return mapper.treeToValue(node, type);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw new IllegalArgumentException(
          where
              + " has "
              + what
              + " that does not decode into "
              + type.getName()
              + ": "
              + e.getMessage(),
          e);
    }
  }
}
