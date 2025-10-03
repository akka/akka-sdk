package shoppingcart.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.application.UserEntity.CloseCartCommand;
import shoppingcart.domain.ShoppingCartEvent;

// tag::consumer[]
@Component(id = "cart-closer-consumer")
@Consume.FromEventSourcedEntity(value = ShoppingCartEntity.class, ignoreUnknown = true)
public class CartCloser extends Consumer {

  private Logger logger = LoggerFactory.getLogger(CartCloser.class);
  protected final ComponentClient componentClient;

  public CartCloser(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onCheckedOut(ShoppingCartEvent.CheckedOut event) {
    logger.debug("Closing cart for user {} due to checkout", event.userId());

    componentClient
      .forEventSourcedEntity(event.userId())
      .method(UserEntity::closeCart)
      .invoke(new CloseCartCommand(event.cartId()));

    return effects().done();
  }
}
// end::consumer[]
