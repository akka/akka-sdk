package shoppingcart.cart;

import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
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

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.time.temporal.ChronoUnit.SECONDS;


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


  private Duration timeout = Duration.of(5, SECONDS);
  @Autowired
  private ComponentClient componentClient;


  @Test
  public void createAndManageCart() {

    String cartId = "card-abc";

    var item1 = new LineItem("tv", "Super TV 55'", 1);

    var response1 = execute(
      componentClient
        .forEventSourcedEntity(cartId)
        .call(ShoppingCartEntity::addItem)
        .params(item1)
    );
    Assertions.assertNotNull(response1);

    var item2 = new LineItem("tv-table", "Table for TV", 1);

    var response2 = execute(
      componentClient
        .forEventSourcedEntity(cartId)
        .call(ShoppingCartEntity::addItem)
        .params(item2)
    );
    Assertions.assertNotNull(response2);


    ShoppingCart cartInfo = execute(
      componentClient
        .forEventSourcedEntity(cartId)
        .call(ShoppingCartEntity::getCart)
    );
    Assertions.assertEquals(2, cartInfo.items().size());


    // removing one of the items
    var response3 =
      execute(
        componentClient
          .forEventSourcedEntity(cartId)
          .call(ShoppingCartEntity::removeItem)
          .params(item1.productId())
      );

    Assertions.assertNotNull(response3);

    // confirming only one product remains
    ShoppingCart cartUpdated = execute(
      componentClient
        .forEventSourcedEntity(cartId)
        .call(ShoppingCartEntity::getCart)
    );
    Assertions.assertEquals(1, cartUpdated.items().size());
    Assertions.assertEquals(item2, cartUpdated.items().get(0));
  }

  protected <T> T execute(DeferredCall<Any, T> deferredCall) {
    try {
      return deferredCall.execute().toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

}