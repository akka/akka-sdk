package customer.api;

import akka.Done;
import akka.NotUsed;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.CacheControl;
import akka.http.javadsl.model.headers.CacheDirectives;
import akka.http.javadsl.model.headers.Connection;
import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import customer.application.*;
import customer.domain.Address;
import customer.domain.Customer;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
// Note: Called in customer-registry-subscriber integration test so must be allowed also from the other service or test will fail
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/customer")
public class CustomerEndpoint {

  private final ComponentClient componentClient;

  public CustomerEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record CustomerRequest(String id, String name, String email, String city, String street) {}

  @Post("/{customerId}")
  public CompletionStage<HttpResponse> create(String customerId, CustomerRequest request) {
    Customer c = new Customer(request.email(), request.name(), new Address(request.street(), request.city()));

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

  @Patch("/{id}/name/{newName}")
  public CompletionStage<HttpResponse> changeName(String id, String newName) {
    if (newName.isEmpty()) {
      throw HttpException.badRequest("Customer name must not be empty");
    }
    return componentClient.forKeyValueEntity(id)
        .method(CustomerEntity::changeName)
        .invokeAsync(newName)
        .thenApply(__ -> HttpResponses.ok());
  }

  @Get("/by-email/{email}")
  public CompletionStage<CustomersByEmail.Customers> getCustomerByEmail(String email) {
    return componentClient.forView()
        .method(CustomersByEmail::getCustomer)
        .invokeAsync(email);
  }

  @Get("/first-by-name/{name}")
  public CompletionStage<CustomersByName.CustomerSummary> getOneCustomerByName(String name) {
    return componentClient.forView()
        .method(CustomersByName::getFirstCustomerSummary)
        .invokeAsync(name);
  }

  @Get("/by-name-csv/{name}")
  public HttpResponse getCustomersCsvByName(String name) {
    // Note: somewhat superficial, shows of streaming consumption of a view, transforming
    // each element and passing along to a streamed response
    var customerSummarySource = componentClient.forView()
        .stream(CustomersByName::getCustomerSummaryStream)
        .source(name);

    Source<ByteString, NotUsed> csvByteChunkStream =
        Source.single("id,name,email\n").concat(customerSummarySource.map(customerSummary ->
            customerSummary.customerId() + "," + customerSummary.name() + "," + customerSummary.email() + "\n"
        )).map(ByteString::fromString);

    return HttpResponse.create()
        .withStatus(StatusCodes.OK)
        .withEntity(HttpEntities.create(ContentTypes.TEXT_CSV_UTF8, csvByteChunkStream));
  }

  @Get("/by-name-sse/{name}")
  public HttpResponse continousByNameServerSentEvents(String name) {
    // view will keep stream going, toggled with streamUpdates = true on the query
    var customerSummarySource = componentClient.forView()
        .stream(CustomersByName::continuousGetCustomerSummaryStream)
        .source(name);

    return HttpResponses.serverSentEvents(customerSummarySource);
  }

  @Get("/{id}/address")
  public CompletionStage<Address> getAddress(String id) {
    return componentClient.forKeyValueEntity(id)
        .method(CustomerEntity::getCustomer)
        .invokeAsync().thenApply(Customer::address);
  }

  @Patch("/{id}/address")
  public CompletionStage<HttpResponse> changeAddress(String id, Address newAddress) {
    return componentClient.forKeyValueEntity(id)
        .method(CustomerEntity::changeAddress)
        .invokeAsync(newAddress)
        .thenApply(__ -> HttpResponses.ok());
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
