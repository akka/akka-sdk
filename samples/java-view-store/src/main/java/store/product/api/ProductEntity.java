package store.product.api;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity;
import store.product.domain.Money;
import store.product.domain.Product;
import store.product.domain.ProductEvent;

import static store.product.domain.ProductEvent.ProductCreated;
import static store.product.domain.ProductEvent.ProductNameChanged;
import static store.product.domain.ProductEvent.ProductPriceChanged;

@ComponentId("product")
public class ProductEntity extends EventSourcedEntity<Product, ProductEvent> {


  public ReadOnlyEffect<Product> get() {
    return effects().reply(currentState());
  }

  public Effect<String> create(Product product) {
    return effects()
      .persist(new ProductCreated(product.name(), product.price()))
      .thenReply(__ -> "OK");
  }

  public Effect<String> changeName(String newName) {
    return effects().persist(new ProductNameChanged(newName)).thenReply(__ -> "OK");
  }

  public Effect<String> changePrice(Money newPrice) {
    return effects().persist(new ProductPriceChanged(newPrice)).thenReply(__ -> "OK");
  }

  @Override
  public Product applyEvent(ProductEvent event) {
    return switch (event) {
      case ProductCreated evt -> new Product(evt.name(), evt.price());
      case ProductNameChanged evt -> currentState().withName(evt.newName());
      case ProductPriceChanged evt -> currentState().withPrice(evt.newPrice());
    };
  }

}
