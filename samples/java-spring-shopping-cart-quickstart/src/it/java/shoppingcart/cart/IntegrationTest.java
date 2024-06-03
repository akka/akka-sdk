package shoppingcart.cart;

import kalix.javasdk.client.ComponentClient;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import shoppingcart.Main;
import shoppingcart.api.ShoppingCartEntity;
import shoppingcart.domain.ShoppingCart;
import shoppingcart.domain.ShoppingCart.LineItem;




/**
 * This is a skeleton for implementing integration tests for a Kalix application built with the Spring SDK.
 * <p>
 * This test will initiate a Kalix Runtime using testcontainers and therefore it's required to have Docker installed
 * on your machine. This test will also start your Spring Boot application.
 * <p>
 * Since this is an integration tests, it interacts with the application using a WebClient
 * (already configured and provided automatically through injection).
 */
@SpringBootTest(classes = Main.class)
public class IntegrationTest extends KalixIntegrationTestKitSupport {


  @Autowired
  private ComponentClient componentClient;


  @Test
  public void createAndManageCart() {

    String cartId = "card-abc";

    var item1 = new LineItem("tv", "Super TV 55'", 1);

    var response1 = await(
      componentClient
        .forEventSourcedEntity(cartId)
        .methodRef(ShoppingCartEntity::addItem)
        .invokeAsync(item1)
    );
    Assertions.assertNotNull(response1);

    var item2 = new LineItem("tv-table", "Table for TV", 1);

    var response2 = await(
      componentClient
        .forEventSourcedEntity(cartId)
        .methodRef(ShoppingCartEntity::addItem)
        .invokeAsync(item2)
    );
    Assertions.assertNotNull(response2);


    ShoppingCart cartInfo = await(
      componentClient
        .forEventSourcedEntity(cartId)
        .methodRef(ShoppingCartEntity::getCart)
        .invokeAsync()
    );
    Assertions.assertEquals(2, cartInfo.items().size());


    // removing one of the items
    var response3 =
      await(
        componentClient
          .forEventSourcedEntity(cartId)
          .methodRef(ShoppingCartEntity::removeItem)
          .invokeAsync(item1.productId())
      );

    Assertions.assertNotNull(response3);

    // confirming only one product remains
    ShoppingCart cartUpdated = await(
      componentClient
        .forEventSourcedEntity(cartId)
        .methodRef(ShoppingCartEntity::getCart).invokeAsync()
    );
    Assertions.assertEquals(1, cartUpdated.items().size());
    Assertions.assertEquals(item2, cartUpdated.items().get(0));
  }


}