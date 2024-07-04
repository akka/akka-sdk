package shoppingcart.api;


// tag::class[]

import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntityContext;
import shoppingcart.domain.ShoppingCart;
import shoppingcart.domain.ShoppingCart.Event.CheckedOut;
import shoppingcart.domain.ShoppingCart.Event.ItemAdded;
import shoppingcart.domain.ShoppingCart.Event.ItemRemoved;

import java.util.ArrayList;

@TypeId("shopping-cart") // <1>
public class ShoppingCartEntity
  extends EventSourcedEntity<ShoppingCart, ShoppingCart.Event> { // <2>

  final private String cartId;

  public ShoppingCartEntity(EventSourcedEntityContext entityContext) {
    this.cartId = entityContext.entityId();
  }

  @Override
  public ShoppingCart emptyState() { // <5>
    return new ShoppingCart(cartId, new ArrayList<>(), false);
  }


  public Effect<String> addItem(ShoppingCart.LineItem item) {
    if (currentState().checkedOut())
      return effects().error("Cart is already checked out.");

    if (item.quantity() <= 0) {
      return effects().error("Quantity for item " + item.productId() + " must be greater than zero.");
    }

    var event = new ItemAdded(item);

    return effects()
      .persist(event) // <6>
      .thenReply(newState -> "OK");
  }


  public Effect<String> removeItem(String productId) {
    if (currentState().checkedOut())
      return effects().error("Cart is already checked out.");

    return effects()
      .persist(new ItemRemoved(productId)) // <7>
      .thenReply(newState -> "OK");
  }

  public Effect<String> checkout() {
    if (currentState().checkedOut())
      return effects().reply("OK");

    return effects()
      .persist(new CheckedOut()) // <7>
      .thenReply(newState -> "OK");
  }

  public Effect<ShoppingCart> getCart() {
    return effects().reply(currentState());
  }


  @Override
  public ShoppingCart applyEvent(ShoppingCart.Event event) { // <7>
    return switch (event) {
      case ItemAdded evt -> currentState().addItem(evt.item());
      case ItemRemoved evt -> currentState().removeItem(evt.productId());
      case CheckedOut evt -> currentState().checkOut();
    };
  }
}
// end::class[]
