package shoppingcart.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.domain.User;
import shoppingcart.domain.UserEvent;

/**
 * The user entity's main role in this sample is to maintain a monotonically
 * increasing shopping cart ID on behalf of each user. In the future it should
 * be easy to add more user-specific features and events.
 */
// tag::entity[]
@ComponentId("user")
public class UserEntity extends EventSourcedEntity<User, UserEvent> {

  private final String entityId;

  private static final Logger logger = LoggerFactory.getLogger(UserEntity.class);

  public record CloseCartCommand(String cartId) {}

  public UserEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  public ReadOnlyEffect<String> currentCartId() {
    return effects().reply(entityId + "-" + currentState().currentCartId());
  }

  public Effect<Done> closeCart(CloseCartCommand command) {
    return effects()
      .persist(new UserEvent.UserCartClosed(entityId, command.cartId()))
      .thenReply(__ -> Done.done());
  }

  @Override
  public User emptyState() {
    int newCartId = 1;
    return new User(entityId, newCartId);
  }

  @Override
  public User applyEvent(UserEvent event) {
    logger.debug("Applying user event to user id={}", entityId);

    return switch (event) {
      case UserEvent.UserCartClosed closed -> currentState().closeCart();
    };
  }
}
// end::entity[]
