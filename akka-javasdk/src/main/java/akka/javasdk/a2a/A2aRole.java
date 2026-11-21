/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The sender of an {@link A2aMessage}. */
public enum A2aRole {
  /** Not set, or a value that this version of the protocol does not know. */
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

  /**
   * The A2aRole with the given protocol name. A name that this version does not know, such as a
   * role added by a later protocol version, is {@link #UNSPECIFIED}.
   */
  @JsonCreator
  public static A2aRole fromWireName(String wireName) {
    for (A2aRole value : values()) {
      if (value.wireName.equals(wireName)) return value;
    }
    return UNSPECIFIED;
  }
}
