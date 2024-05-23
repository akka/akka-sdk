package store.product.api;

import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import store.product.domain.Money;
import store.product.domain.Product;
import store.product.domain.ProductEvent;

import static store.product.domain.ProductEvent.*;

@TypeId("product")
public class ProductEntity extends EventSourcedEntity<Product, ProductEvent> {


    public Effect<Product> get() {
        return effects().reply(currentState());
    }

    public Effect<String> create(Product product) {
        return effects()
            .emitEvent(new ProductCreated(product.name(), product.price()))
            .thenReply(__ -> "OK");
    }

    @EventHandler
    public Product onEvent(ProductCreated created) {
        return new Product(created.name(), created.price());
    }

    public Effect<String> changeName(String newName) {
        return effects().emitEvent(new ProductNameChanged(newName)).thenReply(__ -> "OK");
    }

    @EventHandler
    public Product onEvent(ProductNameChanged productNameChanged) {
        return currentState().withName(productNameChanged.newName());
    }

    public Effect<String> changePrice(Money newPrice) {
        return effects().emitEvent(new ProductPriceChanged(newPrice)).thenReply(__ -> "OK");
    }

    @EventHandler
    public Product onEvent(ProductPriceChanged productPriceChanged) {
        return currentState().withPrice(productPriceChanged.newPrice());
    }
}
