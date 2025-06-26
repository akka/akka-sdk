/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;

@ComponentId("assigned-counter")
public class AssignedCounterEntity extends KeyValueEntity<AssignedCounter> {
  private final String entityId;

  public AssignedCounterEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public AssignedCounter emptyState() {
    return new AssignedCounter(entityId, "");
  }

  public KeyValueEntity.Effect<String> assign(String assigneeId) {
    AssignedCounter newState = currentState().assignTo(assigneeId);
    return effects().updateState(newState).thenReply("OK");
  }
}
