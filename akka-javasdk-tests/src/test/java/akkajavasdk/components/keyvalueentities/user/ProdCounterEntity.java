/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;

@ComponentId("prod-counter")
public class ProdCounterEntity extends KeyValueEntity<Integer> {
  private final String entityId;

  public ProdCounterEntity(KeyValueEntityContext context) {
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
