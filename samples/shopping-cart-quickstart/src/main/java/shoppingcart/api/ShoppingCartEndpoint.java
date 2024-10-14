package shoppingcart.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.domain.ShoppingCart;

import java.util.concurrent.CompletionStage;

// tag::all[]

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
// tag::endpoint-component-interaction[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/shopping-cart")
public class ShoppingCartEndpoint {

  private final ComponentClient componentClient;

  public ShoppingCartEndpoint(ComponentClient componentClient) { // <1>
    this.componentClient = componentClient;
  }

  @Get("/{cartId}")
  public CompletionStage<ShoppingCart> get(String cartId) {
    return componentClient.forEventSourcedEntity(cartId) // <2>
        .method(ShoppingCartEntity::getCart)
        .invokeAsync(); // <3>
  }

  @Put("/{cartId}/item")
  public CompletionStage<HttpResponse> addItem(String cartId, ShoppingCart.LineItem item) {
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::addItem)
      .invokeAsync(item)
      .thenApply(__ -> HttpResponses.ok()); // <4>
  }
  // end::endpoint-component-interaction[]

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

}
// end::all[]
