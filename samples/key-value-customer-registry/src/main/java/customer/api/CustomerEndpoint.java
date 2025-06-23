package customer.api;

import akka.NotUsed;
import akka.actor.Cancellable;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Patch;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import customer.application.CustomerEntity;
import customer.application.CustomerList;
import customer.application.CustomerSummaryByName;
import customer.application.CustomersByCity;
import customer.application.CustomersByEmail;
import customer.application.CustomersByName;
import customer.application.CustomersListByName;
import customer.domain.Address;
import customer.domain.Customer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

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

  public record ApiCustomer(String id, String name, String email, String city, String street) { }

  @Post("/{customerId}")
  public HttpResponse create(String customerId, ApiCustomer request) {
    var customer = new Customer(request.email(), request.name(), new Address(request.street(), request.city()));

    componentClient.forKeyValueEntity(customerId)
        .method(CustomerEntity::create)
        .invoke(customer);

    return HttpResponses.created();
  }

  @Get("/{customerId}")
  public  ApiCustomer get(String customerId) {
    var customer = componentClient.forKeyValueEntity(customerId)
        .method(CustomerEntity::getCustomer)
        .invoke();
    return toApiCustomer(customerId, customer);
  }

  @Patch("/{id}/name/{newName}")
  public HttpResponse changeName(String id, String newName) {
    if (newName.isEmpty()) {
      throw HttpException.badRequest("Customer name must not be empty");
    }
    componentClient.forKeyValueEntity(id)
        .method(CustomerEntity::changeName)
        .invoke(newName);

    return HttpResponses.ok();
  }

  @Get("/by-email/{email}")
  public CustomersByEmail.Customers getCustomerByEmail(String email) {
    return componentClient.forView()
        .method(CustomersByEmail::getCustomer)
        .invoke(email);
  }

  @Get("/first-by-name/{name}")
  public CustomersByName.CustomerSummary getOneCustomerByName(String name) {
    return componentClient.forView()
        .method(CustomersByName::getFirstCustomerSummary)
        .invoke(name);
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

  // tag::sse-view-updates[]
  @Get("/by-city-sse/{cityName}")
  public HttpResponse continousByCityNameServerSentEvents(String cityName) {
    // view will keep stream going, toggled with streamUpdates = true on the query
    Source<Customer, NotUsed> customerSummarySource = componentClient.forView() // <1>
        .stream(CustomersByCity::continuousCustomersInCity)
        .source(cityName);

    return HttpResponses.serverSentEvents(customerSummarySource); // <2>
  }
  // end::sse-view-updates[]

  // tag::sse-customer-changes[]
  private record CustomerStreamState(Optional<Customer> customer, boolean isSame) {}

  @Get("/stream-customer-changes/{customerId}")
  public HttpResponse streamCustomerChanges(String customerId) {
    Source<Customer, Cancellable> stateEvery5Seconds =
        // stream of ticks, one immediately, then one every five seconds
        Source.tick(Duration.ZERO, Duration.ofSeconds(5), "tick") // <1>
          // for each tick, request the entity state
          .mapAsync(1, __ ->
            // Note: not safe to turn this into `.invoke()` in a stream `.map()`
            componentClient.forKeyValueEntity(customerId)
                .method(CustomerEntity::getCustomer)
                .invokeAsync().handle((Customer customer, Throwable error) -> {
                  if (error == null) {
                    return Optional.of(customer);
                  } else if (error instanceof IllegalArgumentException) {
                    // calling getCustomer throws IllegalArgument if the customer does not exist
                    // we want the stream to continue polling in that case, so turn it into an empty optional
                    return Optional.<Customer>empty();
                  } else {
                    throw new RuntimeException("Unexpected error polling customer state", error);
                  }
                })
          )
          // then filter out the empty optionals and return the actual customer states for nonempty
          // so that the stream contains only Customer elements
          .filter(Optional::isPresent).map(Optional::get);

    // deduplicate, so that we don't emit if the state did not change from last time
    Source<Customer, Cancellable> streamOfChanges = // <2>
        stateEvery5Seconds.scan(new CustomerStreamState(Optional.empty(), true),
          (state, newCustomer) ->
            new CustomerStreamState(Optional.of(newCustomer), state.customer.isPresent() && state.customer.get().equals(newCustomer))
        ).filterNot(state -> state.isSame || state.customer.isEmpty())
        .map(state -> state.customer.get());

    // now turn each changed internal state representation into public API representation,
    // just like get endpoint above
    Source<ApiCustomer, Cancellable> streamOfChangesAsApiType = // <3>
        streamOfChanges.map(customer -> toApiCustomer(customerId, customer));

    // turn into server sent event response
    return HttpResponses.serverSentEvents(streamOfChangesAsApiType); // <4>
  }
  // end::sse-customer-changes[]

  @Get("/{id}/address")
  public Address getAddress(String id) {
    return componentClient.forKeyValueEntity(id)
        .method(CustomerEntity::getCustomer)
        .invoke().address();
  }

  @Patch("/{id}/address")
  public HttpResponse changeAddress(String id, Address newAddress) {
    componentClient.forKeyValueEntity(id)
        .method(CustomerEntity::changeAddress)
        .invoke(newAddress);
    return HttpResponses.ok();
  }

  @Get("/by-name/{name}")
  public CustomerList getByName(String name) {
    return componentClient.forView()
        .method(CustomersListByName::getCustomers)
        .invoke(name);
  }

  public record ByNameSummary(String name) {}
  @Post("/by-name-summary")
  public CustomerSummaryByName.CustomerSummary getSummaryByName(ByNameSummary req) {
    return componentClient.forView()
        .method(CustomerSummaryByName::getCustomer)
        .invoke(req.name());
  }

  public record ByCityRequest(List<String> cities) {}

  @Post("/by-city")
  public CustomerList getByCity(ByCityRequest req) {
    return componentClient.forView()
        .method(CustomersByCity::getCustomers)
        .invoke(req.cities());
  }

  @Delete("/{customerId}")
  public HttpResponse delete(String customerId) {
    componentClient.forKeyValueEntity(customerId)
        .method(CustomerEntity::delete)
        .invoke();
    return HttpResponses.noContent();
  }

  private ApiCustomer toApiCustomer(String customerId, Customer customer) {
    return new ApiCustomer(customerId, customer.name(), customer.email(), customer.address().city(), customer.address().street());
  }

}
