/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.JsonSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Reads cases from a JSON Lines file: one JSON object per line with {@code id} and {@code command}.
 * A String command is a JSON string. Any other command is a JSON object, decoded into the command
 * type of the agent's command handler with the SDK's Jackson mapper. Blank lines are skipped.
 *
 * <pre>{@code
 * {"id": "customer-lookup", "command": "Is cust_1 still one of our customers?"}
 * {"id": "tickets-of-the-caller", "command": {"customerId": "cust_7", "text": "What am I waiting on?"}}
 * }</pre>
 *
 * <p>The file carries no expectations. What the agent must do with a command is stated by the
 * evaluators bound to it.
 */
public final class EvalCases {

  private EvalCases() {}

  /**
   * Reads the cases of a file.
   *
   * @param file the JSON Lines file
   * @param commandType the command type of the agent's command handler, {@code String.class} for a
   *     handler that takes the user message
   * @return the cases in file order
   * @throws IllegalArgumentException when a line is not a case: no id, no command, a command that
   *     does not decode into the type, or an id a previous line uses
   * @throws UncheckedIOException when the file cannot be read
   */
  public static <C> List<EvalCase<C>> readJsonLines(Path file, Class<C> commandType) {
    if (file == null) throw new IllegalArgumentException("file required");
    try {
      return parse(Files.readString(file), file.toString(), commandType);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the cases in " + file, e);
    }
  }

  /**
   * Reads the cases of a classpath resource, for example a file under {@code src/test/resources}.
   *
   * @param resource the resource name, as {@link ClassLoader#getResourceAsStream} takes it
   * @param commandType the command type of the agent's command handler, {@code String.class} for a
   *     handler that takes the user message
   * @return the cases in file order
   * @throws IllegalArgumentException when the resource does not exist or a line is not a case
   * @throws UncheckedIOException when the resource cannot be read
   */
  public static <C> List<EvalCase<C>> readJsonLinesResource(String resource, Class<C> commandType) {
    if (resource == null || resource.isBlank())
      throw new IllegalArgumentException("resource required");
    var loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) loader = EvalCases.class.getClassLoader();
    try (InputStream stream = loader.getResourceAsStream(resource)) {
      if (stream == null) throw new IllegalArgumentException("no resource " + resource);
      return parse(
          new String(stream.readAllBytes(), StandardCharsets.UTF_8), resource, commandType);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the cases in " + resource, e);
    }
  }

  static <C> List<EvalCase<C>> parse(String content, String source, Class<C> commandType) {
    if (commandType == null) throw new IllegalArgumentException("commandType required");
    var mapper = JsonSupport.getObjectMapper();
    var cases = new ArrayList<EvalCase<C>>();
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
      if (!ids.add(id.asText()))
        throw new IllegalArgumentException(
            where + " reuses the id " + id.asText() + ", which a previous line uses");
      C decoded;
      try {
        decoded = mapper.treeToValue(command, commandType);
      } catch (JsonProcessingException | IllegalArgumentException e) {
        throw new IllegalArgumentException(
            where
                + " has a command that does not decode into "
                + commandType.getName()
                + ": "
                + e.getMessage(),
            e);
      }
      try {
        cases.add(new EvalCase<>(id.asText(), decoded));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
      }
    }
    return List.copyOf(cases);
  }
}
