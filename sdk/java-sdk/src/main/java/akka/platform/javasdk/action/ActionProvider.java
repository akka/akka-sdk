/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.action;

import com.google.protobuf.Descriptors;
import akka.platform.javasdk.Kalix;
import akka.platform.javasdk.impl.MessageCodec;
import akka.platform.javasdk.impl.action.ActionRouter;

import java.util.Optional;

/**
 * Register an Action in {{@link Kalix}} using an <code>
 * ActionProvider</code>. The concrete <code>ActionProvider</code> is generated for the specific
 * entities defined in Protobuf, for example <code>CustomerActionProvider</code>.
 */
public interface ActionProvider<A extends Action> {

  ActionOptions options();

  Descriptors.ServiceDescriptor serviceDescriptor();

  ActionRouter<A> newRouter(ActionCreationContext context);

  Descriptors.FileDescriptor[] additionalDescriptors();

  default Optional<MessageCodec> alternativeCodec() {
    return Optional.empty();
  }
}
