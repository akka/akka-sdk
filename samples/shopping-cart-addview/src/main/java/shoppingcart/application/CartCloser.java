package shoppingcart.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import shoppingcart.application.UserEntity.CloseCartCommand;
import shoppingcart.domain.ShoppingCartEvent;

// tag::consumer[]
@ComponentId("cart-closer-consumer")
@Consume.FromEventSourcedEntity(ShoppingCartEntity.class)
public class CartCloser extends Consumer {

  private Logger logger = LoggerFactory.getLogger(CartCloser.class);
  protected final ComponentClient componentClient;

  public CartCloser(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // After a user's cart has been checked out, tell the user entity to generate
  // a new UUID for the current/open cart
  public Effect onCheckedOut(ShoppingCartEvent.CheckedOut event) {
    logger.debug("Closing cart for user {} due to checkout", event.userId());

    UUID uuid = UUID.randomUUID();
    String newCartId = uuid.toString();

    componentClient.forEventSourcedEntity(event.userId())
        .method(UserEntity::closeCart)
        .invokeAsync(new CloseCartCommand(event.cartId(), newCartId));

    return effects().done();
  }

  public Effect onItemRemoved(ShoppingCartEvent.ItemRemoved removed) {
    return effects().ignore();
  }

  public Effect onItemAdded(ShoppingCartEvent.ItemAdded added) {
    return effects().ignore();
  }
}
// end::consumer[]
