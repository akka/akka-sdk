/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.javasdk.consumer.Consumer;
import akka.javasdk.consumer.ConsumerContext;
import akka.javasdk.impl.consumer.ConsumerRouter;

/**
 * Low level interface to implement {@link Consumer} components.
 *
 * <p>Generally, this should not be needed, instead, a class extending a generated abstract {@link
 * Consumer} should be used.
 */
public interface ConsumerFactory {
  ConsumerRouter<?> create(ConsumerContext context);
}
