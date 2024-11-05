/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;

@ComponentId("assigned-counter")
public class AssignedCounterEntity extends KeyValueEntity<AssignedCounter> {

  @Override
  public AssignedCounter emptyState() {
    return new AssignedCounter(commandContext().entityId(), "");
  }

  public KeyValueEntity.Effect<String> assign(String assigneeId) {
    AssignedCounter newState = currentState().assignTo(assigneeId);
    return effects().updateState(newState).thenReply("OK");
  }
}
