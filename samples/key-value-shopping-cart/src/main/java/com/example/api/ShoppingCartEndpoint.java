package com.example.api;

import akka.javasdk.Metadata;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import com.example.application.ShoppingCartDTO;
import com.example.application.ShoppingCartDTO.LineItemDTO;
import com.example.application.ShoppingCartEntity;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

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
  public CompletionStage<String> create() {
    final String cartId = UUID.randomUUID().toString();
    CompletionStage<ShoppingCartDTO> shoppingCartCreated =
      componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::create)
        .invokeAsync();


    // transform response
    CompletionStage<String> response =
      shoppingCartCreated.handle((empty, error) -> {
        if (error == null) {
          return cartId;
        } else {
          throw new RuntimeException("Failed to create cart, please retry");
        }
      });

    return response;
  }


  @Post("/{cartId}/items")
  public CompletionStage<ShoppingCartDTO> verifiedAddItem(String cartId,
                                                          LineItemDTO addLineItem) {
    if (addLineItem.name().equalsIgnoreCase("carrot")) {
      throw new RuntimeException("Carrots no longer for sale");
    } else {
      var addItemResult = componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::addItem)
        .invokeAsync(addLineItem);
      return addItemResult;
    }
  }


  @Post("/prepopulated")
  public CompletionStage<String> createPrePopulated() {
    final String cartId = UUID.randomUUID().toString();
    CompletionStage<ShoppingCartDTO> shoppingCartCreated =
      componentClient.forKeyValueEntity(cartId).method(ShoppingCartEntity::create).invokeAsync();

    CompletionStage<ShoppingCartDTO> cartPopulated =
      shoppingCartCreated.thenCompose(empty -> {
        var initialItem = new LineItemDTO("e", "eggplant", 1);

        return componentClient.forKeyValueEntity(cartId)
          .method(ShoppingCartEntity::addItem)
          .invokeAsync(initialItem);
      });

    CompletionStage<String> response = cartPopulated.thenApply(ShoppingCartDTO::cartId);

    return response;
  }

  @Post("/{cartId}/unsafeAddItem")
  public CompletionStage<String> unsafeValidation(String cartId,
                                                  LineItemDTO addLineItem) {
    // NOTE: This is an example of an anti-pattern, do not copy this
    CompletionStage<ShoppingCartDTO> cartReply =
      componentClient.forKeyValueEntity(cartId).method(ShoppingCartEntity::getCart).invokeAsync(); // <1>

    CompletionStage<String> response = cartReply.thenCompose(cart -> {
      int totalCount = cart.items().stream()
        .mapToInt(LineItemDTO::quantity)
        .sum();

      if (totalCount < 10) {
        throw HttpException.badRequest("Max 10 items in a cart");
      } else {
        CompletionStage<String> addItemReply =
          componentClient.forKeyValueEntity(cartId)
            .method(ShoppingCartEntity::addItem)
            .invokeAsync(addLineItem)
            .thenApply(ShoppingCartDTO::cartId);
        return addItemReply; // <2>
      }
    });

    return response;
  }

  @Delete("/{cartId}")
  public CompletionStage<String> removeCart(String cartId
    /*, No headers support quite yet @Headers("UserRole") String userRole */) {
    var userRole = "Admin";
    var metadata = Metadata.EMPTY.add("Role", userRole);
    return
      componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::removeCart)
        .withMetadata(metadata)
        .invokeAsync();
  }

  @Get("/{cartId}")
  public CompletionStage<ShoppingCartDTO> getCart(String cartId) {
    return
      componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::getCart)
        .invokeAsync();
  }
}