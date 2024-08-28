/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.timedaction;

import com.google.protobuf.Descriptors;
import akka.javasdk.impl.ComponentDescriptor;
import akka.javasdk.impl.JsonMessageCodec;
import akka.javasdk.impl.timedaction.ReflectiveTimedActionRouter;
import akka.javasdk.impl.MessageCodec;
import akka.javasdk.impl.timedaction.TimedActionRouter;

import java.util.Optional;
import java.util.function.Function;

public class ReflectiveTimedActionProvider<A extends TimedAction> implements TimedActionProvider<A> {

  private final Function<TimedActionContext, A> factory;

  private final TimedActionOptions options;
  private final Descriptors.FileDescriptor fileDescriptor;
  private final Descriptors.ServiceDescriptor serviceDescriptor;
  private final ComponentDescriptor componentDescriptor;
  private final JsonMessageCodec messageCodec;

  public static <A extends TimedAction> ReflectiveTimedActionProvider<A> of(
      Class<A> cls,
      JsonMessageCodec messageCodec,
      Function<TimedActionContext, A> factory) {
    return new ReflectiveTimedActionProvider<>(cls, messageCodec, factory, TimedActionOptions.defaults());
  }

  private ReflectiveTimedActionProvider(
      Class<A> cls,
      JsonMessageCodec messageCodec,
      Function<TimedActionContext, A> factory,
      TimedActionOptions options) {

    this.factory = factory;
    this.options = options;
    this.messageCodec = messageCodec;

    this.componentDescriptor = ComponentDescriptor.descriptorFor(cls, messageCodec);

    this.fileDescriptor = componentDescriptor.fileDescriptor();
    this.serviceDescriptor = componentDescriptor.serviceDescriptor();
  }

  @Override
  public TimedActionOptions options() {
    return options;
  }

  @Override
  public Descriptors.ServiceDescriptor serviceDescriptor() {
    return serviceDescriptor;
  }

  @Override
  public TimedActionRouter<A> newRouter(TimedActionContext context) {
    A action = factory.apply(context);
    return new ReflectiveTimedActionRouter<>(action, componentDescriptor.commandHandlers());
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
