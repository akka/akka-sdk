// tag::top[]
package shoppingcart.polymorphism.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.polymorphism.domain.OpenShoppingCart;
import shoppingcart.polymorphism.domain.ShoppingCart;
import shoppingcart.polymorphism.domain.ShoppingCartCommand;
import shoppingcart.polymorphism.domain.ShoppingCartEvent;

import java.util.Collections;


@ComponentId("shopping-cart-poly")
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart, ShoppingCartEvent> { // <1>

  private final String entityId;

  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEntity.class);

  public ShoppingCartEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId(); // <1>
  }

  @Override
  public ShoppingCart emptyState() { // <2>
    return new OpenShoppingCart(entityId, Collections.emptyList());
  }

  public Effect<Done> handleCommand(ShoppingCartCommand command) {
    //todo some basic validation
    var event = currentState().handleCommand(command);

    return effects().persist(event).thenReply(__ -> Done.done());
  }

  public ReadOnlyEffect<ShoppingCart> getCart() {
    return effects().reply(currentState()); // <3>
  }


  @Override
  public ShoppingCart applyEvent(ShoppingCartEvent event) {
    return currentState().applyEvent(event);
  }
}
