/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonValue;

/** The lifecycle state of an {@link A2aTask}. */
public enum A2aTaskState {
  /** Not set. */
  UNSPECIFIED("TASK_STATE_UNSPECIFIED"),
  /** The agent accepted the task and has not started it. */
  SUBMITTED("TASK_STATE_SUBMITTED"),
  /** The agent is working on the task. */
  WORKING("TASK_STATE_WORKING"),
  /** The task finished successfully. Terminal. */
  COMPLETED("TASK_STATE_COMPLETED"),
  /** The task finished with an error. Terminal. */
  FAILED("TASK_STATE_FAILED"),
  /** The task was canceled before it finished. Terminal. */
  CANCELED("TASK_STATE_CANCELED"),
  /** The agent needs more input from the client to continue. Interrupted. */
  INPUT_REQUIRED("TASK_STATE_INPUT_REQUIRED"),
  /** The agent refused the task. Terminal. */
  REJECTED("TASK_STATE_REJECTED"),
  /** The agent needs the client to authenticate to continue. Interrupted. */
  AUTH_REQUIRED("TASK_STATE_AUTH_REQUIRED");

  private final String wireName;

  A2aTaskState(String wireName) {
    this.wireName = wireName;
  }

  /** The name used in the protocol's JSON, for example {@code TASK_STATE_COMPLETED}. */
  @JsonValue
  public String wireName() {
    return wireName;
  }

  /** True when the task can no longer change: completed, failed, canceled or rejected. */
  public boolean terminal() {
    return this == COMPLETED || this == FAILED || this == CANCELED || this == REJECTED;
  }
}
