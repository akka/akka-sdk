/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.action;

import com.google.protobuf.Descriptors;
import akka.platform.javasdk.common.ForwardHeadersExtractor;
import akka.platform.javasdk.impl.ComponentDescriptor;
import akka.platform.javasdk.impl.ComponentDescriptorFactory;
import akka.platform.javasdk.impl.JsonMessageCodec;
import akka.platform.javasdk.impl.action.ReflectiveActionRouter;
import akka.platform.javasdk.impl.MessageCodec;
import akka.platform.javasdk.impl.action.ActionRouter;

import java.util.Optional;
import java.util.function.Function;

public class ReflectiveActionProvider<A extends Action> implements ActionProvider<A> {

  private final Function<ActionContext, A> factory;

  private final ActionOptions options;
  private final Descriptors.FileDescriptor fileDescriptor;
  private final Descriptors.ServiceDescriptor serviceDescriptor;
  private final ComponentDescriptor componentDescriptor;
  private final JsonMessageCodec messageCodec;

  public static <A extends Action> ReflectiveActionProvider<A> of(
      Class<A> cls,
      JsonMessageCodec messageCodec,
      Function<ActionContext, A> factory) {
    return new ReflectiveActionProvider<>(cls, messageCodec, factory, ActionOptions.defaults());
  }

  private ReflectiveActionProvider(
      Class<A> cls,
      JsonMessageCodec messageCodec,
      Function<ActionContext, A> factory,
      ActionOptions options) {

    this.factory = factory;
    this.options = options.withForwardHeaders(ForwardHeadersExtractor.extractFrom(cls));
    this.messageCodec = messageCodec;

    this.componentDescriptor = ComponentDescriptor.descriptorFor(cls, messageCodec);

    this.fileDescriptor = componentDescriptor.fileDescriptor();
    this.serviceDescriptor = componentDescriptor.serviceDescriptor();
  }

  @Override
  public ActionOptions options() {
    return options;
  }

  @Override
  public Descriptors.ServiceDescriptor serviceDescriptor() {
    return serviceDescriptor;
  }

  @Override
  public ActionRouter<A> newRouter(ActionContext context) {
    A action = factory.apply(context);
    return new ReflectiveActionRouter<>(action, componentDescriptor.commandHandlers(), ComponentDescriptorFactory.findIgnore(action.getClass()));
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
