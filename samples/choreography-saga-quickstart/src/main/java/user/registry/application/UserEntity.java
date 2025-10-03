package user.registry.application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.domain.User;
import user.registry.domain.UserEvent;

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
@Component(id = "user")
public class UserEntity extends EventSourcedEntity<User, UserEvent> {

  public record Create(String name, String country, String email) {}

  public record ChangeEmail(String newEmail) {}

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
  @JsonSubTypes(
    {
      @JsonSubTypes.Type(value = Result.Success.class, name = "Success"),
      @JsonSubTypes.Type(value = Result.InvalidCommand.class, name = "InvalidCommand"),
    }
  )
  public sealed interface Result {
    record InvalidCommand(String msg) implements Result {}

    record Success() implements Result {}
  }

  public Effect<Result> createUser(Create cmd) {
    // since the user creation depends on the email address reservation, a better place to valid an incoming command
    // would be in the ApplicationController where we coordinate the two operations.
    // However, to demonstrate a failure case, we validate the command here.
    // As such, we can simulate the situation where an email address is reserved, but we fail to create the user.
    // When that happens the timer defined by the UniqueEmailSubscriber will fire and cancel the email address reservation.
    if (cmd.name() == null) {
      return effects().reply(new Result.InvalidCommand("Name is empty"));
    }

    if (currentState() != null) {
      return effects().reply(new Result.Success());
    }

    logger.info("Creating user {}", cmd);
    return effects()
      .persist(new UserEvent.UserWasCreated(cmd.name, cmd.country, cmd.email))
      .thenReply(__ -> new Result.Success());
  }

  /**
   * Persists EmailAssigned and EmailUnassigned event.
   * Persists nothing if 'changing' to the same email address.
   * <p>
   * When changing the email address, we need to persist two events:
   * one to assign the new email address and one to un-assign the old email address.
   * <p>
   * Later the UserEventsSubscriber will react to these events and update the UniqueEmailEntity accordingly.
   * The newly assigned email will be confirmed and the old email will be marked as not-in-use.
   */
  public Effect<Done> changeEmail(ChangeEmail cmd) {
    if (currentState() == null) {
      return effects().error("User not found");
    }

    if (cmd.newEmail().equals(currentState().email())) {
      return effects().reply(done());
    } else {
      var events = List.of(
        new UserEvent.EmailAssigned(cmd.newEmail),
        new UserEvent.EmailUnassigned(currentState().email())
      );

      return effects().persistAll(events).thenReply(__ -> done());
    }
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
      case UserEvent.UserWasCreated evt -> new User(evt.name(), evt.country(), evt.email());
      case UserEvent.EmailAssigned evt -> currentState().withEmail(evt.newEmail());
      case UserEvent.EmailUnassigned evt -> currentState();
    };
  }
}
