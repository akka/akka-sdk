package shoppingcart.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.domain.ShoppingCart;
import shoppingcart.domain.ShoppingCartEvent;

@ComponentId("shopping-cart")
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart, ShoppingCartEvent> {

  private final String entityId;

  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEntity.class);

  // tag::newentityapi[]
  public record AddLineItemCommand(
    String userId,
    String productId,
    String name,
    int quantity,
    String description
  ) {}

  // end::newentityapi[]

  public ShoppingCartEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public ShoppingCart emptyState() {
    return new ShoppingCart(entityId, Collections.emptyList(), false);
  }

  public Effect<Done> addItem(AddLineItemCommand item) {
    if (currentState().checkedOut()) {
      logger.info("Cart id={} is already checked out.", entityId);
      return effects().error("Cannot add line items to a checked-out cart.");
    }
    if (item.quantity() <= 0) {
      logger.info("Quantity for item {} must be greater than zero.", item.productId());
      return effects()
        .error("Quantity for item " + item.productId() + " must be greater than zero.");
    }

    var event = new ShoppingCartEvent.ItemAdded(
      entityId,
      item.userId(),
      item.productId(),
      item.name(),
      item.quantity(),
      item.description()
    );

    return effects().persist(event).thenReply(newState -> Done.done());
  }

  public Effect<Done> removeItem(String productId) {
    if (currentState().checkedOut()) {
      logger.info("Cart id={} is already checked out.", entityId);
      return effects().error("Cannot remove line items from a checked-out cart.");
    }
    if (currentState().findItemByProductId(productId).isEmpty()) {
      logger.info("Cannot remove item {} because it is not in the cart.", productId);
      return effects()
        .error("Cannot remove item " + productId + " because it is not in the cart.");
    }

    var event = new ShoppingCartEvent.ItemRemoved(entityId, productId);

    return effects().persist(event).thenReply(newState -> Done.done());
  }

  public ReadOnlyEffect<ShoppingCart> getCart() {
    return effects().reply(currentState());
  }

  public Effect<Done> checkout(String userId) {
    if (currentState().checkedOut()) return effects().reply(Done.done());

    return effects()
      .persist(new ShoppingCartEvent.CheckedOut(entityId, userId))
      .deleteEntity()
      .thenReply(newState -> Done.done());
  }

  @Override
  public ShoppingCart applyEvent(ShoppingCartEvent event) {
    return switch (event) {
      case ShoppingCartEvent.ItemAdded evt -> currentState()
        .onItemAdded(new ShoppingCart.LineItem(evt.productId(), evt.quantity()));
      case ShoppingCartEvent.ItemRemoved evt -> currentState().removeItem(evt.productId());
      case ShoppingCartEvent.CheckedOut evt -> currentState().onCheckedOut();
    };
  }
}
