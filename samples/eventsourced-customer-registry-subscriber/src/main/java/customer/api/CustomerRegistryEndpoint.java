package customer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.HttpResponses;
import customer.application.CustomersByNameView;
import customer.domain.CustomersList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
// tag::cross-service-call[]
@HttpEndpoint("/customer")
public class CustomerRegistryEndpoint {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final HttpClient httpClient;
  private final ComponentClient componentClient;

  public record Address(String street, String city) { }

  public record CreateCustomerRequest(String id, String email, String name, Address address){ }


  public CustomerRegistryEndpoint(HttpClientProvider webClientProvider, // <1>
                                  ComponentClient componentClient) {
    this.httpClient = webClientProvider.httpClientFor("customer-registry"); // <2>
    this.componentClient = componentClient;
  }

  @Post("/create")
  public CompletionStage<HttpResponse> create(CreateCustomerRequest createRequest) {
    log.info("Delegating customer creation to upstream service: {}", createRequest);
    // make call on customer-registry service
    return
      httpClient.POST("/customer") // <3>
        .withRequestBody(createRequest)
        .invokeAsync() // <4>
        .thenApply(response -> { // <5>
          if (response.httpResponse().status() == StatusCodes.CREATED) {
            return HttpResponses.created();
          } else {
            throw new RuntimeException("Delegate call to create upstream customer failed, response status: " + response.httpResponse().status());
          }
        });
  }
  // end::cross-service-call[]
  
  @Get("/by_name/{name}")
  public CompletionStage<CustomersList> findByName(String name) {
    return componentClient.forView().method(CustomersByNameView::findByName).invokeAsync(name);
  }
}
