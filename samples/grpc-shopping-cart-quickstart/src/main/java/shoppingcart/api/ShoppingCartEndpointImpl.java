// tag::top[]
package shoppingcart.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shoppingcart.api.proto.ShoppingCartEndpoint;
import shoppingcart.api.proto.ShoppingCartEndpointOuterClass;
import shoppingcart.api.proto.ShoppingCartEndpointOuterClass.*;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.domain.ShoppingCart;

import java.util.concurrent.CompletionStage;

// end::top[]

// tag::class[]

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
// tag::endpoint-component-interaction[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@GrpcEndpoint // <1>
public class ShoppingCartEndpointImpl implements ShoppingCartEndpoint {

  private final ComponentClient componentClient;

  private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEndpointImpl.class);

  public ShoppingCartEndpointImpl(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // end::class[]

  // tag::get[]

  @Override
  public CompletionStage<ShoppingCartEndpointOuterClass.ShoppingCart> getCart(GetCartRequest in) {
    logger.info("Get cart id={}", in.getCartId());
    return componentClient.forEventSourcedEntity(in.getCartId())
        .method(ShoppingCartEntity::getCart)
        .invokeAsync()
        .thenApply(ShoppingCartEndpointImpl::convertToProto);
  }

  private static ShoppingCartEndpointOuterClass.ShoppingCart convertToProto(ShoppingCart domainCart) {
    var protoCartBuilder = ShoppingCartEndpointOuterClass.ShoppingCart.newBuilder();
    protoCartBuilder.setCartId(domainCart.cartId());
    protoCartBuilder.setCheckedOut(domainCart.checkedOut());
    domainCart.items().forEach(item -> {
      var protoItem = ShoppingCartEndpointOuterClass.LineItem.newBuilder()
          .setProductId(item.productId())
          .setQuantity(item.quantity())
          .build();
      protoCartBuilder.addItems(protoItem);
    });
    return protoCartBuilder.build();
  }
  // end::get[]

  // tag::addItem[]
  @Override
  public CompletionStage<AddItemResponse> addItem(AddItemRequest in) {
    logger.info("Adding item to cart id={} item={}", in.getCartId(), in.getItem());
    return componentClient.forEventSourcedEntity(in.getCartId())
        .method(ShoppingCartEntity::addItem)
        .invokeAsync(convertToDomain(in.getItem()))
        .thenApply(__ -> AddItemResponse.newBuilder().setSuccess(true).build());
  }

  private static ShoppingCart.LineItem convertToDomain(ShoppingCartEndpointOuterClass.LineItem protoItem) {
    return new ShoppingCart.LineItem(protoItem.getProductId(), protoItem.getName(), protoItem.getQuantity());
  }
  // end::endpoint-component-interaction[]

  // end::addItem[]

  @Override
  public CompletionStage<RemoveItemResponse> removeItem(RemoveItemRequest in) {
    logger.info("Removing item from cart id={} item={}", in.getCartId(), in.getProductId());
    return componentClient.forEventSourcedEntity(in.getCartId())
        .method(ShoppingCartEntity::removeItem)
        .invokeAsync(in.getProductId())
        .thenApply(__ -> RemoveItemResponse.newBuilder().setSuccess(true).build());
  }

  @Override
  public CompletionStage<CheckoutResponse> checkout(CheckoutRequest in) {
    logger.info("Checkout cart id={}", in.getCartId());
    return componentClient.forEventSourcedEntity(in.getCartId())
        .method(ShoppingCartEntity::checkout)
        .invokeAsync()
        .thenApply(__ -> CheckoutResponse.newBuilder().setSuccess(true).build());
  }
  // tag::class[]
}
// end::class[]
