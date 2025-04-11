package com.example;

import akka.javasdk.Metadata;
import akka.javasdk.testkit.TestKitSupport;
import com.example.api.ShoppingCartDTO;
import com.example.api.ShoppingCartDTO.LineItemDTO;
import com.example.application.ShoppingCartEntity;
import com.example.domain.ShoppingCart;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class ShoppingCartIntegrationTest extends TestKitSupport {

  ShoppingCart getCart(String cartId) {
    return
      componentClient
        .forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::getCart).invoke();
  }

  void addItem(String cartId, String productId, String name, int quantity) {

      componentClient
        .forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::addItem)
        .invoke(new ShoppingCart.LineItem(productId, name, quantity)
    );
  }

  void removeItem(String cartId, String productId) {


      componentClient
        .forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::removeItem)
        .invoke(productId);
  }

  void removeCart(String cartId, String userRole) {
    var metadata = Metadata.EMPTY.add("Role", userRole);

      componentClient
        .forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::removeCart).withMetadata(metadata)
        .invoke();
  }

  ShoppingCart.LineItem item(String productId, String name, int quantity) {
    return new ShoppingCart.LineItem(productId, name, quantity);
  }


  String createPrePopulated() {
    return
      httpClient.POST("/carts/prepopulated")
        .responseBodyAs(String.class)
        .invoke()
        .body();
  }

  ShoppingCartDTO verifiedAddItem(String cartId, LineItemDTO in) {
    return httpClient.POST("/carts/" + cartId + "/items")
      .withRequestBody(in)
      .responseBodyAs(ShoppingCartDTO.class)
      .invoke()
      .body();
  }

  @Test
  public void emptyCartByDefault() {
    assertEquals(0, getCart("user1").items().size(), "shopping cart should be empty");
  }

  @Test
  public void addItemsToCart() {
    addItem("cart2", "a", "Apple", 1);
    addItem("cart2", "b", "Banana", 2);
    addItem("cart2", "c", "Cantaloupe", 3);
    var cart = getCart("cart2");
    assertEquals(3, cart.items().size(), "shopping cart should have 3 items");
    assertEquals(
      List.of(item("a", "Apple", 1), item("b", "Banana", 2), item("c", "Cantaloupe", 3)),
      cart.items(),
      "shopping cart should have expected items");
  }

  @Test
  public void removeItemsFromCart() throws Exception {
    addItem("cart3", "a", "Apple", 1);
    addItem("cart3", "b", "Banana", 2);
    var cart1 = getCart("cart3");
    assertEquals(2, cart1.items().size(), "shopping cart should have 2 items");
    assertEquals(
      cart1.items(),
      List.of(item("a", "Apple", 1), item("b", "Banana", 2)),
      "shopping cart should have expected items");
    removeItem("cart3", "a");
    var cart2 = getCart("cart3");
    assertEquals(1, cart2.items().size(), "shopping cart should have 1 item");
    assertEquals(
      cart2.items(),
      List.of(item("b", "Banana", 2)),
      "shopping cart should have expected items");
  }

  @Test
  public void removeCart() throws Exception {
    addItem("cart4", "a", "Apple", 42);
    var cart1 = getCart("cart4");
    assertEquals(1, cart1.items().size(), "shopping cart should have 1 item");
    assertEquals(
      cart1.items(),
      List.of(item("a", "Apple", 42)),
      "shopping cart should have expected items");
    removeCart("cart4", "Admin");
    assertEquals(0, getCart("cart4").items().size(), "shopping cart should be empty");
  }

  @Test
  public void createNewPrePopulatedCart() throws Exception {
    var cart = getCart(createPrePopulated());
    assertEquals(1, cart.items().size());
  }

  @Test
  public void verifiedAddItem() throws Exception {
    final String cartId = "carrot-cart";
    assertThrows(Exception.class, () ->
      verifiedAddItem(
        cartId,
        new LineItemDTO("c", "Carrot", 4)
      )
    );
    verifiedAddItem(
      cartId,
      new LineItemDTO("b", "Banana", 1));
    var cart = getCart(cartId);
    assertEquals(1, cart.items().size());
  }


}
