package store.customer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import store.customer.application.CustomerEntity;
import store.customer.domain.Customer;

import static akka.javasdk.http.HttpResponses.created;

@HttpEndpoint("/customers")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class CustomerEndpoint {

  private final ComponentClient componentClient;

  public CustomerEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/{customerId}")
  public HttpResponse create(String customerId, Customer customer) {
    componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::create)
      .invoke(customer);
    return created();
  }

  @Get("/{customerId}")
  public Customer get(String customerId) {
    return componentClient.forEventSourcedEntity(customerId)
      .method(CustomerEntity::get)
      .invoke();
  }
}
