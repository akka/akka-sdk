package customer.actions;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.ActionId;
import akka.platform.javasdk.http.HttpClient;
import akka.platform.javasdk.http.HttpClientProvider;

import akka.platform.javasdk.http.StrictResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ActionId("customer-registry")
public class CustomerRegistryAction extends Action {

  private Logger log = LoggerFactory.getLogger(getClass());
  private final HttpClient httpClient;

  public record Address(String street, String city) {
  }

  public record Customer(String email, String name, Address address) {
  }

  public record Confirm(String msg) {
  }

  public record CreateRequest(String customerId, Customer customer) {}


  public CustomerRegistryAction(HttpClientProvider webClientProvider) {
    this.httpClient = webClientProvider.httpClientFor("customer-registry");
  }

  public Effect<Confirm> create(CreateRequest createRequest) {
    log.debug("Creating {} with id: {}", createRequest.customer, createRequest.customerId);
    // make call on customer-registry service
    var res =
      httpClient.POST("/akka/v1.0/entity/customer/" + createRequest.customerId + "/create")
        .withRequestBody(createRequest.customer)
        .responseBodyAs(Confirm.class)
        .invokeAsync()
        .thenApply(StrictResponse::body);

    return effects().asyncReply(res);
  }
}
