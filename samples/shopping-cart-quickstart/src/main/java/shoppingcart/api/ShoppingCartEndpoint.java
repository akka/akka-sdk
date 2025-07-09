// tag::top[]
package shoppingcart.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
// end::top[]
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
// tag::top[]
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.domain.ShoppingCart;

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
  public ShoppingCart get(String cartId) {
    logger.info("Get cart id={}", cartId);
    return componentClient
      .forEventSourcedEntity(cartId) // <4>
      .method(ShoppingCartEntity::getCart)
      .invoke(); // <5>
  }

  // end::get[]

  // tag::addItem[]
  @Put("/{cartId}/item") // <6>
  public HttpResponse addItem(String cartId, ShoppingCart.LineItem item) {
    logger.info("Adding item to cart id={} item={}", cartId, item);
    componentClient
      .forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::addItem)
      .invoke(item);
    return HttpResponses.ok(); // <7>
  }

  // end::endpoint-component-interaction[]

  // end::addItem[]

  @Delete("/{cartId}/item/{productId}")
  public HttpResponse removeItem(String cartId, String productId) {
    logger.info("Removing item from cart id={} item={}", cartId, productId);
    componentClient
      .forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::removeItem)
      .invoke(productId);
    return HttpResponses.ok();
  }

  @Post("/{cartId}/checkout")
  public HttpResponse checkout(String cartId) {
    logger.info("Checkout cart id={}", cartId);
    componentClient
      .forEventSourcedEntity(cartId)
      .method(ShoppingCartEntity::checkout)
      .invoke();
    return HttpResponses.ok();
  }
  // tag::class[]
}
// end::class[]
