/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * The state of a task at one point in time.
 *
 * @param state the state
 * @param message an optional message from the agent that explains the state, for example the
 *     question for an {@link A2aTaskState#INPUT_REQUIRED} task
 * @param timestamp when the task entered this state; the protocol recommends millisecond precision
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record A2aTaskStatus(A2aTaskState state, A2aMessage message, Instant timestamp) {}
