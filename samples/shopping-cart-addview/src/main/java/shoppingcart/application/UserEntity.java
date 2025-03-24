package shoppingcart.application;

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

  public record CloseCartCommand(String cartId) {
  }

  public UserEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  public ReadOnlyEffect<String> currentCartId() {
    return effects().reply(entityId + "-" + String.valueOf(currentState().currentCartId()));
  }

  public Effect<Done> closeCart(CloseCartCommand command) {
    return effects()
        .persist(new UserEvent.UserCartClosed(entityId, command.cartId()))
        .thenReply(__ -> Done.getInstance());
  }

  @Override
  public UserState emptyState() {
    int newCartId = 1;
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
