package user.registry.application;


import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.domain.UserEvent;
import user.registry.domain.UserEvent.EmailAssigned;
import user.registry.domain.UserEvent.EmailUnassigned;
import user.registry.domain.UserEvent.UserWasCreated;

/**
 * This Consumer consumes from the UserEntity.
 * <p>
 * In the choreography, this consumer will react to events (facts) produced by the UserEntity and modify the
 * UniqueEmailEntity accordingly. Either by confirming or un-reserving the email address.
 */
@ComponentId("user-events-subscriber")
@Consume.FromEventSourcedEntity(value = UserEntity.class)
public class UserEventsConsumer extends Consumer {

  private final ComponentClient client;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public UserEventsConsumer(ComponentClient client) {
    this.client = client;
  }

  public Effect onEvent(UserEvent evt) {
    return switch (evt) {
      case UserWasCreated created -> confirmEmail(created.email());
      case EmailAssigned assigned -> confirmEmail(assigned.newEmail());
      case EmailUnassigned unassigned -> markAsNotUsed(evt, unassigned);
    };

  }

  /**
   * When a user stops to use an email address, this method gets called and un-reserves the email address.
   */
  private Effect markAsNotUsed(UserEvent evt, EmailUnassigned unassigned) {
    logger.info("Old email address unassigned: {}, deleting unique email address record", evt);
    var unreserved =
      client.forKeyValueEntity(unassigned.oldEmail())
        .method(UniqueEmailEntity::markAsNotUsed)
        .invokeAsync();

    return effects().acyncDone(unreserved);
  }

  /**
   * This method is called and a user is created or when a new email address is assigned to a user.
   * It will hit the UniqueEmailEntity to confirm the email address.
   */
  private Effect confirmEmail(String emailAddress) {
    logger.info("User got a new email address assigned: {}, confirming new address address", emailAddress);
    var confirmation =
      client.forKeyValueEntity(emailAddress)
        .method(UniqueEmailEntity::confirm)
        .invokeAsync();

    return effects().acyncDone(confirmation);
  }
}
