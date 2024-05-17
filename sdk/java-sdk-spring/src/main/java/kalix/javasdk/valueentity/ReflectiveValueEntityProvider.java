/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.valueentity;

import com.google.protobuf.Descriptors;
import kalix.javasdk.common.ForwardHeadersExtractor;
import kalix.javasdk.impl.ComponentDescriptor;
import kalix.javasdk.impl.ComponentDescriptorFactory$;
import kalix.javasdk.impl.JsonMessageCodec;
import kalix.javasdk.impl.MessageCodec;
import kalix.javasdk.impl.valueentity.ReflectiveValueEntityRouter;
import kalix.javasdk.impl.valueentity.ValueEntityRouter;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.valueentity.ValueEntityContext;
import kalix.javasdk.valueentity.ValueEntityOptions;
import kalix.javasdk.valueentity.ValueEntityProvider;

import java.util.Optional;
import java.util.function.Function;

public class ReflectiveValueEntityProvider<S, E extends ValueEntity<S>>
    implements ValueEntityProvider<S, E> {

  private final String typeId;
  private final Function<ValueEntityContext, E> factory;
  private final ValueEntityOptions options;
  private final Descriptors.FileDescriptor fileDescriptor;
  private final Descriptors.ServiceDescriptor serviceDescriptor;
  private final ComponentDescriptor componentDescriptor;
  private final JsonMessageCodec messageCodec;

  public static <S, E extends ValueEntity<S>> ReflectiveValueEntityProvider<S, E> of(
      Class<E> cls, JsonMessageCodec messageCodec, Function<ValueEntityContext, E> factory) {
    return new ReflectiveValueEntityProvider<>(
        cls, messageCodec, factory, ValueEntityOptions.defaults());
  }

  public ReflectiveValueEntityProvider(
      Class<E> entityClass,
      JsonMessageCodec messageCodec,
      Function<ValueEntityContext, E> factory,
      ValueEntityOptions options) {

    String annotation = ComponentDescriptorFactory$.MODULE$.readTypeIdValue(entityClass);
    if (annotation == null)
      throw new IllegalArgumentException(
          "Value Entity [" + entityClass.getName() + "] is missing '@TypeId' annotation");

    this.typeId = annotation;

    this.factory = factory;
    this.options = options.withForwardHeaders(ForwardHeadersExtractor.extractFrom(entityClass));
    this.messageCodec = messageCodec;

    this.componentDescriptor = ComponentDescriptor.descriptorFor(entityClass, messageCodec);

    this.fileDescriptor = componentDescriptor.fileDescriptor();
    this.serviceDescriptor = componentDescriptor.serviceDescriptor();
  }

  @Override
  public ValueEntityOptions options() {
    return options;
  }

  @Override
  public Descriptors.ServiceDescriptor serviceDescriptor() {
    return serviceDescriptor;
  }

  @Override
  public String typeId() {
    return typeId;
  }

  @Override
  public ValueEntityRouter<S, E> newRouter(ValueEntityContext context) {
    E entity = factory.apply(context);
    return new ReflectiveValueEntityRouter<>(entity, componentDescriptor.commandHandlers());
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
