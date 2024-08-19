/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.consumer;

import akka.platform.javasdk.common.ForwardHeadersExtractor;
import akka.platform.javasdk.impl.ComponentDescriptor;
import akka.platform.javasdk.impl.ComponentDescriptorFactory;
import akka.platform.javasdk.impl.JsonMessageCodec;
import akka.platform.javasdk.impl.MessageCodec;
import akka.platform.javasdk.impl.consumer.ConsumerRouter;
import akka.platform.javasdk.impl.consumer.ReflectiveConsumerRouter;
import com.google.protobuf.Descriptors;

import java.util.Optional;
import java.util.function.Function;

public class ReflectiveConsumerProvider<A extends Consumer> implements ConsumerProvider<A> {

  private final Function<ConsumerContext, A> factory;

  private final ConsumerOptions options;
  private final Descriptors.FileDescriptor fileDescriptor;
  private final Descriptors.ServiceDescriptor serviceDescriptor;
  private final ComponentDescriptor componentDescriptor;
  private final JsonMessageCodec messageCodec;

  public static <A extends Consumer> ReflectiveConsumerProvider<A> of(
      Class<A> cls,
      JsonMessageCodec messageCodec,
      Function<ConsumerContext, A> factory) {
    return new ReflectiveConsumerProvider<>(cls, messageCodec, factory, ConsumerOptions.defaults());
  }

  private ReflectiveConsumerProvider(
      Class<A> cls,
      JsonMessageCodec messageCodec,
      Function<ConsumerContext, A> factory,
      ConsumerOptions options) {

    this.factory = factory;
    this.options = options;
    this.messageCodec = messageCodec;

    this.componentDescriptor = ComponentDescriptor.descriptorFor(cls, messageCodec);

    this.fileDescriptor = componentDescriptor.fileDescriptor();
    this.serviceDescriptor = componentDescriptor.serviceDescriptor();
  }

  @Override
  public ConsumerOptions options() {
    return options;
  }

  @Override
  public Descriptors.ServiceDescriptor serviceDescriptor() {
    return serviceDescriptor;
  }

  @Override
  public ConsumerRouter<A> newRouter(ConsumerContext context) {
    A consumer = factory.apply(context);
    return new ReflectiveConsumerRouter<>(consumer, componentDescriptor.commandHandlers(), ComponentDescriptorFactory.findIgnore(consumer.getClass()));
  }

  @Override
  public Descriptors.FileDescriptor[] additionalDescriptors() {
    return new Descriptors.FileDescriptor[] {fileDescriptor};
  }

  @Override
  public Optional<MessageCodec> alternativeCodec() {
    return Optional.of(messageCodec);
  }
}
