package shoppingcart;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.TestKitSupport;
import shoppingcart.api.ShoppingCartEndpoint.LineItemRequest;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.domain.ShoppingCartState;

public class ShoppingCartIntegrationTest extends TestKitSupport {

        @Test
        public void createAndManageCart() {

                String cartId = "card-abc";
                var item1 = new LineItemRequest("tv", "Super TV 55'", 1, "A large television");
                var response1 = await(
                                componentClient
                                                .forEventSourcedEntity(cartId)
                                                .method(ShoppingCartEntity::addItem)
                                                .invokeAsync(item1));
                Assertions.assertNotNull(response1);

                var item2 = new LineItemRequest("tv-table", "Table for TV", 1, "A table perfectly sized to hold a TV");
                var response2 = await(
                                componentClient
                                                .forEventSourcedEntity(cartId)
                                                .method(ShoppingCartEntity::addItem)
                                                .invokeAsync(item2));
                Assertions.assertNotNull(response2);

                ShoppingCartState cartInfo = await(
                                componentClient
                                                .forEventSourcedEntity(cartId)
                                                .method(ShoppingCartEntity::getCart)
                                                .invokeAsync());
                Assertions.assertEquals(2, cartInfo.items().size());

                // removing one of the items
                var response3 = await(
                                componentClient
                                                .forEventSourcedEntity(cartId)
                                                .method(ShoppingCartEntity::removeItem)
                                                .invokeAsync(item1.productId()));

                Assertions.assertNotNull(response3);

                // confirming only one product remains
                ShoppingCartState cartUpdated = await(
                                componentClient
                                                .forEventSourcedEntity(cartId)
                                                .method(ShoppingCartEntity::getCart)
                                                .invokeAsync());
                Assertions.assertEquals(1, cartUpdated.items().size());
                var t2 = new ShoppingCartState.LineItem("tv-table", 1);
                Assertions.assertEquals(t2, cartUpdated.items().get(0));
        }

}
