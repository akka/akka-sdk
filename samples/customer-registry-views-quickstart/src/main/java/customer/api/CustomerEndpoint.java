package customer.api;

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
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import customer.domain.Address;
import customer.domain.Customer;
import customer.application.CustomerEntity;
import customer.application.CustomersByEmailView;
import customer.application.CustomersByNameView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
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

    @Post("/{id}")
    public CompletionStage<CustomerEntity.Ok> create(String id, Customer customer) {
        logger.info("Request to create customer {}", customer);
        if (customer.name() == null || customer.name().isEmpty()) {
            throw HttpException.badRequest("Customer name must not be empty");
        }
        return componentClient.forKeyValueEntity(id)
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
    public CompletionStage<CustomersByEmailView.Customers> getCustomerByEmail(String email) {
        return componentClient.forView()
                .method(CustomersByEmailView::getCustomer)
                .invokeAsync(email);
    }

    @Get("/by-name/{name}")
    public CompletionStage<CustomersByNameView.Customers> getCustomersByName(String name) {
        return componentClient.forView()
                .method(CustomersByNameView::getCustomers)
                .invokeAsync(name);
    }

    @Get("/first-by-name/{name}")
    public CompletionStage<CustomersByNameView.CustomerSummary> getOneCustomerByName(String name) {
        return componentClient.forView()
            .method(CustomersByNameView::getFirstCustomerSummary)
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
            Source.single("id,name,email\n").concat(customerSummarySource.map(customerSummary ->
                    customerSummary.customerId() + "," + customerSummary.name() + "," + customerSummary.email() + "\n"
                )).map(ByteString::fromString);

        return HttpResponse.create()
            .withStatus(StatusCodes.OK)
            .withEntity(HttpEntities.create(ContentTypes.TEXT_CSV_UTF8, csvByteChunkStream));
    }

    private final static ContentType TEXT_EVENT_STREAM = ContentTypes.parse("text/event-stream");

    @Get("/by-name-sse/{name}")
    public HttpResponse continousByNameServerSentEvents(String name) {
        // view will keep stream going, toggled with streamUpdates = true on the query
        var customerSummarySource = componentClient.forView()
            .stream(CustomersByNameView::continuousGetCustomerSummaryStream)
            .source(name);

        final var eventPrefix = ByteString.fromString("data: ");
        final var eventEnd = ByteString.fromString("\n\n");
        // Server sent events
        // https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events#event_stream_format
        Source<ByteString, NotUsed> sseCustomerSummaryStream =
            customerSummarySource.map(customerSummary ->
                eventPrefix.concat(JsonSupport.encodeToAkkaByteString(customerSummary)).concat(eventEnd)
            );

        return HttpResponse.create()
            .withStatus(StatusCodes.OK)
            .withHeaders(Arrays.asList(
                CacheControl.create(CacheDirectives.NO_CACHE),
                Connection.create("keep-alive")
            ))
            .withEntity(HttpEntities.create(TEXT_EVENT_STREAM, sseCustomerSummaryStream));
    }


}
