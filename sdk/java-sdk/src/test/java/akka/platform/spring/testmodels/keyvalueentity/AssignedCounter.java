/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.keyvalueentity;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;
import akka.platform.spring.testmodels.Done;

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
