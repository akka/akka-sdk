package customer.api;

import akka.javasdk.testkit.TestKitSupport;
import customer.api.proto.CustomerGrpcEndpointClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// tag::sample-it[]
public class CustomerGrpcIntegrationTest extends TestKitSupport {

  @Test
  public void createCustomerCart() {

    var client = getGrpcEndpointClient(CustomerGrpcEndpointClient.class);

    var customerRequest = customer.api.proto.CreateCustomerRequest.newBuilder()
        .setCustomerId("customer-abc")
        .setCustomer(customer.api.proto.Customer.newBuilder()
            .setEmail("abc@email.com")
            .setName("John Doe")
            .build())
        .build();

    client.createCustomer(customerRequest);

    var getCustomer =
        client.getCustomer(customer.api.proto.GetCustomerRequest.newBuilder()
            .setCustomerId("customer-abc")
            .build());
    Assertions.assertEquals("John Doe", getCustomer.getName());
  }
}
// end::sample-it[]
