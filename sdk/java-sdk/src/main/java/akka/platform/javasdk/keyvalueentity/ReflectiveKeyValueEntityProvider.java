/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.keyvalueentity;

import com.google.protobuf.Descriptors;
import akka.platform.javasdk.common.ForwardHeadersExtractor;
import akka.platform.javasdk.impl.ComponentDescriptor;
import akka.platform.javasdk.impl.ComponentDescriptorFactory$;
import akka.platform.javasdk.impl.JsonMessageCodec;
import akka.platform.javasdk.impl.MessageCodec;
import akka.platform.javasdk.impl.keyvalueentity.ReflectiveKeyValueEntityRouter;
import akka.platform.javasdk.impl.keyvalueentity.KeyValueEntityRouter;

import java.util.Optional;
import java.util.function.Function;

public class ReflectiveKeyValueEntityProvider<S, E extends KeyValueEntity<S>>
    implements KeyValueEntityProvider<S, E> {

  private final String typeId;
  private final Function<KeyValueEntityContext, E> factory;
  private final KeyValueEntityOptions options;
  private final Descriptors.FileDescriptor fileDescriptor;
  private final Descriptors.ServiceDescriptor serviceDescriptor;
  private final ComponentDescriptor componentDescriptor;
  private final JsonMessageCodec messageCodec;

  public static <S, E extends KeyValueEntity<S>> ReflectiveKeyValueEntityProvider<S, E> of(
      Class<E> cls, JsonMessageCodec messageCodec, Function<KeyValueEntityContext, E> factory) {
    return new ReflectiveKeyValueEntityProvider<>(
        cls, messageCodec, factory, KeyValueEntityOptions.defaults());
  }

  public ReflectiveKeyValueEntityProvider(
      Class<E> entityClass,
      JsonMessageCodec messageCodec,
      Function<KeyValueEntityContext, E> factory,
      KeyValueEntityOptions options) {

    String annotation = ComponentDescriptorFactory$.MODULE$.readComponentIdIdValue(entityClass);
    if (annotation == null)
      throw new IllegalArgumentException(
        "Key Value Entity [" + entityClass.getName() + "] is missing '@ComponentId' annotation");

    this.typeId = annotation;

    this.factory = factory;
    this.options = options.withForwardHeaders(ForwardHeadersExtractor.extractFrom(entityClass));
    this.messageCodec = messageCodec;

    this.componentDescriptor = ComponentDescriptor.descriptorFor(entityClass, messageCodec);

    this.fileDescriptor = componentDescriptor.fileDescriptor();
    this.serviceDescriptor = componentDescriptor.serviceDescriptor();
  }

  @Override
  public KeyValueEntityOptions options() {
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
  public KeyValueEntityRouter<S, E> newRouter(KeyValueEntityContext context) {
    E entity = factory.apply(context);
    return new ReflectiveKeyValueEntityRouter<>(entity, componentDescriptor.commandHandlers());
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
