/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl;

import akka.platform.javasdk.consumer.Consumer;
import akka.platform.javasdk.consumer.ConsumerContext;
import akka.platform.javasdk.impl.consumer.ConsumerRouter;

/**
 * Low level interface to implement {@link Consumer} components.
 *
 * <p>Generally, this should not be needed, instead, a class extending a generated abstract {@link
 * Consumer} should be used.
 */
public interface ConsumerFactory {
  ConsumerRouter<?> create(ConsumerContext context);
}
