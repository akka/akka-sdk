package shoppingcart.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import shoppingcart.domain.UserEvent;
import shoppingcart.domain.UserState;

@ComponentId("user")
public class UserEntity extends EventSourcedEntity<UserState, UserEvent> {
  private final String entityId;

  private static final Logger logger = LoggerFactory.getLogger(UserEntity.class);

  public UserEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  public ReadOnlyEffect<String> currentCartId() {
    return effects().reply(currentState().currentCartId());
  }

  // We don't create a random cart ID in this method because we always
  // want command handlers to be deterministic
  public Effect<Done> closeCart(String newCartId) {
    return effects()
        .persist(new UserEvent.UserCartClosed(entityId, newCartId))
        .thenReply(__ -> Done.getInstance());
  }

  @Override
  public UserState emptyState() {
    UUID uuid = UUID.randomUUID();
    String newCartId = uuid.toString();
    return new UserState(entityId, newCartId);
  }

  @Override
  public UserState applyEvent(UserEvent event) {
    logger.debug("Applying user event to user id={}", entityId);

    return switch (event) {
      case UserEvent.UserCartClosed closed -> currentState().onCartClosed(closed);
    };
  }
}
