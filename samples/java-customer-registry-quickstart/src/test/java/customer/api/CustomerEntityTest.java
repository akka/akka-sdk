package customer.api;

import akka.platform.javasdk.testkit.ValueEntityResult;
import akka.platform.javasdk.valueentity.ValueEntity;
import akka.platform.javasdk.testkit.ValueEntityTestKit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import customer.domain.Address;
import customer.domain.Customer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CustomerEntityTest {

  private Address address = new Address("Acme Street", "Acme City");
  private Customer customer = new Customer("info@acme.com", "Acme Inc.", address);

  @Test
  public void testCustomerNameChange() {

    ValueEntityTestKit<Customer, CustomerEntity> testKit = ValueEntityTestKit.of(CustomerEntity::new);
    {
      ValueEntityResult<CustomerEntity.Ok> result = testKit.call(e -> e.create(customer));
      assertEquals(CustomerEntity.Ok.instance, result.getReply());
    }

    {
      ValueEntityResult<CustomerEntity.Ok> result = testKit.call(e -> e.changeName("FooBar"));
      assertEquals(CustomerEntity.Ok.instance, result.getReply());
      assertEquals("FooBar", testKit.getState().name());
    }

  }

  @Test
  public void testCustomerAddressChange() {

    ValueEntityTestKit<Customer, CustomerEntity> testKit = ValueEntityTestKit.of(CustomerEntity::new);
    {
      ValueEntityResult<CustomerEntity.Ok> result = testKit.call(e -> e.create(customer));
      assertEquals(CustomerEntity.Ok.instance, result.getReply());
    }

    {
      Address newAddress = new Address("Sesame Street", "Sesame City");
      ValueEntityResult<CustomerEntity.Ok> result = testKit.call(e -> e.changeAddress(newAddress));
      assertEquals(CustomerEntity.Ok.instance, result.getReply());
      assertEquals("Sesame Street", testKit.getState().address().street());
      assertEquals("Sesame City", testKit.getState().address().city());
    }

  }
}
