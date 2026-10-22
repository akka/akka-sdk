/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import akka.javasdk.JsonSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;

/**
 * A tool call the model requested within a model response, together with its recorded response.
 *
 * @param id the id of the tool call, correlating the request with its response
 * @param name the name of the tool that was called
 * @param arguments the arguments passed to the tool, as a JSON string
 * @param response the result recorded for the call, as a string. Empty when the tool returned an
 *     empty result, and also when the ledger has no result for the call.
 */
public record ToolCall(String id, String name, String arguments, String response) {

  /**
   * The arguments passed to the tool, decoded from their JSON as the given type, for example a
   * record with one component per tool parameter.
   *
   * @throws IllegalArgumentException when the arguments do not decode as the type
   */
  public <T> T argumentsAs(Class<T> type) {
    try {
      return JsonSupport.getObjectMapper().readValue(arguments, type);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "the arguments of tool call [" + name + "] do not decode as " + type.getName(), e);
    }
  }

  /**
   * The result recorded for the call, decoded from its JSON as the given type. Empty when the call
   * has no result, for example when a guardrail denied the call or the tool failed.
   *
   * @throws IllegalArgumentException when the result does not decode as the type
   */
  public <T> Optional<T> responseAs(Class<T> type) {
    if (response == null || response.isBlank()) return Optional.empty();
    try {
      return Optional.of(JsonSupport.getObjectMapper().readValue(response, type));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "the response of tool call [" + name + "] does not decode as " + type.getName(), e);
    }
  }
}
