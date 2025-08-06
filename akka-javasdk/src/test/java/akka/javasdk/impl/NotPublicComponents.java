/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testmodels.keyvalueentity.User;
import akka.javasdk.testmodels.workflow.StartWorkflow;
import akka.javasdk.testmodels.workflow.WorkflowState;
import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.workflow.Workflow;

// below components are not public and thus need to be in the same package as the corresponding test
public class NotPublicComponents {

  @ComponentId("not-public")
  static class NotPublicAction extends TimedAction {
    public Effect message() {
      return effects().done();
    }
  }

  @ComponentId("not-public")
  static class NotPublicConsumer extends Consumer {
    public Effect message() {
      return effects().ignore();
    }
  }

  @ComponentId("counter")
  static class NotPublicEventSourced
      extends EventSourcedEntity<Integer, NotPublicEventSourced.Event> {

    public sealed interface Event {
      public record Created() implements Event {}
      ;
    }

    public Integer test() {
      return 0;
    }

    @Override
    public Integer applyEvent(Event event) {
      return 0;
    }
  }

  @ComponentId("user")
  static class NotPublicValueEntity extends KeyValueEntity<User> {
    public KeyValueEntity.Effect<String> ok() {
      return effects().reply("ok");
    }
  }

  @ComponentId("transfer-workflow")
  static class NotPublicWorkflow extends Workflow<WorkflowState> {
    @Override
    public WorkflowDef<WorkflowState> definition() {
      return null;
    }

    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }
  }
}
