package com.example.tracing.api;

import com.example.tracing.domain.User;
import com.example.tracing.domain.UserEvent;
import kalix.javasdk.StatusCode;
import kalix.javasdk.annotations.ForwardHeaders;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TypeId("user")
@ForwardHeaders("traceparent")
public class UserEntity extends EventSourcedEntity<User, UserEvent> {

  private static final Logger log = LoggerFactory.getLogger(UserEntity.class);

  private final String entityId;

  public sealed interface UserCmd {
    record CreateCmd(String email) implements UserCmd {
    }

    record UpdateNameCmd(String name) implements UserCmd {
    }

    record UpdatePhotoCmd(String url) implements UserCmd {
    }
  }


  public UserEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public User emptyState() { // <2>
    return new User(entityId, "", "", "");
  }

  public Effect<User> get() {
    if (currentState().equals(emptyState()))
      return effects().error("User does not exist");

    return effects().reply(currentState());
  }

  public Effect<String> add(UserCmd.CreateCmd create) {
    log.info("Current context: {}, empty {}", currentState(), emptyState());
    if (!currentState().equals(emptyState())) {
      return effects().error("User already exists");
    }

    var created = new UserEvent.UserAdded(create.email);

    return effects()
      .persist(created)
      .thenReply(newState -> "OK");
  }

  public Effect<String> updateName(UserCmd.UpdateNameCmd updateNameCmd) {
    if (currentState().equals(emptyState())) {
      return effects().error("User does not exist");
    }

    var updated = new UserEvent.UserNameUpdated(updateNameCmd.name);

    return effects()
      .persist(updated)
      .thenReply(newState -> "OK");
  }

  public Effect<String> updatePhoto(UserCmd.UpdatePhotoCmd updatePhotoCmd) {
    if (currentState().equals(emptyState())) {
      return effects().error("User does not exist");
    }

    var updated = new UserEvent.UserPhotoUpdated(updatePhotoCmd.url);
    return effects()
      .persist(updated)
      .thenReply(newState -> "OK");
  }

  @Override
  public User applyEvent(UserEvent event) {
    return switch (event) {
      case UserEvent.UserAdded userAdded -> {
        log.info("User added: {}", userAdded.email());
        yield new User(entityId, "", userAdded.email(), "");
      }
      case UserEvent.UserNameUpdated nameUpdated -> {
        log.info("User name updated: {}", nameUpdated.name());
        yield currentState().withName(nameUpdated.name());
      }
      case UserEvent.UserPhotoUpdated photoUpdated -> {
        log.info("User photo updated: {}", photoUpdated.url());
        yield currentState().withPhoto(photoUpdated.url());
      }
    };
  }

}
