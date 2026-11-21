/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * INTERNAL API
 *
 * <p>The request of {@code DeleteTaskPushNotificationConfig}. The result is an empty object.
 *
 * @param tenant the tenant of the agent, when it serves several
 * @param taskId the id of the task
 * @param id the id of the push notification configuration
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aDeleteTaskPushNotificationConfigRequest(String tenant, String taskId, String id) {}
