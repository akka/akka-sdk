package com.example.api;

import akka.javasdk.http.HttpException;
import com.example.api.ShoppingCartDTO.LineItemDTO;
import akka.javasdk.Metadata;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

@HttpEndpoint("/carts")
public class ShoppingCartController {
  // end::forward-headers[]

  private final ComponentClient componentClient;

  public ShoppingCartController(ComponentClient componentClient) {
    this.componentClient = componentClient; // <1>
  }

  // end::forward[]

  // tag::initialize[]
  @Post("/create")
  public CompletionStage<String> initializeCart() {
    final String cartId = UUID.randomUUID().toString(); // <1>
    CompletionStage<ShoppingCartDTO> shoppingCartCreated =
      componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::create) // <2>
        .invokeAsync(); // <3>


    // transform response
    CompletionStage<String> response =
      shoppingCartCreated.handle((empty, error) -> { // <4>
        if (error == null) {
          return cartId; // <5>
        } else {
          throw new RuntimeException("Failed to create cart, please retry"); // <6>
        }
      });

    return response; // <7>
  }
  // end::initialize[]

  // tag::forward[]
  @Post("/{cartId}/items/add") // <2>
  public CompletionStage<ShoppingCartDTO> verifiedAddItem(String cartId,
                                                        LineItemDTO addLineItem) {
    if (addLineItem.name().equalsIgnoreCase("carrot")) { // <3>
      throw new RuntimeException("Carrots no longer for sale"); // <4>
    } else {
      var addItemResult = componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::addItem)
        .invokeAsync(addLineItem); // <5>
      return addItemResult; // <6>
    }
  }
  // end::forward[]


  // tag::createPrePopulated[]
  @Post("/prepopulated")
  public CompletionStage<String> createPrePopulated() {
    final String cartId = UUID.randomUUID().toString();
    CompletionStage<ShoppingCartDTO> shoppingCartCreated =
      componentClient.forKeyValueEntity(cartId).method(ShoppingCartEntity::create).invokeAsync();

    CompletionStage<ShoppingCartDTO> cartPopulated =
      shoppingCartCreated.thenCompose(empty -> { // <1>
        var initialItem = new LineItemDTO("e", "eggplant", 1);

        return componentClient.forKeyValueEntity(cartId)
          .method(ShoppingCartEntity::addItem)
          .invokeAsync(initialItem); // <2>
      });

    CompletionStage<String> response = cartPopulated.thenApply(ShoppingCartDTO::cartId); // <4>

    return response; // <5>
  }
  // end::createPrePopulated[]

  // tag::unsafeValidation[]
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
  // end::unsafeValidation[]

  // tag::forward-headers[]
  @Delete("/{cartId}")
  public CompletionStage<String> removeCart(String cartId
          /*, No headers support quite yet @Headers("UserRole") String userRole */) { // <2>
    var userRole = "Admin";
    var metadata = Metadata.EMPTY.add("Role", userRole);
    return
      componentClient.forKeyValueEntity(cartId)
        .method(ShoppingCartEntity::removeCart)
        .withMetadata(metadata)
        .invokeAsync(); // <4>
  }
}
// end::forward-headers[]
