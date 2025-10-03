/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testmodels.Done;

@Component(id = "assigned-counter")
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
