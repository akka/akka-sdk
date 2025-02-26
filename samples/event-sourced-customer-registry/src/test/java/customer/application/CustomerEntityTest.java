package customer.application;

import akka.Done;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import customer.domain.Address;
import customer.domain.Customer;
import customer.domain.CustomerEvent;
import org.junit.jupiter.api.Test;

import static akka.Done.done;
import static customer.domain.CustomerEvent.AddressChanged;
import static customer.domain.CustomerEvent.CustomerCreated;
import static customer.domain.CustomerEvent.NameChanged;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomerEntityTest {

  private Address address = new Address("Acme Street", "Acme City");
  private Customer customer = new Customer("info@acme.com", "Acme Inc.", address);


  @Test
  public void testCustomerNameChange() {

    EventSourcedTestKit<Customer, CustomerEvent, CustomerEntity> testKit = EventSourcedTestKit.of(CustomerEntity::new);
    {
      EventSourcedResult<Done> result = testKit.method(CustomerEntity::create).invoke(customer);
      assertEquals(done(), result.getReply());
      result.getNextEventOfType(CustomerCreated.class);
    }
    {
      EventSourcedResult<Done> result = testKit.method(CustomerEntity::changeName).invoke("FooBar");
      assertEquals(done(), result.getReply());
      assertEquals("FooBar", testKit.getState().name());
      result.getNextEventOfType(NameChanged.class);
    }
  }

  @Test
  public void testCustomerAddressChange() {

    EventSourcedTestKit<Customer, CustomerEvent, CustomerEntity> testKit = EventSourcedTestKit.of(CustomerEntity::new);
    {
      EventSourcedResult<Done> result = testKit.method(CustomerEntity::create).invoke(customer);
      assertEquals(done(), result.getReply());
      result.getNextEventOfType(CustomerCreated.class);
    }

    {
      Address newAddress = new Address("Sesame Street", "Sesame City");
      EventSourcedResult<Done> result = testKit.method(CustomerEntity::changeAddress).invoke(newAddress);
      assertEquals(done(), result.getReply());
      assertEquals("Sesame Street", testKit.getState().address().street());
      assertEquals("Sesame City", testKit.getState().address().city());
      result.getNextEventOfType(AddressChanged.class);
    }

  }
}
