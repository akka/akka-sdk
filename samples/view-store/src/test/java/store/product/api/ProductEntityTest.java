package store.product.api;

import akka.Done;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;
import store.customer.application.CustomerEntity;
import store.product.application.ProductEntity;
import store.product.domain.Money;
import store.product.domain.Product;
import store.product.domain.ProductEvent;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProductEntityTest {

  @Test
  public void testProductNameChange() {

    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
      EventSourcedTestKit.of(ProductEntity::new);

    {
      String name = "Super Duper Thingamajig";
      Product product = new Product(name, new Money("USD", 123, 45));
      EventSourcedResult<Done> result = testKit.method(ProductEntity::create).invoke(product);
      assertEquals(done(), result.getReply());
      assertEquals(name, testKit.getState().name());
      result.getNextEventOfType(ProductEvent.ProductCreated.class);
    }

    {
      String newName = "Thing Supreme";
      EventSourcedResult<Done> result = testKit.method(ProductEntity::changeName).invoke(newName);
      assertEquals(done(), result.getReply());
      assertEquals(newName, testKit.getState().name());
      result.getNextEventOfType(ProductEvent.ProductNameChanged.class);
    }
  }

  @Test
  public void testProductPriceChange() {

    EventSourcedTestKit<Product, ProductEvent, ProductEntity> testKit =
      EventSourcedTestKit.of(ProductEntity::new);

    {
      Money price = new Money("USD", 123, 45);
      Product product = new Product("Super Duper Thingamajig", price);
      EventSourcedResult<Done> result = testKit.method(ProductEntity::create).invoke(product);
      assertEquals(done(), result.getReply());
      assertEquals(price.currency(), testKit.getState().price().currency());
      assertEquals(price.units(), testKit.getState().price().units());
      assertEquals(price.cents(), testKit.getState().price().cents());
      result.getNextEventOfType(ProductEvent.ProductCreated.class);
    }

    {
      Money newPrice = new Money("USD", 56, 78);
      EventSourcedResult<Done> result = testKit.method(ProductEntity::changePrice).invoke(newPrice);
      assertEquals(done(), result.getReply());
      assertEquals(newPrice.currency(), testKit.getState().price().currency());
      assertEquals(newPrice.units(), testKit.getState().price().units());
      assertEquals(newPrice.cents(), testKit.getState().price().cents());
      result.getNextEventOfType(ProductEvent.ProductPriceChanged.class);
    }
  }
}
