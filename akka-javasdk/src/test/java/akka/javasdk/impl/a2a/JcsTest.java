/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import akka.javasdk.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JcsTest {

  private static String resource(String name) throws IOException {
    try (InputStream in = JcsTest.class.getResourceAsStream("/a2a-jcs/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static JsonNode json(String name) throws IOException {
    return JsonSupport.getObjectMapper().readTree(resource(name));
  }

  record NumberSample(String hex, String expected) {
    @Override
    public String toString() {
      return hex;
    }
  }

  static Stream<NumberSample> numberSamples() throws IOException {
    return resource("rfc8785-appendix-b-numbers.txt")
        .lines()
        .filter(line -> !line.startsWith("#") && !line.isBlank())
        .map(
            line -> {
              String[] parts = line.split(" ", 2);
              return new NumberSample(parts[0], parts.length > 1 ? parts[1] : "");
            });
  }

  @ParameterizedTest
  @MethodSource("numberSamples")
  void numbersAreWrittenAsEcmaScriptWritesThem(NumberSample sample) {
    double value = Double.longBitsToDouble(Long.parseUnsignedLong(sample.hex(), 16));
    if (sample.expected().isEmpty()) {
      assertThrows(IllegalArgumentException.class, () -> Jcs.number(value));
    } else {
      assertEquals(sample.expected(), Jcs.number(value));
    }
  }

  @Test
  void primitivesAreSerializedAndMembersSorted() throws IOException {
    assertEquals(
        resource("rfc8785-3.2.2-primitives.expected.txt"),
        Jcs.canonicalString(json("rfc8785-3.2.2-primitives.json")));
  }

  @Test
  void membersAreSortedAsUtf16CodeUnits() throws IOException {
    JsonNode sorted =
        JsonSupport.getObjectMapper()
            .readTree(Jcs.canonicalize(json("rfc8785-3.2.3-sorting.json")));
    List<String> values = sorted.properties().stream().map(e -> e.getValue().asText()).toList();
    assertEquals(
        List.of(
            "Carriage Return",
            "One",
            "Control",
            "Latin Small Letter O With Diaeresis",
            "Euro Sign",
            "Emoji: Grinning Face",
            "Hebrew Letter Dalet With Dagesh"),
        values);
  }

  @Test
  void sortingAppliesInsideArraysAndKeepsArrayOrder() throws IOException {
    JsonNode node =
        JsonSupport.getObjectMapper().readTree("{\"b\":[{\"y\":1,\"x\":2},3],\"a\":{}}");
    assertEquals("{\"a\":{},\"b\":[{\"x\":2,\"y\":1},3]}", Jcs.canonicalString(node));
  }

  @Test
  void loneSurrogatesAreRefused() {
    JsonNode node = JsonNodeFactory.instance.textNode("\ud800");
    assertThrows(IllegalArgumentException.class, () -> Jcs.canonicalize(node));
  }

  @Test
  void defaultValuesAreRemovedAsTheSpecificationExampleShows() throws IOException {
    JsonNode payload = AgentCardSigning.canonicalPayload(json("spec-8.4.1-default-removal.json"));
    assertEquals(resource("spec-8.4.1-default-removal.expected.txt"), Jcs.canonicalString(payload));
  }
}
