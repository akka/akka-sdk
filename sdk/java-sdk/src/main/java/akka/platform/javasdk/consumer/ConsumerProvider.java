/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.consumer;

import akka.platform.javasdk.Kalix;
import akka.platform.javasdk.impl.MessageCodec;
import akka.platform.javasdk.impl.consumer.ConsumerRouter;
import com.google.protobuf.Descriptors;

import java.util.Optional;

/**
 * Register an Consumer in {{@link Kalix}} using an <code>ConsumerProvider</code>.
 */
public interface ConsumerProvider<A extends Consumer> {

  ConsumerOptions options();

  Descriptors.ServiceDescriptor serviceDescriptor();

  ConsumerRouter<A> newRouter(ConsumerContext context);

  Descriptors.FileDescriptor[] additionalDescriptors();

  default Optional<MessageCodec> alternativeCodec() {
    return Optional.empty();
  }
}
