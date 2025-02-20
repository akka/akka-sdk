// tag::top[]
package shoppingcart.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
// end::top[]
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Post;
// tag::top[]
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.domain.ShoppingCart;

import java.util.concurrent.CompletionStage;

// end::top[]

// tag::class[]

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/carts") // <1>
public class ShoppingCartEndpoint {

  private final ComponentClient componentClient;

  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEndpoint.class);

  public ShoppingCartEndpoint(ComponentClient componentClient) { // <2>
    this.componentClient = componentClient;
  }

  // end::class[]

  // tag::get[]
  @Get("/{cartId}") // <3>
  public CompletionStage<ShoppingCart> get(String cartId) {
    logger.info("Get cart id={}", cartId);
    return componentClient.forEventSourcedEntity(cartId) // <4>
        .method(ShoppingCartEntity::getCart)
        .invokeAsync(); // <5>
  }

  // end::get[]

  // tag::addItem[]
  @Put("/{cartId}/item") // <6>
  public CompletionStage<HttpResponse> addItem(String cartId, ShoppingCart.LineItem item) {
    logger.info("Adding item to cart id={} item={}", cartId, item);
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::addItem)
      .invokeAsync(item)
      .thenApply(__ -> HttpResponses.ok()); // <7>
  }

  // end::addItem[]

  @Delete("/{cartId}/item/{productId}")
  public CompletionStage<HttpResponse> removeItem(String cartId, String productId) {
    logger.info("Removing item from cart id={} item={}", cartId, productId);
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::removeItem)
      .invokeAsync(productId)
      .thenApply(__ -> HttpResponses.ok());
  }

  @Post("/{cartId}/checkout")
  public CompletionStage<HttpResponse> checkout(String cartId) {
    logger.info("Checkout cart id={}", cartId);
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::checkout)
      .invokeAsync()
      .thenApply(__ -> HttpResponses.ok());
  }

  // tag::class[]
}
// end::class[]
