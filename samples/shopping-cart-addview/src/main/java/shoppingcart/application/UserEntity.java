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

// tag::entity[]
@ComponentId("user")
public class UserEntity extends EventSourcedEntity<UserState, UserEvent> {
  private final String entityId;

  private static final Logger logger = LoggerFactory.getLogger(UserEntity.class);

  public record CloseCartCommand(String oldCartId, String newCartId) {
  }

  public UserEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  public ReadOnlyEffect<String> currentCartId() {
    return effects().reply(currentState().currentCartId());
  }

  // We don't create a random cart ID in this method because we always
  // want command handlers to be deterministic
  public Effect<Done> closeCart(CloseCartCommand command) {
    // Reject close commands for anything other than the current cart ID -
    // idempotent
    if (!command.oldCartId().equals(currentState().currentCartId())) {
      return effects().reply(Done.getInstance());
    }
    return effects()
        .persist(new UserEvent.UserCartClosed(entityId, command.newCartId()))
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
// end::entity[]
