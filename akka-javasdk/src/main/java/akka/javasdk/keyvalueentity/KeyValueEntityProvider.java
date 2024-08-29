/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.keyvalueentity;

import akka.javasdk.impl.Kalix;
import akka.javasdk.impl.MessageCodec;
import com.google.protobuf.Descriptors;
import akka.javasdk.impl.keyvalueentity.KeyValueEntityRouter;

import java.util.Optional;

/**
 * Register a value based entity in {@link Kalix} using a <code>
 * KeyValueEntityProvider</code>. The concrete <code>KeyValueEntityProvider</code> is generated for the
 * specific entities defined in Protobuf, for example <code>CustomerEntityProvider</code>.
 */
public interface KeyValueEntityProvider<S, E extends KeyValueEntity<S>> {

  KeyValueEntityOptions options();

  Descriptors.ServiceDescriptor serviceDescriptor();

  String typeId();

  KeyValueEntityRouter<S, E> newRouter(KeyValueEntityContext context);

  Descriptors.FileDescriptor[] additionalDescriptors();

  default Optional<MessageCodec> alternativeCodec() {
    return Optional.empty();
  }
}
