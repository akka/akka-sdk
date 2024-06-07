/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.view.View;
import kalix.javasdk.workflow.Workflow;
import kalix.spring.testmodels.Message;
import kalix.spring.testmodels.valueentity.User;
import kalix.spring.testmodels.valueentity.UserEntity;
import kalix.spring.testmodels.workflow.StartWorkflow;
import kalix.spring.testmodels.workflow.WorkflowState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// below components are not public and thus need to be in the same package as the corresponding test
public class NotPublicComponents {
  static class NotPublicAction extends Action {
    @GetMapping("/message")
    public Action.Effect<Message> message() {
      return effects().ignore();
    }
  }

  @TypeId("counter")
  static class NotPublicEventSourced extends EventSourcedEntity<Integer, NotPublicEventSourced.Event> {

    public sealed interface Event {
      public record Created()implements Event {};
    }

    public Integer test() {
      return 0;
    }

    @Override
    public Integer applyEvent(Event event) {
      return 0;
    }
  }

  @TypeId("user")
  static class NotPublicValueEntity extends ValueEntity<User> {
    public ValueEntity.Effect<String> ok() {
      return effects().reply("ok");
    }
  }

  @ViewId("users_view")
  @Table(value = "users_view")
  @Subscribe.ValueEntity(UserEntity.class)
  static class NotPublicView extends View<User> {

    public record QueryParameters(String email) {}

    @Query("SELECT * FROM users_view WHERE email = :email")
    public User getUser(QueryParameters email) {
      return null;
    }
  }

  @TypeId("transfer-workflow")
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


