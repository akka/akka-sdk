/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.workflow;

import com.google.protobuf.Descriptors;
import akka.platform.javasdk.impl.MessageCodec;
import akka.platform.javasdk.impl.workflow.WorkflowRouter;

import java.util.Optional;

public interface WorkflowProvider<S, W extends AbstractWorkflow<S>> {

  String typeId();

  WorkflowOptions options();

  Descriptors.ServiceDescriptor serviceDescriptor();

  WorkflowRouter<S, W> newRouter(WorkflowContext context);

  Descriptors.FileDescriptor[] additionalDescriptors();

  default Optional<MessageCodec> alternativeCodec() {
    return Optional.empty();
  }

}
