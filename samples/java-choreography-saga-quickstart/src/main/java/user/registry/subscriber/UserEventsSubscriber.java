package user.registry.subscriber;


import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import user.registry.common.Done;
import user.registry.domain.UserEvent;
import user.registry.domain.UserEvent.EmailAssigned;
import user.registry.domain.UserEvent.EmailUnassigned;
import user.registry.domain.UserEvent.UserWasCreated;
import user.registry.entity.UniqueEmailEntity;
import user.registry.entity.UserEntity;

/**
 * This Action plays the role of a subscriber to the UserEntity.
 * <p>
 * In the choreography, this subscriber will react to events (facts) produced by the UserEntity and modify the
 * UniqueEmailEntity accordingly. Either by confirming or un-reserving the email address.
 */
@Consume.FromEventSourcedEntity(value = UserEntity.class)
public class UserEventsSubscriber extends Action {

  private final ComponentClient client;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public UserEventsSubscriber(ComponentClient client) {
    this.client = client;
  }

  public Effect<Done> onEvent(UserEvent evt) {
    return switch (evt) {
      case UserWasCreated created -> confirmEmail(created.email());
      case EmailAssigned assigned -> confirmEmail(assigned.newEmail());
      case EmailUnassigned unassigned -> markAsNotUsed(evt, unassigned);
    };

  }

  /**
   * When a user stops to use an email address, this method gets called and un-reserves the email address.
   */
  private Effect<Done> markAsNotUsed(UserEvent evt, EmailUnassigned unassigned) {
    logger.info("Old email address unassigned: {}, deleting unique email address record", evt);
    var unreserved =
      client.forValueEntity(unassigned.oldEmail())
        .method(UniqueEmailEntity::markAsNotUsed)
        .invokeAsync();

    return effects().asyncReply(unreserved);
  }

  /**
   * This method is called and a user is created or when a new email address is assigned to a user.
   * It will hit the UniqueEmailEntity to confirm the email address.
   */
  private Effect<Done> confirmEmail(String emailAddress) {
    logger.info("User got a new email address assigned: {}, confirming new address address", emailAddress);
    var confirmation =
      client.forValueEntity(emailAddress)
        .method(UniqueEmailEntity::confirm)
        .invokeAsync();

    return effects().asyncReply(confirmation);
  }
}
