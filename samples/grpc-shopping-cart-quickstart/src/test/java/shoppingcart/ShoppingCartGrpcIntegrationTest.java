package shoppingcart;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import shoppingcart.api.proto.ShoppingCartEndpointClient;
import shoppingcart.api.proto.ShoppingCartEndpointOuterClass;

// tag::sample-it[]
public class ShoppingCartGrpcIntegrationTest extends TestKitSupport { // <1>

  @Test
  public void createAndManageCart() {

    var client = getGrpcEndpointClient(ShoppingCartEndpointClient.class); // <2>

    var item = ShoppingCartEndpointOuterClass.LineItem.newBuilder()
        .setProductId("tv")
        .setName("Super TV 55'")
        .setQuantity(1)
        .build();
    var response = await(
        client.addItem(
            ShoppingCartEndpointOuterClass.AddItemRequest.newBuilder()
                .setCartId("cart-abc")
                .setItem(item)
                .build()));

    Assertions.assertTrue(response.getSuccess());
    // end::sample-it[]

    var item2 = ShoppingCartEndpointOuterClass.LineItem.newBuilder()
        .setProductId("tv-table")
        .setName("Table for TV")
        .setQuantity(1)
        .build();
    var response2 = await(
        client.addItem(
            ShoppingCartEndpointOuterClass.AddItemRequest.newBuilder()
                .setCartId("cart-abc")
                .setItem(item2)
                .build()));

    Assertions.assertTrue(response2.getSuccess());

    var cartInfo = await(
        client.getCart(
            ShoppingCartEndpointOuterClass.GetCartRequest.newBuilder()
                .setCartId("cart-abc")
                .build()));

    Assertions.assertEquals(2, cartInfo.getItemsCount());
  }

// tag::sample-it[]
}
// end::sample-it[]