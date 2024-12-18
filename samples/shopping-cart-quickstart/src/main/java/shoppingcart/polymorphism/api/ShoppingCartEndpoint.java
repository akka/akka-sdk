// tag::top[]
package shoppingcart.polymorphism.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.domain.ShoppingCart.LineItem;
import shoppingcart.polymorphism.application.ShoppingCartEntity;
import shoppingcart.polymorphism.domain.ShoppingCart;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.CheckedOutShoppingCartCommand.Cancel;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.OpenShoppingCartCommand.AddItem;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.OpenShoppingCartCommand.Checkout;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.OpenShoppingCartCommand.RemoveItem;

import java.util.concurrent.CompletionStage;

// end::top[]

// tag::class[]

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
// tag::endpoint-component-interaction[]
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
  public CompletionStage<HttpResponse> addItem(String cartId, LineItem item) {
    logger.info("Adding item to cart id={} item={}", cartId, item);
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::handleCommand)
      .invokeAsync(new AddItem(item))
      .thenApply(__ -> HttpResponses.ok()); // <7>
  }
  // end::endpoint-component-interaction[]

  // end::addItem[]

  @Delete("/{cartId}/item/{productId}")
  public CompletionStage<HttpResponse> removeItem(String cartId, String productId) {
    logger.info("Removing item from cart id={} item={}", cartId, productId);
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::handleCommand)
      .invokeAsync(new RemoveItem(productId))
      .thenApply(__ -> HttpResponses.ok());
  }

  @Post("/{cartId}/checkout")
  public CompletionStage<HttpResponse> checkout(String cartId) {
    logger.info("Checkout cart id={}", cartId);
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::handleCommand)
      .invokeAsync(new Checkout())
      .thenApply(__ -> HttpResponses.ok());
  }

  @Post("/{cartId}/cancel")
  public CompletionStage<HttpResponse> cancel(String cartId) {
    logger.info("Checkout cart id={}", cartId);
    return componentClient.forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::handleCommand)
      .invokeAsync(new Cancel())
      .thenApply(__ -> HttpResponses.ok());
  }

  // tag::class[]
}
// end::class[]
