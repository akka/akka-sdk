package shoppingcart.application;


// tag::class[]

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import shoppingcart.domain.ShoppingCart;
import shoppingcart.domain.ShoppingCartEvent;
import shoppingcart.domain.ShoppingCartEvent.CheckedOut;
import shoppingcart.domain.ShoppingCartEvent.ItemAdded;
import shoppingcart.domain.ShoppingCartEvent.ItemRemoved;

import java.util.ArrayList;

import static akka.Done.done;

@ComponentId("shopping-cart") // <1>
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart, ShoppingCartEvent> { // <2>

  final private String cartId;

  public ShoppingCartEntity(EventSourcedEntityContext entityContext) {
    this.cartId = entityContext.entityId();
  }

  @Override
  public ShoppingCart emptyState() { // <5>
    return new ShoppingCart(cartId, new ArrayList<>(), false);
  }


  public Effect<Done> addItem(ShoppingCart.LineItem item) {
    if (currentState().checkedOut())
      return effects().error("Cart is already checked out.");

    if (item.quantity() <= 0) {
      return effects().error("Quantity for item " + item.productId() + " must be greater than zero.");
    }

    var event = new ItemAdded(item);

    return effects()
      .persist(event) // <6>
      .thenReply(__ -> done());
  }


  public Effect<Done> removeItem(String productId) {
    if (currentState().checkedOut())
      return effects().error("Cart is already checked out.");

    return effects()
      .persist(new ItemRemoved(productId)) // <7>
      .thenReply(__ -> done());
  }

  public Effect<Done> checkout() {
    if (currentState().checkedOut())
      return effects().reply(done());

    return effects()
      .persist(new CheckedOut()) // <7>
      .thenReply(__ -> done());
  }

  public ReadOnlyEffect<ShoppingCart> getCart() {
    return effects().reply(currentState());
  }

  @Override
  public ShoppingCart applyEvent(ShoppingCartEvent event) { // <7>
    return switch (event) {
      case ItemAdded evt -> currentState().addItem(evt.item());
      case ItemRemoved evt -> currentState().removeItem(evt.productId());
      case CheckedOut __ -> currentState().checkOut();
    };
  }
}
// end::class[]
