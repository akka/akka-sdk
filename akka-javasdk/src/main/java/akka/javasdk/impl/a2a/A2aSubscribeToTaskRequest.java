/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * INTERNAL API
 *
 * <p>The request of {@code SubscribeToTask}.
 *
 * @param tenant the tenant of the agent, when it serves several
 * @param id the id of the task
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aSubscribeToTaskRequest(String tenant, String id) {}
