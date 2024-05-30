/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.valueentities.user;

import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;

@TypeId("assigned-counter")
public class AssignedCounterEntity extends ValueEntity<AssignedCounter> {

  @Override
  public AssignedCounter emptyState() {
    return new AssignedCounter(commandContext().entityId(), "");
  }

  public ValueEntity.Effect<String> assign(String assigneeId) {
    AssignedCounter newState = currentState().assignTo(assigneeId);
    return effects().updateState(newState).thenReply("OK");
  }
}
