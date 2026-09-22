/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.JsonSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A structured judgment request as a {@link JudgmentModelProvider.Custom} provider receives it.
 *
 * @param stateJson the state as JSON text: a string literal, an object or an array
 * @param questions the questions by key, in request order
 */
public record JudgmentRequest(String stateJson, Map<String, Question> questions) {

  public JudgmentRequest {
    Objects.requireNonNull(stateJson, "stateJson");
    Objects.requireNonNull(questions, "questions");
    questions = Collections.unmodifiableMap(new LinkedHashMap<>(questions));
  }

  /** The state as plain text when it was given as a String, otherwise the JSON text. */
  public String stateAsText() {
    try {
      JsonNode node = JsonSupport.getObjectMapper().readTree(stateJson);
      return node.isTextual() ? node.asText() : stateJson;
    } catch (JsonProcessingException e) {
      return stateJson;
    }
  }
}
