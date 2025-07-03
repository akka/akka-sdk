// tag::top[]
package shoppingcart.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.domain.ShoppingCart;
import shoppingcart.domain.ShoppingCart.LineItem;
import shoppingcart.domain.ShoppingCartEvent;

import java.util.Collections;

// end::top[]

// tag::all[]
// tag::class[]
@ComponentId("shopping-cart") // <2>
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart, ShoppingCartEvent> { // <1>
  // end::class[]

  // tag::getCart[]
  private final String entityId;

  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEntity.class);

  public ShoppingCartEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId(); // <1>
  }

  @Override
  public ShoppingCart emptyState() { // <2>
    return new ShoppingCart(entityId, Collections.emptyList(), false);
  }

  // end::getCart[]

  // tag::addItem[]
  public Effect<Done> addItem(LineItem item) {
    if (currentState().checkedOut()) {
      logger.info("Cart id={} is already checked out.", entityId);
      return effects().error("Cart is already checked out.");
    }
    if (item.quantity() <= 0) { // <1>
      logger.info("Quantity for item {} must be greater than zero.", item.productId());
      return effects().error("Quantity for item " + item.productId() + " must be greater than zero.");
    }

    var event = new ShoppingCartEvent.ItemAdded(item); // <2>

    return effects()
        .persist(event) // <3>
        .thenReply(newState -> Done.getInstance()); // <4>
  }

  // end::addItem[]

  public Effect<Done> removeItem(String productId) {
    if (currentState().checkedOut()) {
      logger.info("Cart id={} is already checked out.", entityId);
      return effects().error("Cart is already checked out.");
    }
    if (currentState().findItemByProductId(productId).isEmpty()) {
      logger.info("Cannot remove item {} because it is not in the cart.", productId);
      return effects().error("Cannot remove item " + productId + " because it is not in the cart.");
    }

    var event = new ShoppingCartEvent.ItemRemoved(productId);

    return effects()
        .persist(event)
        .thenReply(newState -> Done.getInstance());
  }

  // tag::getCart[]
  // tag::read-only[]
  public ReadOnlyEffect<ShoppingCart> getCart() {
    return effects().reply(currentState()); // <3>
  }
  // end::read-only[]
  // end::getCart[]

  // tag::checkout[]
  public Effect<Done> checkout() {
    if (currentState().checkedOut())
      return effects().reply(Done.getInstance());

    return effects()
        .persist(new ShoppingCartEvent.CheckedOut()) // <1>
        .deleteEntity() // <2>
        .thenReply(newState -> Done.getInstance());
  }
  // end::checkout[]

  // tag::addItem[]
  @Override
  public ShoppingCart applyEvent(ShoppingCartEvent event) {
    return switch (event) {
      case ShoppingCartEvent.ItemAdded evt -> currentState().addItem(evt.item()); // <5>
      // end::addItem[]
      case ShoppingCartEvent.ItemRemoved evt -> currentState().removeItem(evt.productId());
      case ShoppingCartEvent.CheckedOut evt -> currentState().onCheckedOut();
      // tag::addItem[]
    };
  }
  // end::addItem[]
// tag::class[]
}
// end::class[]
// end::all[]
