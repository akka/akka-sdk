/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import java.util.Optional;

/**
 * Describes why a swarm was paused.
 *
 * @param type the category of pause
 * @param message human-readable explanation
 * @param pendingApprovalId identifier for an approval request, if applicable
 */
public record PauseReason(
    Type type,
    String message,
    Optional<String> pendingApprovalId) {

  public enum Type {
    HITL,
    EMERGENCY,
    APPROVAL_NEEDED
  }

  public PauseReason(Type type, String message) {
    this(type, message, Optional.empty());
  }
}
