/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonValue;

/** The sender of an {@link A2aMessage}. */
public enum A2aRole {
  /** Not set. */
  UNSPECIFIED("ROLE_UNSPECIFIED"),
  /** The message comes from the client. */
  USER("ROLE_USER"),
  /** The message comes from the agent. */
  AGENT("ROLE_AGENT");

  private final String wireName;

  A2aRole(String wireName) {
    this.wireName = wireName;
  }

  /** The name used in the protocol's JSON. */
  @JsonValue
  public String wireName() {
    return wireName;
  }
}
