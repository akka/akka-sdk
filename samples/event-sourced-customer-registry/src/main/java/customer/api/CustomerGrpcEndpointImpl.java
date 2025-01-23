package customer.api;

import akka.grpc.GrpcServiceException;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.client.ComponentClient;
import customer.api.proto.*;
import customer.application.CustomerByEmailView;
import customer.application.CustomerByNameView;
import customer.application.CustomerEntity;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@GrpcEndpoint
public class CustomerGrpcEndpointImpl implements CustomerGrpcEndpoint {

  private static final Logger log = LoggerFactory.getLogger(CustomerGrpcEndpointImpl.class);

  private final ComponentClient componentClient;

  public CustomerGrpcEndpointImpl(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public CompletionStage<CreateCustomerResponse> createCustomer(CreateCustomerRequest in) {
    log.info("gRPC request to create customer: {}", in);
    if (in.getCustomerId().isBlank())
      throw new IllegalArgumentException("Customer id must not be empty");

    return componentClient.forEventSourcedEntity(in.getCustomerId())
        .method(CustomerEntity::create)
        .invokeAsync(apiToDomain(in.getCustomer()))
        .thenApply(__ -> CreateCustomerResponse.getDefaultInstance());
  }

  @Override
  public CompletionStage<Customer> getCustomer(GetCustomerRequest in) {
    return componentClient.forEventSourcedEntity(in.getCustomerId())
        .method(CustomerEntity::getCustomer)
        .invokeAsync()
        .thenApply(this::domainToApi)
        .exceptionally(ex -> {
          if (ex.getMessage().contains("No customer found for id")) throw new GrpcServiceException(Status.NOT_FOUND);
          else throw new RuntimeException(ex);
        });
  }

  @Override
  public CompletionStage<ChangeNameResponse> changeName(ChangeNameRequest in) {
    log.info("gRPC request to change customer [{}] name: {}", in.getCustomerId(), in.getNewName());
    return componentClient.forEventSourcedEntity(in.getCustomerId())
        .method(CustomerEntity::changeName)
        .invokeAsync(in.getNewName())
        .thenApply(__ -> ChangeNameResponse.getDefaultInstance());
  }

  @Override
  public CompletionStage<ChangeAddressResponse> changeAddress(ChangeAddressRequest in) {
    log.info("gRPC request to change customer [{}] address: {}", in.getCustomerId(), in.getNewAddress());
    return componentClient.forEventSourcedEntity(in.getCustomerId())
        .method(CustomerEntity::changeAddress)
        .invokeAsync(apiToDomain(in.getNewAddress()))
        .thenApply(__ -> ChangeAddressResponse.getDefaultInstance());
  }

  // The two methods below are not necessarily realistic since we have the full result in one response,
  // but provides examples of streaming a response
  @Override
  public CompletionStage<CustomerList> customerByName(CustomerByNameRequest in) {
    return componentClient.forView()
        .method(CustomerByNameView::getCustomers)
        .invokeAsync(name)
        .thenApply(viewCustomerList -> {
          var apiCustomers = viewCustomerList.customers().stream().map(this::domainToApi).toList();

          return CustomerList.newBuilder().addAllCustomers(apiCustomers).build();
        });
  }

  @Override
  public CompletionStage<CustomerList> customerByEmail(CustomerByEmailRequest in) {
    return componentClient.forView()
        .method(CustomerByEmailView::getCustomers)
        .invokeAsync(name)
        .thenApply(viewCustomerList -> {
          var apiCustomers = viewCustomerList.customers().stream().map(this::domainToApi).toList();

          return CustomerList.newBuilder().addAllCustomers(apiCustomers).build();
        });
  }


  // Conversions between the public gRPC API protobuf messages and the internal
  // Java domain classes.
  private customer.domain.Customer apiToDomain(Customer protoCustomer) {
    return new customer.domain.Customer(
        protoCustomer.getEmail(),
        protoCustomer.getName(),
        apiToDomain(protoCustomer.getAddress())
    );
  }

  private customer.domain.Address apiToDomain(Address protoAddress) {
    if (protoAddress == null) return null;
    else {
      return new customer.domain.Address(
          protoAddress.getStreet(),
          protoAddress.getCity()
      );
    }
  }

  private Customer domainToApi(customer.domain.Customer domainCustomer) {
    return Customer.newBuilder()
        .setName(domainCustomer.name())
        .setEmail(domainCustomer.email())
        .setAddress(domainToApi(domainCustomer.address()))
        .build();
  }

  private Address domainToApi(customer.domain.Address domainAddress) {
    if (domainAddress == null) return null;
    else {
      return Address.newBuilder()
          .setCity(domainAddress.city())
          .setStreet(domainAddress.street())
          .build();
    }
  }

  private Customer domainToApi(customer.domain.CustomerRow domainRow) {
    return Customer.newBuilder()
        .setName(domainRow.name())
        .setEmail(domainRow.email())
        .setAddress(domainToApi(domainRow.address()))
        .build();
  }
}
