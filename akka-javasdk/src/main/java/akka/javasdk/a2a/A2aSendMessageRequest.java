/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * The request of {@code SendMessage} and {@code SendStreamingMessage}.
 *
 * @param tenant the tenant of the agent, when it serves several
 * @param message the message to send
 * @param configuration how the agent should handle the message
 * @param metadata free form details about the request
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aSendMessageRequest(
    String tenant, A2aMessage message, Configuration configuration, Map<String, Object> metadata) {

  public A2aSendMessageRequest {
    metadata = A2aValues.optionalMap(metadata);
  }

  /**
   * How an agent handles a message.
   *
   * @param acceptedOutputModes the media types the client accepts in the reply
   * @param taskPushNotificationConfig a webhook to register for the task
   * @param historyLength how many of the latest messages of the task history to return; zero for
   *     none, unset for the agent's default
   * @param returnImmediately true to return the task at once instead of waiting for it to finish
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Configuration(
      List<String> acceptedOutputModes,
      A2aTaskPushNotificationConfig taskPushNotificationConfig,
      Integer historyLength,
      @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean returnImmediately) {

    public Configuration {
      acceptedOutputModes = A2aValues.list(acceptedOutputModes);
    }
  }
}
