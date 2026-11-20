/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The request of {@code GetTaskPushNotificationConfig}.
 *
 * @param tenant the tenant of the agent, when it serves several
 * @param taskId the id of the task
 * @param id the id of the push notification configuration
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aGetTaskPushNotificationConfigRequest(String tenant, String taskId, String id) {}
