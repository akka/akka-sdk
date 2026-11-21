/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * INTERNAL API
 *
 * <p>The request of {@code ListTasks}. An unset filter does not restrict the result.
 *
 * @param tenant the tenant of the agent, when it serves several
 * @param contextId only tasks in this conversation
 * @param status only tasks in this state, by its protocol name; read as text so that an unknown
 *     state is refused rather than read as unspecified
 * @param pageSize the maximum number of tasks to return, from 1 to 100
 * @param pageToken the {@code nextPageToken} of the previous response, to get the next page
 * @param historyLength how many of the latest messages of each task history to return
 * @param statusTimestampAfter only tasks whose status changed after this time
 * @param includeArtifacts true to include the artifacts of each task; the default is false
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aListTasksRequest(
    String tenant,
    String contextId,
    String status,
    Integer pageSize,
    String pageToken,
    Integer historyLength,
    Instant statusTimestampAfter,
    Boolean includeArtifacts) {}
