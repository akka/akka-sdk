package com.example;

import com.example.api.ShoppingCartDTO;
import com.example.api.ShoppingCartDTO.LineItemDTO;
import com.example.api.ShoppingCartEntity;
import kalix.javasdk.Metadata;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * This is a skeleton for implmenting integration tests for a Kalix application built with the Java SDK.
 * <p>
 * Since this is an integration tests, it interacts with the application using a WebClient
 * provided by {{KalixIntegrationTestKitSupport}}
 */
public class IntegrationTest extends KalixIntegrationTestKitSupport {

  private Duration timeout = Duration.of(5, SECONDS);

  ShoppingCartDTO getCart(String cartId) {
    return await(
      componentClient
        .forValueEntity(cartId)
        .method(ShoppingCartEntity::getCart).invokeAsync()
    );
  }

  void addItem(String cartId, String productId, String name, int quantity) {
    await(
      componentClient
        .forValueEntity(cartId)
        .method(ShoppingCartEntity::addItem)
        .invokeAsync(new LineItemDTO(productId, name, quantity))
    );
  }

  void removeItem(String cartId, String productId) {

    await(
      componentClient
        .forValueEntity(cartId)
        .method(ShoppingCartEntity::removeItem)
        .invokeAsync(productId)
    );
  }

  void removeCart(String cartId, String userRole) {
    var metadata = Metadata.EMPTY.add("Role", userRole);
    await(
      componentClient
        .forValueEntity(cartId)
        .method(ShoppingCartEntity::removeCart).withMetadata(metadata)
        .invokeAsync()

    );
  }

  LineItemDTO item(String productId, String name, int quantity) {
    return new LineItemDTO(productId, name, quantity);
  }


  String createPrePopulated() {
    return
      await(httpClient.POST("/carts/prepopulated")
        .responseBodyAs(String.class)
        .invokeAsync(), timeout)
              .body();
  }

  ShoppingCartDTO verifiedAddItem(String cartId, LineItemDTO in) {
    return await(httpClient.POST("/carts/" + cartId + "/items/add")
              .withRequestBody(in)
              .responseBodyAs(ShoppingCartDTO.class)
              .invokeAsync(), timeout)
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