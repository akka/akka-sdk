package shoppingcart;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.domain.ShoppingCart;
import shoppingcart.domain.ShoppingCart.LineItem;

// tag::sample-it[]
public class ShoppingCartIntegrationTest extends TestKitSupport { // <1>

  @Test
  public void createAndManageCart() {
    String cartId = "card-abc";
    var item1 = new LineItem("tv", "Super TV 55'", 1);
    var response1 = componentClient // <2>
      .forEventSourcedEntity(cartId) // <3>
      .method(ShoppingCartEntity::addItem) // <4>
      .invoke(item1);
    Assertions.assertNotNull(response1);
    // end::sample-it[]

    var item2 = new LineItem("tv-table", "Table for TV", 1);
    var response2 = componentClient
      .forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::addItem)
      .invoke(item2);
    Assertions.assertNotNull(response2);

    ShoppingCart cartInfo = componentClient
      .forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::getCart)
      .invoke();
    Assertions.assertEquals(2, cartInfo.items().size());

    // removing one of the items
    var response3 = componentClient
      .forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::removeItem)
      .invoke(item1.productId());

    Assertions.assertNotNull(response3);

    // confirming only one product remains
    // tag::sample-it[]
    // confirming only one product remains
    ShoppingCart cartUpdated = componentClient
      .forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::getCart) // <5>
      .invoke();
    Assertions.assertEquals(1, cartUpdated.items().size()); // <6>
    Assertions.assertEquals(item2, cartUpdated.items().get(0));
  }
}
// end::sample-it[]
