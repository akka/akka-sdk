package customer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@HttpEndpoint("/customer")
public class CustomerRegistryEndpoint {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final HttpClient httpClient;

  public record Address(String street, String city) {
  }

  public record CreateCustomerRequest(String id, String email, String name, Address address){
  }


  public CustomerRegistryEndpoint(HttpClientProvider webClientProvider) {
    this.httpClient = webClientProvider.httpClientFor("customer-registry");
  }

  @Post("/create")
  public CompletionStage<HttpResponse> create(CreateCustomerRequest createRequest) {
    log.debug("Creating customer: {}", createRequest);
    // make call on customer-registry service
    return
      httpClient.POST("/customer")
        .withRequestBody(createRequest)
        .invokeAsync()
        .thenApply(__ -> HttpResponses.created());
  }
}
