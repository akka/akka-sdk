package customer.api;

import akka.NotUsed;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ResponseEntity;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import customer.domain.Address;
import customer.domain.Customer;
import customer.application.CustomerEntity;
import customer.application.CustomerByEmailView;
import customer.application.CustomersByNameView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/customer")
public class CustomerEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(CustomerEndpoint.class);

    private final ComponentClient componentClient;


    public CustomerEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post("/")
    public CompletionStage<CustomerEntity.Ok> create(Customer customer) {
        logger.info("Request to create customer {}", customer);
        if (customer.name() == null || customer.name().isEmpty()) {
            throw HttpException.badRequest("Customer name must not be empty");
        }
        return componentClient.forKeyValueEntity(customer.customerId())
                .method(CustomerEntity::create)
                .invokeAsync(customer);
    }


    @Get("/{id}")
    public CompletionStage<Customer> get(String id) {
        return componentClient.forKeyValueEntity(id)
                .method(CustomerEntity::getCustomer)
                .invokeAsync();
    }


    @Put("/{id}/name")
    public CompletionStage<CustomerEntity.Ok> changeName(String id, String newName) {
        logger.info("Request to change name for customer [{}] to [{}]", id, newName);
        if (newName.isEmpty()) {
            throw HttpException.badRequest("Customer name must not be empty");
        }
        return componentClient.forKeyValueEntity(id)
                .method(CustomerEntity::changeName)
                .invokeAsync(newName);
    }

    @Get("/{id}/address")
    public CompletionStage<Address> getAddress(String id) {
        return componentClient.forKeyValueEntity(id)
            .method(CustomerEntity::getCustomer)
            .invokeAsync().thenApply(Customer::address);
    }

    @Put("/{id}/address")
    public CompletionStage<CustomerEntity.Ok> changeAddress(String id, Address newAddress) {
        logger.info("Request to change address for customer [{}] to [{}]", id, newAddress);
        return componentClient.forKeyValueEntity(id)
                .method(CustomerEntity::changeAddress)
                .invokeAsync(newAddress);
    }

    @Get("/by-email/{email}")
    public CompletionStage<Customer> getCustomerByEmail(String email) {
        return componentClient.forView()
                .method(CustomerByEmailView::getCustomer)
                .invokeAsync(email);
    }

    @Get("/by-name/{name}")
    public CompletionStage<CustomersByNameView.Customers> getCustomersByName(String name) {
        return componentClient.forView()
                .method(CustomersByNameView::getCustomers)
                .invokeAsync(name);
    }

    @Get("/by-name-csv/{name}")
    public HttpResponse getCustomersCsvByName(String name) {
        // Note: somewhat superficial, shows of streaming consumption of a view, transforming
        // each element and passing along to a streamed response
        var customerSummarySource = componentClient.forView()
            .stream(CustomersByNameView::getCustomerSummaryStream)
            .source(name);

        Source<ByteString, NotUsed> csvByteChunkStream =
            Source.single("name,email\n").concat(customerSummarySource.map(customerSummary ->
                    customerSummary.name() + "," + customerSummary.email() + "\n"
                )).map(ByteString::fromString);

        return HttpResponse.create()
            .withStatus(StatusCodes.OK)
            .withEntity(HttpEntities.create(ContentTypes.TEXT_CSV_UTF8, csvByteChunkStream));
    }


}
