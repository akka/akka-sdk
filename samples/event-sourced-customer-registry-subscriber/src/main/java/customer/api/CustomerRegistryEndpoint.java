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
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import customer.application.CustomersByNameView;
import customer.domain.CustomerEntries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  public record CreateCustomerRequest(String email, String name, Address address) { }


  public CustomerRegistryEndpoint(HttpClientProvider webClientProvider, // <1>
                                  ComponentClient componentClient) {
    this.httpClient = webClientProvider.httpClientFor("customer-registry"); // <2>
    this.componentClient = componentClient;
  }

  @Post("/{id}")
  public HttpResponse create(String id, CreateCustomerRequest createRequest) {
    log.info("Delegating customer creation to upstream service: {}", createRequest);
    if (id == null || id.isBlank())
      throw HttpException.badRequest("No id specified");

    // make call to customer-registry service
    var response = httpClient.POST("/customer/" + id) // <3>
        .withRequestBody(createRequest)
        .invoke();

    if (response.httpResponse().status() == StatusCodes.CREATED) {
      return HttpResponses.created(); // <4>
    } else {
      throw new RuntimeException("Delegate call to create upstream customer failed, response status: " + response.httpResponse().status());
    }
  }
  // end::cross-service-call[]
  
  @Get("/by-name/{name}")
  public CustomerEntries findByName(String name) {
    return componentClient.forView().method(CustomersByNameView::findByName).invoke(name);
  }
}
