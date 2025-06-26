/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;

@ComponentId("stage-counter")
public class StageCounterEntity extends KeyValueEntity<Integer> {
  private final String entityId;

  public StageCounterEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public Integer emptyState() {
    return 100;
  }

  public Effect<Integer> get() {
    return effects().reply(currentState());
  }
}
