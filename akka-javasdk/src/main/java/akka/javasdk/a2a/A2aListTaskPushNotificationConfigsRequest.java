/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The request of {@code ListTaskPushNotificationConfigs}.
 *
 * @param tenant the tenant of the agent, when it serves several
 * @param taskId the id of the task
 * @param pageSize the maximum number of configurations to return
 * @param pageToken the {@code nextPageToken} of the previous response, to get the next page
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aListTaskPushNotificationConfigsRequest(
    String tenant, String taskId, Integer pageSize, String pageToken) {}
