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
 * @param response the result of the call as text when the status is {@link Status#COMPLETED}, and
 *     the error text when the status is {@link Status#FAILED}. Empty for {@link Status#NOT_RUN},
 *     {@link Status#PENDING} and {@link Status#NO_RESULT}. For {@link Status#UNSPECIFIED}, the
 *     recorded result, or empty when the ledger has none.
 * @param status what became of the call
 */
public record ToolCall(String id, String name, String arguments, String response, Status status) {

  /**
   * What became of a tool call, as known when the runtime recorded the interaction. A later SDK
   * version can add values.
   */
  public enum Status {
    /**
     * The runtime did not report the status, or this version of the SDK does not know it. A record
     * that an older runtime wrote has this value.
     */
    UNSPECIFIED,
    /**
     * The call has a result that is not an error result. {@code response} holds it, and it can be
     * empty. The runtime does not read the text, so a result that reports an error only in its text
     * has this status. A call of a failed interaction can have this status. The runtime then may
     * not have sent the result to the model.
     */
    COMPLETED,
    /**
     * The call has an error result. The tool returned one, or an autonomous agent turned the
     * failure of the call into one, for example a tool that throws or a tool name that does not
     * exist. {@code response} holds the error text. A call of a failed interaction can have this
     * status. The runtime then may not have sent the error text to the model.
     */
    FAILED,
    /**
     * The runtime did not run the tool. The call was past the tool call limit, or an autonomous
     * agent was stopped before the tool round ran. A capability tool call of an autonomous agent
     * also has this status when a guardrail blocked the response of the model or a tool result of
     * the tool round, because the agent then does not act on it.
     */
    NOT_RUN,
    /**
     * A capability tool call of an autonomous agent that the agent acts on after the interaction.
     * When the agent runs another iteration, its result is a tool call response of that
     * interaction.
     */
    PENDING,
    /**
     * The interaction ended before the call had a result, because the tool round failed or the
     * caller cancelled a streamed reply while the tool round ran. The tool can have run. When a
     * guardrail blocks a call of a tool round, every call of the round has this status, also a call
     * that completed and a capability tool call. The same holds when a tool fails on a single reply
     * or a streamed reply. An autonomous agent records a tool that fails as {@link #FAILED}
     * instead, and keeps the results of the other calls of the round.
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
   * The response of the call, decoded from its JSON as the given type. Empty when the response is
   * empty, for example when a guardrail denied the call or the tool returned an empty result. The
   * method decodes the response whatever the status, and the response of a {@link Status#FAILED}
   * call is the error text.
   *
   * @throws IllegalArgumentException when the response does not decode as the type, for example the
   *     error text of a failed call
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
