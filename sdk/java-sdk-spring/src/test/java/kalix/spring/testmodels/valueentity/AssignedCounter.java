/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.testmodels.valueentity;

import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.spring.testmodels.Done;

@TypeId("assigned-counter")
public class AssignedCounter extends ValueEntity<AssignedCounterState> {

  @Override
  public AssignedCounterState emptyState() {
    return new AssignedCounterState(commandContext().entityId(), "");
  }

  public ValueEntity.Effect<Done> assign(String assigneeId) {
    AssignedCounterState newState = currentState().assignTo(assigneeId);
    return effects().updateState(newState).thenReply(Done.instance);
  }
}
