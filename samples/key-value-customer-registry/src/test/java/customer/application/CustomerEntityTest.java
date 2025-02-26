package customer.application;

import akka.Done;
import customer.domain.Address;
import customer.domain.Customer;
import akka.javasdk.testkit.KeyValueEntityResult;
import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomerEntityTest {

  private Address address = new Address("Acme Street", "Acme City");
  private Customer customer = new Customer("info@acme.com", "Acme Inc.", address);

  @Test
  public void testCustomerNameChange() {

    KeyValueEntityTestKit<Customer, CustomerEntity> testKit = KeyValueEntityTestKit.of(CustomerEntity::new);
    {
      KeyValueEntityResult<Done> result = testKit.method(CustomerEntity::create).invoke(customer);
      assertEquals(Done.getInstance(), result.getReply());
    }

    {
      KeyValueEntityResult<Done> result = testKit.method(CustomerEntity::changeName).invoke("FooBar");
      assertEquals(Done.getInstance(), result.getReply());
      assertEquals("FooBar", testKit.getState().name());
    }

  }

  @Test
  public void testCustomerAddressChange() {

    KeyValueEntityTestKit<Customer, CustomerEntity> testKit = KeyValueEntityTestKit.of(CustomerEntity::new);
    {
      KeyValueEntityResult<Done> result = testKit.method(CustomerEntity::create).invoke(customer);
      assertEquals(Done.getInstance(), result.getReply());
    }

    {
      Address newAddress = new Address("Sesame Street", "Sesame City");
      KeyValueEntityResult<Done> result = testKit.method(CustomerEntity::changeAddress).invoke(newAddress);
      assertEquals(Done.getInstance(), result.getReply());
      assertEquals("Sesame Street", testKit.getState().address().street());
      assertEquals("Sesame City", testKit.getState().address().city());
    }

  }
}
