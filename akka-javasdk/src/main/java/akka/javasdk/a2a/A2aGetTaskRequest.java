/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The request of {@code GetTask}.
 *
 * @param tenant the tenant of the agent, when it serves several
 * @param id the id of the task
 * @param historyLength how many of the latest messages of the task history to return; zero for
 *     none, unset for the agent's default
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aGetTaskRequest(String tenant, String id, Integer historyLength) {}
