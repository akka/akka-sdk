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
import customer.application.CustomersByEmailView;
import customer.application.CustomersByNameView;
import customer.application.CustomerEntity;
import customer.domain.Address;
import customer.domain.Customer;
import customer.domain.CustomerEntries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  public HttpResponse create(String customerId, CreateCustomerRequest createCustomerRequest) {
    log.info("Request to create customer: {}", createCustomerRequest);
    componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::create)
      .invoke(new Customer(createCustomerRequest.email, createCustomerRequest.name, createCustomerRequest.address));

    return HttpResponses.created();
  }

  @Get("/{customerId}")
  public Customer getCustomer(String customerId) {
    try {
      return componentClient.forEventSourcedEntity(customerId)
          .method(CustomerEntity::getCustomer)
          .invoke();
    } catch (Exception ex) {
      if (ex.getMessage().contains("No customer found for id")) throw HttpException.notFound();
      else throw new RuntimeException(ex);
    }
  }

  @Patch("/{customerId}/name/{newName}")
  public HttpResponse changeName(String customerId, String newName) {
    log.info("Request to change customer [{}] name: {}",customerId, newName);
    componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::changeName)
      .invoke(newName);
    return HttpResponses.ok();
  }

  @Patch("/{customerId}/address")
  public HttpResponse changeAddress(String customerId, Address newAddress) {
    log.info("Request to change customer [{}] address: {}",customerId, newAddress);
    componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::changeAddress)
      .invoke(newAddress);
    return HttpResponses.ok();
  }

  @Get("/by-name/{name}")
  public CustomerEntries customerByName(String name) {
    return componentClient.forView()
      .method(CustomersByNameView::getCustomers)
      .invoke(name);
  }

  @Get("/by-email/{email}")
  public CustomerEntries customerByEmail(String email) {
    return componentClient.forView()
      .method(CustomersByEmailView::getCustomers)
      .invoke(email);
  }
}
