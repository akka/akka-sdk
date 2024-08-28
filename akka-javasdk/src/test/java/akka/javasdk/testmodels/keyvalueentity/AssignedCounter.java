/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testmodels.Done;

@ComponentId("assigned-counter")
public class AssignedCounter extends KeyValueEntity<AssignedCounterState> {

  @Override
  public AssignedCounterState emptyState() {
    return new AssignedCounterState(commandContext().entityId(), "");
  }

  public KeyValueEntity.Effect<Done> assign(String assigneeId) {
    AssignedCounterState newState = currentState().assignTo(assigneeId);
    return effects().updateState(newState).thenReply(Done.instance);
  }
}
