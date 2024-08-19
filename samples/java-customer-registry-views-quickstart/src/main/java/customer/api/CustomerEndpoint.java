package customer.api;

import akka.platform.javasdk.annotations.http.Endpoint;
import akka.platform.javasdk.annotations.http.Get;
import akka.platform.javasdk.annotations.http.Post;
import akka.platform.javasdk.annotations.http.Put;
import akka.platform.javasdk.client.ComponentClient;
import customer.domain.Address;
import customer.domain.Customer;
import customer.domain.CustomerEntity;
import customer.view.CustomerByEmailView;
import customer.view.CustomersByNameView;

import java.util.concurrent.CompletionStage;

@Endpoint("/customer")
public class CustomerEndpoint {

    private final ComponentClient componentClient;

    public CustomerEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Post("/")
    public CompletionStage<CustomerEntity.Ok> create(Customer customer) {
        return componentClient.forKeyValueEntity(customer.customerId())
                .method(CustomerEntity::create)
                .invokeAsync(customer);
    }


    @Get("/{id}")
    public CompletionStage<Customer> create(String id) {
        return componentClient.forKeyValueEntity(id)
                .method(CustomerEntity::getCustomer)
                .invokeAsync();
    }


    @Put("/{id}/name")
    public CompletionStage<CustomerEntity.Ok> changeName(String id, String newName) {
        return componentClient.forKeyValueEntity(id)
                .method(CustomerEntity::changeName)
                .invokeAsync(newName);
    }


    @Put("/{id}/address")
    public CompletionStage<CustomerEntity.Ok> changeAddress(String id, Address newAddress) {
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
