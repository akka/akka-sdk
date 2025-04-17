package com.example.api;

import akka.javasdk.Metadata;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import com.example.api.ShoppingCartDTO.LineItemDTO;
import com.example.application.ShoppingCartEntity;
import com.example.domain.ShoppingCart;

import java.util.UUID;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/carts")
public class ShoppingCartEndpoint {

  private final ComponentClient componentClient;

  public ShoppingCartEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient; // <1>
  }

  @Post("/create")
  public String create() {
    final String cartId = UUID.randomUUID().toString();
    try {
        componentClient.forKeyValueEntity(cartId)
            .method(ShoppingCartEntity::create)
            .invoke();
      return cartId;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create cart, please retry", e);
    }
  }


  @Post("/{cartId}/items")
  public ShoppingCartDTO verifiedAddItem(String cartId,
                                                          LineItemDTO addLineItem) {
    if (addLineItem.name().equalsIgnoreCase("carrot")) {
      throw new RuntimeException("Carrots no longer for sale");
    } else {
      var addItemResult = componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::addItem)
        .invoke(addLineItem.toDomain());
      return ShoppingCartDTO.of(addItemResult);
    }
  }


  @Post("/prepopulated")
  public String createPrePopulated() {
    final String cartId = UUID.randomUUID().toString();
    componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::create)
        .invoke();

    var initialItem = new ShoppingCart.LineItem("e", "eggplant", 1);
    componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::addItem)
        .invoke(initialItem);

    return cartId;
  }

  @Post("/{cartId}/unsafeAddItem")
  public String unsafeValidation(String cartId,
                                                  LineItemDTO addLineItem) {
    // NOTE: This is an example of an anti-pattern, do not copy this
    ShoppingCart cart =
      componentClient.forKeyValueEntity(cartId).method(ShoppingCartEntity::getCart).invoke(); // <1>

    int totalCount = cart.items().stream()
        .mapToInt(ShoppingCart.LineItem::quantity)
        .sum();

    if (totalCount < 10) {
      throw HttpException.badRequest("Max 10 items in a cart");
    } else {
      var cartAfterAdd =
          componentClient.forKeyValueEntity(cartId)
            .method(ShoppingCartEntity::addItem)
            .invoke(addLineItem.toDomain());
        return cartAfterAdd.cartId();
      }
    }


  @Delete("/{cartId}")
  public String removeCart(String cartId
    /*, No headers support quite yet @Headers("UserRole") String userRole */) {
    var userRole = "Admin";
    var metadata = Metadata.EMPTY.add("Role", userRole);
    return
      componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::removeCart)
        .withMetadata(metadata)
        .invoke();
  }

  @Get("/{cartId}")
  public ShoppingCartDTO getCart(String cartId) {
    var cart = componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::getCart)
        .invoke();
    return ShoppingCartDTO.of(cart);
  }
}
