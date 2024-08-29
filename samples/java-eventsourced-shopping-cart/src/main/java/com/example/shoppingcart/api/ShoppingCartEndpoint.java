package com.example.shoppingcart.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.http.*;
import akka.javasdk.client.ComponentClient;
import com.example.shoppingcart.application.ShoppingCartEntity;
import com.example.shoppingcart.domain.ShoppingCart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@Endpoint("/shopping-cart")
public class ShoppingCartEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(ShoppingCartEndpoint.class);

    private final ComponentClient componentClient;

    public ShoppingCartEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Get("/{id}")
    public CompletionStage<ShoppingCart> getCart(String id){
        logger.info("Getting cart [{}].", id);
       return componentClient.forEventSourcedEntity(id)
               .method(ShoppingCartEntity::getCart).invokeAsync();
    }

    @Post("/{id}/item")
    public CompletionStage<String> addItem(String id, ShoppingCart.LineItem item){
        logger.info("Adding item [{}] to cart [{}].", item, id);
        return componentClient.forEventSourcedEntity(id)
                .method(ShoppingCartEntity::addItem).invokeAsync(item)
                .thenApply(done -> String.format("%s added", item));
    }

    @Delete("/{id}/item/{productId}")
    public CompletionStage<String> removeItem(String id, String productId){
        logger.info("Removing item with product id [{}] from cart [{}].", productId, id);
        return componentClient.forEventSourcedEntity(id)
                .method(ShoppingCartEntity::removeItem).invokeAsync(productId).thenApply(done -> "OK");
    }

    @Post("/{id}/checkout")
    public CompletionStage<String> checkout(String id){
        logger.info("Checking out cart [{}].", id);
        return componentClient.forEventSourcedEntity(id)
                .method(ShoppingCartEntity::checkout).invokeAsync().thenApply(done -> "OK");
    }

}
