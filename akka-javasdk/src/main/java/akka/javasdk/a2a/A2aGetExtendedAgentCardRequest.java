/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The request of {@code GetExtendedAgentCard}. The result is an {@link A2aAgentCard}.
 *
 * @param tenant the tenant of the agent, when it serves several
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aGetExtendedAgentCardRequest(String tenant) {}
