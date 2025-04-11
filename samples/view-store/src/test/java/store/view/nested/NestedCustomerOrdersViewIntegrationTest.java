package store.view.nested;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import store.order.view.nested.CustomerOrder;
import store.order.view.nested.NestedCustomerOrders;
import store.order.view.nested.NestedCustomerOrdersView;
import store.view.StoreViewIntegrationTest;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NestedCustomerOrdersViewIntegrationTest extends StoreViewIntegrationTest {


  @Test
  public void getCustomerOrders() {
    createProduct("P123", "Super Duper Thingamajig", "USD", 123, 45);
    createProduct("P987", "Awesome Whatchamacallit", "NZD", 987, 65);
    createCustomer("C001", "someone@example.com", "Some Customer", "123 Some Street", "Some City");
    createOrder("O1234", "P123", "C001", 42);
    createOrder("O5678", "P987", "C001", 7);

    {
      NestedCustomerOrders customerOrders =
        awaitCustomerOrders("C001", customer -> customer.orders().size() >= 2);

      assertEquals(2, customerOrders.orders().size());

      assertEquals("C001", customerOrders.customerId());
      assertEquals("someone@example.com", customerOrders.email());
      assertEquals("Some Customer", customerOrders.name());
      assertEquals("123 Some Street", customerOrders.address().street());
      assertEquals("Some City", customerOrders.address().city());

      CustomerOrder customerOrder1 = customerOrders.orders().get(0);
      assertEquals("C001", customerOrder1.customerId());
      assertEquals("O1234", customerOrder1.orderId());
      assertEquals("P123", customerOrder1.productId());
      assertEquals("Super Duper Thingamajig", customerOrder1.productName());
      assertEquals("USD", customerOrder1.price().currency());
      assertEquals(123, customerOrder1.price().units());
      assertEquals(45, customerOrder1.price().cents());
      assertEquals(42, customerOrder1.quantity());

      CustomerOrder customerOrder2 = customerOrders.orders().get(1);
      assertEquals("C001", customerOrder2.customerId());
      assertEquals("O5678", customerOrder2.orderId());
      assertEquals("P987", customerOrder2.productId());
      assertEquals("Awesome Whatchamacallit", customerOrder2.productName());
      assertEquals("NZD", customerOrder2.price().currency());
      assertEquals(987, customerOrder2.price().units());
      assertEquals(65, customerOrder2.price().cents());
      assertEquals(7, customerOrder2.quantity());
    }

    String newCustomerName = "Some Name";
    changeCustomerName("C001", newCustomerName);

    {
      NestedCustomerOrders customerOrders =
        awaitCustomerOrders("C001", customer -> newCustomerName.equals(customer.name()));

      assertEquals("Some Name", customerOrders.name());
    }

    String newProductName = "Thing Supreme";
    changeProductName("P123", newProductName);

    {
      NestedCustomerOrders customerOrders =
        awaitCustomerOrders(
          "C001", customer -> newProductName.equals(customer.orders().get(0).productName()));

      CustomerOrder customerOrder1 = customerOrders.orders().get(0);
      assertEquals("O1234", customerOrder1.orderId());
      assertEquals("Thing Supreme", customerOrder1.productName());

      CustomerOrder customerOrder2 = customerOrders.orders().get(1);
      assertEquals("O5678", customerOrder2.orderId());
      assertEquals("Awesome Whatchamacallit", customerOrder2.productName());
    }
  }

  private NestedCustomerOrders getCustomerOrders(String customerId) {
    return 
      componentClient.forView()
        .method(NestedCustomerOrdersView::get)
        .invoke(customerId);
  }

  private NestedCustomerOrders awaitCustomerOrders(
    String customerId, Function<NestedCustomerOrders, Boolean> condition) {
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(() -> condition.apply(getCustomerOrders(customerId)));
    return getCustomerOrders(customerId);
  }
}
