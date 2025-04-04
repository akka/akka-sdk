package shoppingcart.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import shoppingcart.application.ShoppingCartEntity;
import shoppingcart.application.ShoppingCartView;
import shoppingcart.application.UserEntity;

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
    public ShoppingCartView.Cart get(String cartId) {
        logger.info("Get cart id={}", cartId);

        var userId = requestContext().getJwtClaims().subject().get();

        var cart=  componentClient.forView()
          .method(ShoppingCartView::getCart) // <1>
          .invoke(cartId);

        if (cart.userId().trim().equals(userId)) {
          return cart;
        } else {
          throw HttpException.error(StatusCodes.NOT_FOUND, "no such cart");
        }
    }
    // end::get[]

    // tag::getmy[]
    @Get("/my")
    public ShoppingCartView.Cart getByUser() {
        var userId = requestContext().getJwtClaims().subject().get();

        logger.info("Get cart userId={}", userId);

        var result = componentClient.forView()
                .method(ShoppingCartView::getUserCart) // <1>
                .invoke(userId);

        return result.orElseThrow(() -> HttpException.error(StatusCodes.NOT_FOUND, "no such cart"));
    }
    // end::getmy[]

    @Put("/my/item")
    public HttpResponse addItem(LineItemRequest item) {
        logger.info("Adding item to cart item={}", item);

        var userId = requestContext().getJwtClaims().subject().get();

        var cartId = componentClient.forEventSourcedEntity(userId)
                .method(UserEntity::currentCartId)
                .invoke();

        componentClient.forEventSourcedEntity(cartId)
            .method(ShoppingCartEntity::addItem)
            .invoke(new ShoppingCartEntity.AddLineItemCommand(
                    userId,
                    item.productId(),
                    item.name(),
                    item.quantity(),
                    item.description()));

        return HttpResponses.ok();
    }

    @Delete("/my/item/{productId}")
    public HttpResponse removeItem(String productId) {
        logger.info("Removing item from item={}", productId);

        var userId = requestContext().getJwtClaims().subject().get();

        var cartId = componentClient.forEventSourcedEntity(userId)
                .method(UserEntity::currentCartId)
                .invoke();
        componentClient.forEventSourcedEntity(cartId)
                        .method(ShoppingCartEntity::removeItem)
                        .invoke(productId);
        return HttpResponses.ok();
    }

    @Post("/my/checkout")
    public HttpResponse checkout() {
        logger.info("Checkout cart");

        var userId = requestContext().getJwtClaims().subject().get();

        var cartId = componentClient.forEventSourcedEntity(userId)
                .method(UserEntity::currentCartId)
                .invoke();

        componentClient.forEventSourcedEntity(cartId)
                        .method(ShoppingCartEntity::checkout)
                        .invoke(userId);

        return HttpResponses.ok();
    }

}
