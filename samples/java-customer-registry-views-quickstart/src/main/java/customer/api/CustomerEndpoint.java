package customer.api;

import akka.platform.javasdk.annotations.http.Endpoint;
import akka.platform.javasdk.annotations.http.Get;
import akka.platform.javasdk.annotations.http.Post;
import akka.platform.javasdk.annotations.http.Put;
import akka.platform.javasdk.client.ComponentClient;
import akka.platform.javasdk.http.HttpException;
import customer.domain.Address;
import customer.domain.Customer;
import customer.application.CustomerEntity;
import customer.application.CustomerByEmailView;
import customer.application.CustomersByNameView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@Endpoint("/customer")
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
                .invokeAsync(new CustomerByEmailView.QueryParameters(email));
    }

    @Get("/by-name/{name}")
    public CompletionStage<CustomersByNameView.Customers> getCustomersByName(String name) {
        return componentClient.forView()
                .method(CustomersByNameView::getCustomers)
                .invokeAsync(new CustomersByNameView.QueryParameters(name));
    }


}
