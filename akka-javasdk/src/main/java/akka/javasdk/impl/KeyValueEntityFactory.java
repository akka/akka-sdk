/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.annotation.InternalApi;
import akka.javasdk.impl.keyvalueentity.KeyValueEntityRouter;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import akka.javasdk.keyvalueentity.KeyValueEntity;

/**
 * INTERNAL API
 *
 * <p>Low level interface for handling commands on a value based entity.
 *
 * <p>Generally, this should not be needed, instead, a class extending a generated abstract {@link
 * KeyValueEntity} should be used.
 *
 * @hidden
 */
@InternalApi
public interface KeyValueEntityFactory {
  /**
   * Create an entity handler for the given context.
   *
   * @param context The context.
   * @return The handler for the given context.
   */
  KeyValueEntityRouter<?, ?> create(KeyValueEntityContext context);
}
