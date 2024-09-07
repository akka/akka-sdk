package customer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import customer.application.CustomerByEmailView;
import customer.application.CustomerByNameView;
import customer.application.CustomerEntity;
import customer.domain.Address;
import customer.domain.Customer;
import customer.domain.CustomersList;

import java.util.concurrent.CompletionStage;

@HttpEndpoint("/customer")
public class CustomerEndpoint {

  record CreateCustomerRequest(String id, String email, String name, Address address){
  }

  private final ComponentClient componentClient;

  public CustomerEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public CompletionStage<HttpResponse> create(CreateCustomerRequest createCustomerRequest) {
    return componentClient.forEventSourcedEntity(createCustomerRequest.id())
      .method(CustomerEntity::create)
      .invokeAsync(new Customer(createCustomerRequest.email, createCustomerRequest.name, createCustomerRequest.address))
      .thenApply(__ -> HttpResponses.created());
  }

  @Patch("/{customerId}/name/{newName}")
  public CompletionStage<HttpResponse> changeName(String customerId, String newName) {
    return componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::changeName)
      .invokeAsync(newName)
      .thenApply(__ -> HttpResponses.ok());
  }

  @Patch("/{customerId}/address")
  public CompletionStage<HttpResponse> changeAddress(String customerId, Address newAddress) {
    return componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::changeAddress)
      .invokeAsync(newAddress)
      .thenApply(__ -> HttpResponses.ok());
  }

  @Get("/{customerId}")
  public CompletionStage<Customer> changeAddress(String customerId) {
    return componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::getCustomer)
      .invokeAsync();
  }

  @Get("/by-name/{name}")
  public CompletionStage<CustomersList> userByName(String name) {
    return componentClient.forView()
      .method(CustomerByNameView::getCustomers)
      .invokeAsync(name);
  }

  @Get("/by-email/{name}")
  public CompletionStage<CustomersList> userByEmail(String email) {
    return componentClient.forView()
      .method(CustomerByEmailView::getCustomers)
      .invokeAsync(email);
  }
}
