package customer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import customer.application.*;
import customer.domain.Address;
import customer.domain.Customer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@HttpEndpoint("/customer")
public class CustomerEndpoint {

  private final ComponentClient componentClient;

  public CustomerEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record CustomerRequest(String id, String name, String email, String city, String street) {}

  @Post("/{customerId}/create")
  public CompletionStage<HttpResponse> create(String customerId, CustomerRequest request) {
    Customer c = new Customer(customerId, request.email(), request.name(), new Address(request.street(), request.city()));

    return componentClient.forKeyValueEntity(customerId)
        .method(CustomerEntity::create)
        .invokeAsync(c)
        .thenApply(__ -> HttpResponses.created());
  }

  @Get("/{customerId}")
  public  CompletionStage<CustomerRequest> get(String customerId) {
    return componentClient.forKeyValueEntity(customerId)
        .method(CustomerEntity::getCustomer)
        .invokeAsync()
        .thenApply(c -> new CustomerRequest(customerId, c.name(), c.email(), c.address().city(), c.address().street()));
  }

  @Get("/by-name/{name}")
  public CompletionStage<CustomerList> getByName(String name) {
    return componentClient.forView()
        .method(CustomersResponseByName::getCustomers)
        .invokeAsync(name);
  }

  public record ByNameSummary(String name) {}
  @Post("/by-name-summary")
  public CompletionStage<CustomerSummaryByName.CustomerSummary> getSummaryByName(ByNameSummary req) {
    return componentClient.forView()
        .method(CustomerSummaryByName::getCustomer)
        .invokeAsync(req.name());
  }

  public record ByCityRequest(List<String> cities) {}

  @Post("/by-city")
  public CompletionStage<CustomerList> getByCity(ByCityRequest req) {
    return componentClient.forView()
        .method(CustomersByCity::getCustomers)
        .invokeAsync(req.cities());
  }

  @Delete("/{customerId}")
  public CompletionStage<HttpResponse> delete(String customerId) {
    return componentClient.forKeyValueEntity(customerId)
        .method(CustomerEntity::delete)
        .invokeAsync()
        .thenApply(__ -> HttpResponses.noContent());
  }

}
