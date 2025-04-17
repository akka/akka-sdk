package shoppingcart;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.TestKitSupport;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.domain.ShoppingCartState;

public class ShoppingCartIntegrationTest extends TestKitSupport {
    @Test
    public void createAndManageCart() {
        String userId = "card-abc";
        var item1 = new ShoppingCartEntity.AddLineItemCommand(
                "user",
                "tv",
                "Super TV 55'",
                1,
                "A large television");

        var response1 = componentClient
                .forEventSourcedEntity(userId)
                .method(ShoppingCartEntity::addItem)
                .invoke(item1);
        Assertions.assertNotNull(response1);

        var item2 = new ShoppingCartEntity.AddLineItemCommand("user", "tv-table",
                "Table for TV",
                1,
                "A table perfectly sized to hold a TV");
        var response2 = componentClient
                .forEventSourcedEntity(userId)
                .method(ShoppingCartEntity::addItem)
                .invoke(item2);
        Assertions.assertNotNull(response2);

        ShoppingCartState cartInfo = componentClient
                .forEventSourcedEntity(userId)
                .method(ShoppingCartEntity::getCart)
                .invoke();
        Assertions.assertEquals(2, cartInfo.items().size());

        // removing one of the items
        var response3 = componentClient
                .forEventSourcedEntity(userId)
                .method(ShoppingCartEntity::removeItem)
                .invoke(item1.productId());

        Assertions.assertNotNull(response3);

        // confirming only one product remains
        ShoppingCartState cartUpdated = componentClient
                .forEventSourcedEntity(userId)
                .method(ShoppingCartEntity::getCart)
                .invoke();
        Assertions.assertEquals(1, cartUpdated.items().size());
        var t2 = new ShoppingCartState.LineItem("tv-table", 1);
        Assertions.assertEquals(t2, cartUpdated.items().get(0));
    }

}
