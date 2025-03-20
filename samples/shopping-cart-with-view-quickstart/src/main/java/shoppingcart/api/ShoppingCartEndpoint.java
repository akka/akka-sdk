package shoppingcart.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.NoEntryFoundException;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.application.ShoppingCartView;

// tag::top[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)
@HttpEndpoint("/carts")
public class ShoppingCartEndpoint extends AbstractHttpEndpoint {
  // end::top[]

  private final ComponentClient componentClient;

  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEndpoint.class);

  // tag::newpubapi[]
  public record LineItemRequest(String productId, String name, int quantity, String description) {
  }
  // end::newpubapi[]

  public ShoppingCartEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::get[]
  @Get("/{cartId}")
  public CompletionStage<ShoppingCartView.Cart> get(String cartId) {
    logger.info("Get cart id={}", cartId);

    var userId = requestContext().getJwtClaims().subject().get();

    try {
      return componentClient.forView()
          .method(ShoppingCartView::getCart) // <1>
          .invokeAsync(cartId)
          .thenCompose(
              cart -> (cart.userId().trim().equals(userId))
                  ? CompletableFuture.completedStage(cart)
                  : CompletableFuture.failedStage(HttpException.notFound()));
    } catch (NoEntryFoundException nef) {
      throw HttpException.notFound();
    }
  }
  // end::get[]

  // tag::getmy[]
  @Get("/my")
  public CompletionStage<ShoppingCartView.Cart> getByUser() {
    var userId = requestContext().getJwtClaims().subject().get();

    logger.info("Get cart userId={}", userId);

    try {
      return componentClient.forView()
          .method(ShoppingCartView::getUserCart) // <1>
          .invokeAsync(userId);
    } catch (NoEntryFoundException nef) {
      throw HttpException.notFound();
    }
  }
  // end::getmy[]

  @Put("/{cartId}/item")
  public CompletionStage<HttpResponse> addItem(String cartId, LineItemRequest item) {
    logger.info("Adding item to cart id={} item={}", cartId, item);

    var userId = requestContext().getJwtClaims().subject().get();

    return componentClient.forEventSourcedEntity(cartId)
        .method(ShoppingCartEntity::addItem)
        .invokeAsync(new ShoppingCartEntity.AddLineItemCommand(
            userId,
            item.productId(),
            item.name(),
            item.quantity(),
            item.description()))
        .thenApply(__ -> HttpResponses.ok());
  }

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

    var userId = requestContext().getJwtClaims().subject().get();
    return componentClient.forEventSourcedEntity(cartId)
        .method(ShoppingCartEntity::checkout)
        .invokeAsync()
        .thenApply(__ -> HttpResponses.ok());
  }

}
