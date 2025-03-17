package shoppingcart.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.application.ShoppingCartView;
import shoppingcart.domain.ShoppingCartState;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/carts")
public class ShoppingCartEndpoint {

  private final ComponentClient componentClient;

  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEndpoint.class);

  public record LineItemRequest(String productId, String name, int quantity, String description) {
  }

  public record CartResponse(String userId, List<CartResponse.LineItem> items, boolean checkedout) {

    public static CartResponse fromCartView(ShoppingCartView.Cart cart) {
      return new CartResponse(cart.userId(),
          cart.items().stream().map(
              i -> new CartResponse.LineItem(i.itemId(), i.name(), i.quantity(), i.description()))
              .collect(Collectors.toList()),
          cart.checkedout());
    }

    public record LineItem(String itemId, String name, int quantity, String description) {
    }

  }

  public ShoppingCartEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/{userId}")
  public CompletionStage<CartResponse> get(String userId) {
    logger.info("Get cart id={}", userId);
    return componentClient.forView()
        .method(ShoppingCartView::getCartForUser)
        .invokeAsync(userId)
        .thenApply(cr -> CartResponse.fromCartView(cr));
  }

  @Put("/{userId}/item")
  public CompletionStage<HttpResponse> addItem(String userId, LineItemRequest item) {
    logger.info("Adding item to cart id={} item={}", userId, item);
    return componentClient.forEventSourcedEntity(userId)
        .method(ShoppingCartEntity::addItem)
        .invokeAsync(new ShoppingCartEntity.AddLineItemCommand(item.productId(),
            item.name(),
            item.quantity(),
            item.description()))
        .thenApply(__ -> HttpResponses.ok());
  }

  @Delete("/{userId}/item/{productId}")
  public CompletionStage<HttpResponse> removeItem(String userId, String productId) {
    logger.info("Removing item from cart id={} item={}", userId, productId);
    return componentClient.forEventSourcedEntity(userId)
        .method(ShoppingCartEntity::removeItem)
        .invokeAsync(productId)
        .thenApply(__ -> HttpResponses.ok());
  }

  @Post("/{userId}/checkout")
  public CompletionStage<HttpResponse> checkout(String userId) {
    logger.info("Checkout cart id={}", userId);
    return componentClient.forEventSourcedEntity(userId)
        .method(ShoppingCartEntity::checkout)
        .invokeAsync()
        .thenApply(__ -> HttpResponses.ok());
  }

}
