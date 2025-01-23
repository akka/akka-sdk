package customer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import customer.application.CustomerByEmailView;
import customer.application.CustomerByNameView;
import customer.application.CustomerEntity;
import customer.domain.Address;
import customer.domain.Customer;
import customer.domain.CustomersList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
// Note: Called in customer-registry-subscriber integration test so must be allowed also from the other service or test will fail
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/customer")
public class CustomerEndpoint {

  private static final Logger log = LoggerFactory.getLogger(CustomerEndpoint.class);

  record CreateCustomerRequest(String email, String name, Address address){ }

  private final ComponentClient componentClient;

  public CustomerEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/{customerId}")
  public CompletionStage<HttpResponse> create(String customerId, CreateCustomerRequest createCustomerRequest) {
    log.info("Request to create customer: {}", createCustomerRequest);
    return componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::create)
      .invokeAsync(new Customer(createCustomerRequest.email, createCustomerRequest.name, createCustomerRequest.address))
      .thenApply(__ -> HttpResponses.created());
  }

  @Get("/{customerId}")
  public CompletionStage<Customer> getCustomer(String customerId) {
    return componentClient.forEventSourcedEntity(customerId)
        .method(CustomerEntity::getCustomer)
        .invokeAsync()
        .exceptionally(ex -> {
          if (ex.getMessage().contains("No customer found for id")) throw HttpException.notFound();
          else throw new RuntimeException(ex);
        });
  }

  @Patch("/{customerId}/name/{newName}")
  public CompletionStage<HttpResponse> changeName(String customerId, String newName) {
    log.info("Request to change customer [{}] name: {}",customerId, newName);
    return componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::changeName)
      .invokeAsync(newName)
      .thenApply(__ -> HttpResponses.ok());
  }

  @Patch("/{customerId}/address")
  public CompletionStage<HttpResponse> changeAddress(String customerId, Address newAddress) {
    log.info("Request to change customer [{}] address: {}",customerId, newAddress);
    return componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::changeAddress)
      .invokeAsync(newAddress)
      .thenApply(__ -> HttpResponses.ok());
  }

  @Get("/by-name/{name}")
  public CompletionStage<CustomersList> customerByName(String name) {
    return componentClient.forView()
      .method(CustomerByNameView::getCustomers)
      .invokeAsync(name);
  }

  @Get("/by-email/{email}")
  public CompletionStage<CustomersList> customerByEmail(String email) {
    return componentClient.forView()
      .method(CustomerByEmailView::getCustomers)
      .invokeAsync(email);
  }
}
