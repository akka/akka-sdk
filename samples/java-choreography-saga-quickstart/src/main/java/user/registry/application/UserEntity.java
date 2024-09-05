package user.registry.application;


import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.domain.User;
import user.registry.domain.UserEvent;

import static akka.Done.done;

/**
 * Entity wrapping a User.
 * <p>
 * The UserEntity is part of the application layer. It implements the glue between the domain layer (user) and Akka.
 * Incoming commands are delivered to the UserEntity, which passes them to the domain layer.
 * The domain layer returns the events that need to be persisted. The entity wraps them in an {@link Effect} that describes
 * to Akka what needs to be done, e.g.: emit events, reply to the caller, etc.
 * <p>
 * A User has a name, a country and an email address.
 * The email address must be unique across all existing users. This is achieved with a choreography saga which ensures that
 * a user is only created if the email address is not already reserved.
 * <p>
 * This entity is protected from outside access. It can only be accessed from within this service (see the ACL annotation).
 * External access is gated and should go through the ApplicationController.
 */
@ComponentId("user")
@Acl(allow = @Acl.Matcher(service = "*"))
public class UserEntity extends EventSourcedEntity<User, UserEvent> {

  private final Logger logger = LoggerFactory.getLogger(getClass());


  public Effect<Done> createUser(User.Create cmd) {

    // since the user creation depends on the email address reservation, a better place to valid an incoming command
    // would be in the ApplicationController where we coordinate the two operations.
    // However, to demonstrate a failure case, we validate the command here.
    // As such, we can simulate the situation where an email address is reserved, but we fail to create the user.
    // When that happens the timer defined by the UniqueEmailSubscriber will fire and cancel the email address reservation.
    if (cmd.name() == null) {
      return effects().error("Name is empty");
    }

    if (currentState() != null) {
      return effects().reply(done());
    }

    logger.info("Creating user {}", cmd);
    return effects()
      .persist(User.onCommand(cmd))
      .thenReply(__ -> done());
  }

  public Effect<Done> changeEmail(User.ChangeEmail cmd) {
    if (currentState() == null) {
      return effects().error("User not found");
    }
    return effects()
      .persistAll(currentState().onCommand(cmd))
      .thenReply(__ -> done());
  }

  public ReadOnlyEffect<User> getState() {
    if (currentState() == null) {
      return effects().error("User not found");
    }
    return effects().reply(currentState());
  }


  @Override
  public User applyEvent(UserEvent event) {
    return switch (event) {
      case UserEvent.UserWasCreated evt -> User.onEvent(evt);
      case UserEvent.EmailAssigned evt -> currentState().onEvent(evt);
      case UserEvent.EmailUnassigned evt -> currentState();
    };
  }
}
