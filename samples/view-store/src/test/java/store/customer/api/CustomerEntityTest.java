package store.customer.api;

import akka.Done;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;
import store.customer.application.CustomerEntity;
import store.customer.domain.Address;
import store.customer.domain.Customer;
import store.customer.domain.CustomerEvent;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static store.customer.domain.CustomerEvent.CustomerAddressChanged;
import static store.customer.domain.CustomerEvent.CustomerCreated;
import static store.customer.domain.CustomerEvent.CustomerNameChanged;

public class CustomerEntityTest {

  @Test
  public void testCustomerNameChange() {

    EventSourcedTestKit<Customer, CustomerEvent, CustomerEntity> testKit =
      EventSourcedTestKit.of(CustomerEntity::new);

    {
      String name = "Some Customer";
      Address address = new Address("123 Some Street", "Some City");
      Customer customer = new Customer("someone@example.com", name, address);
      EventSourcedResult<Done> result = testKit.method(CustomerEntity::create).invoke(customer);
      assertEquals(done(), result.getReply());
      assertEquals(name, testKit.getState().name());
      result.getNextEventOfType(CustomerCreated.class);
    }

    {
      String newName = "Some Name";
      EventSourcedResult<Done> result = testKit.method(CustomerEntity::changeName).invoke(newName);
      assertEquals(done(), result.getReply());
      assertEquals(newName, testKit.getState().name());
      result.getNextEventOfType(CustomerNameChanged.class);
    }
  }

  @Test
  public void testCustomerAddressChange() {

    EventSourcedTestKit<Customer, CustomerEvent, CustomerEntity> testKit =
      EventSourcedTestKit.of(CustomerEntity::new);

    {
      Address address = new Address("123 Some Street", "Some City");
      Customer customer = new Customer("someone@example.com", "Some Customer", address);
      EventSourcedResult<Done> result = testKit.method(CustomerEntity::create).invoke(customer);
      assertEquals(done(), result.getReply());
      assertEquals(address.street(), testKit.getState().address().street());
      assertEquals(address.city(), testKit.getState().address().city());
      result.getNextEventOfType(CustomerCreated.class);
    }

    {
      Address newAddress = new Address("42 Some Road", "Some Other City");
      EventSourcedResult<Done> result = testKit.method(CustomerEntity::changeAddress).invoke(newAddress);
      assertEquals(done(), result.getReply());
      assertEquals(newAddress.street(), testKit.getState().address().street());
      assertEquals(newAddress.city(), testKit.getState().address().city());
      result.getNextEventOfType(CustomerAddressChanged.class);
    }
  }
}
