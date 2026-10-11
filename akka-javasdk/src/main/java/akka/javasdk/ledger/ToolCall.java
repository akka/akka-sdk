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
 * @param response the result of the call when the status is {@link Status#COMPLETED}, and the error
 *     text that the model was sent when the status is {@link Status#FAILED} and there is one. Empty
 *     for {@link Status#NOT_RUN}, {@link Status#PENDING} and {@link Status#NO_RESULT}. For {@link
 *     Status#UNSPECIFIED}, the recorded result, or empty when the ledger has none.
 * @param status what became of the call
 */
public record ToolCall(String id, String name, String arguments, String response, Status status) {

  /**
   * What became of a tool call, as known when the interaction was recorded. A later SDK version can
   * add values.
   */
  public enum Status {
    /**
     * The runtime did not report the status, or this version of the SDK does not know it. A record
     * that was written before the runtime recorded the status has this value.
     */
    UNSPECIFIED,
    /** The tool returned a result. {@code response} holds it, and it can be empty. */
    COMPLETED,
    /**
     * The tool failed, returned an error result, or its result could not be used. {@code response}
     * holds the error text when the model was sent one.
     */
    FAILED,
    /**
     * The runtime did not run the tool. The call was past the tool call limit, the request was
     * cancelled or the agent was stopped first, a guardrail denied the call, or its intent could
     * not be recorded.
     */
    NOT_RUN,
    /**
     * A capability tool call of an autonomous agent. The agent resolves it after the interaction,
     * and its result is a tool call response of a later interaction.
     */
    PENDING,
    /**
     * The interaction ended before the call finished, because another call of the round failed or
     * the caller cancelled while the round ran. The tool can run to completion afterwards.
     */
    NO_RESULT
  }

  /** A tool call with the status {@link Status#UNSPECIFIED}. */
  public ToolCall(String id, String name, String arguments, String response) {
    this(id, name, arguments, response, Status.UNSPECIFIED);
  }

  /**
   * The arguments passed to the tool, decoded from their JSON as the given type, for example a
   * record with one component per tool parameter. Empty arguments decode as an empty JSON object.
   *
   * @throws IllegalArgumentException when the arguments do not decode as the type
   */
  public <T> T argumentsAs(Class<T> type) {
    var json = arguments == null || arguments.isBlank() ? "{}" : arguments;
    try {
      return JsonSupport.getObjectMapper().readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "the arguments of tool call [" + name + "] do not decode as " + type.getName(), e);
    }
  }

  /**
   * The result recorded for the call, decoded from its JSON as the given type. Empty when the
   * ledger has no result for the call, for example when a guardrail denied the call.
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
