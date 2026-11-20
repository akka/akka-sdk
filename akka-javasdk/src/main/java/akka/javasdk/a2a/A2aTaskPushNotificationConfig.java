/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A webhook that an agent calls with updates about a task. This is also the request of {@code
 * CreateTaskPushNotificationConfig}.
 *
 * @param tenant the tenant of the agent, when it serves several
 * @param id the id of this configuration, set by the agent when the client leaves it empty
 * @param taskId the id of the task to notify about
 * @param url the URL that the agent calls with an {@link A2aStreamResponse}
 * @param token a token that the agent sends back so the client can match the call to the task
 * @param authentication how the agent authenticates its call to the URL
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aTaskPushNotificationConfig(
    String tenant,
    String id,
    String taskId,
    String url,
    String token,
    Authentication authentication) {

  /**
   * The credentials that the agent sends to the webhook.
   *
   * @param scheme the HTTP authentication scheme, for example {@code Bearer}
   * @param credentials the credentials for the scheme
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record Authentication(String scheme, String credentials) {}
}
