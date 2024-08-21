package customer.api;

import akka.platform.javasdk.annotations.http.Endpoint;
import akka.platform.javasdk.annotations.http.Post;
import akka.platform.javasdk.http.HttpClient;
import akka.platform.javasdk.http.HttpClientProvider;
import akka.platform.javasdk.http.StrictResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@Endpoint("/customer")
public class CustomerRegistryEndpoint {

  private Logger log = LoggerFactory.getLogger(getClass());
  private final HttpClient httpClient;

  public record Address(String street, String city) {
  }

  public record Customer(String email, String name, Address address) {
  }

  public record Confirm(String msg) {
  }

  public record CreateRequest(String customerId, Customer customer) {}


  public CustomerRegistryEndpoint(HttpClientProvider webClientProvider) {
    this.httpClient = webClientProvider.httpClientFor("customer-registry");
  }

  @Post("/create")
  public CompletionStage<Confirm> create(CreateRequest createRequest) {
    log.debug("Creating {} with id: {}", createRequest.customer, createRequest.customerId);
    // make call on customer-registry service
    return
      httpClient.POST("/akka/v1.0/entity/customer/" + createRequest.customerId + "/create")
        .withRequestBody(createRequest.customer)
        .responseBodyAs(Confirm.class)
        .invokeAsync()
        .thenApply(StrictResponse::body);
  }
}
