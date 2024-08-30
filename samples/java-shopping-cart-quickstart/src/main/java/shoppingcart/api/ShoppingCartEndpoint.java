package shoppingcart.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Endpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.domain.ShoppingCart;

import java.util.concurrent.CompletionStage;

@Endpoint("/shopping-cart")
public class ShoppingCartEndpoint {

  private final ComponentClient componentClient;

  public ShoppingCartEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Put("/{cartId}/item")
  public CompletionStage<HttpResponse> addItem(String cartId, ShoppingCart.LineItem item) {
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::addItem)
      .invokeAsync(item)
      .thenApply(__ -> HttpResponses.ok());
  }

  @Delete("/{cartId}/item/{productId}")
  public CompletionStage<HttpResponse> removeItem(String cartId, String productId) {
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::removeItem)
      .invokeAsync(productId)
      .thenApply(__ -> HttpResponses.ok());
  }

  @Post("/{cartId}/checkout")
  public CompletionStage<HttpResponse> checkout(String cartId) {
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::checkout)
      .invokeAsync()
      .thenApply(__ -> HttpResponses.ok());
  }

  @Get("/{cartId}")
  public CompletionStage<ShoppingCart> get(String cartId) {
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::getCart)
      .invokeAsync();
  }
}
