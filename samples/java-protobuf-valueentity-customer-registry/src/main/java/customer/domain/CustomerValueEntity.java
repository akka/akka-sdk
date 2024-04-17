/* This code was generated by Kalix tooling.
 * As long as this file exists it will not be re-generated.
 * You are free to make changes to this file.
 */
package customer.domain;

import kalix.javasdk.valueentity.ValueEntityContext;
import com.google.protobuf.Empty;
import customer.api.CustomerApi;

public class CustomerValueEntity extends AbstractCustomerValueEntity {
  @SuppressWarnings("unused")
  private final String entityId;

  public CustomerValueEntity(ValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public Effect<CustomerApi.Customer> getCustomer(
      CustomerDomain.CustomerState currentState, CustomerApi.GetCustomerRequest request) {
    return effects().reply(convertToApi(currentState));
  }

  @Override
  public Effect<Empty> create(
      CustomerDomain.CustomerState currentState, CustomerApi.Customer customer) {
    CustomerDomain.CustomerState state = convertToDomain(customer);
    return effects().updateState(state).thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<Empty> changeName(
      CustomerDomain.CustomerState currentState, CustomerApi.ChangeNameRequest request) {
    CustomerDomain.CustomerState updatedState =
        currentState.toBuilder().setName(request.getNewName()).build();
    return effects().updateState(updatedState).thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<Empty> changeAddress(
      CustomerDomain.CustomerState currentState, CustomerApi.ChangeAddressRequest request) {
    CustomerDomain.CustomerState updatedState =
        currentState
            .toBuilder()
            .setAddress(convertAddressToDomain(request.getNewAddress()))
            .build();
    return effects().updateState(updatedState).thenReply(Empty.getDefaultInstance());
  }

  @Override
  public Effect<Empty> delete(CustomerDomain.CustomerState currentState, CustomerApi.DeleteCustomerRequest deleteCustomerRequest) {
    return effects().deleteEntity().thenReply(Empty.getDefaultInstance());
  }

  private CustomerApi.Customer convertToApi(CustomerDomain.CustomerState state) {
    CustomerApi.Address address = CustomerApi.Address.getDefaultInstance();
    if (state.hasAddress()) {
      address =
          CustomerApi.Address.newBuilder()
              .setStreet(state.getAddress().getStreet())
              .setCity(state.getAddress().getCity())
              .build();
    }
    return CustomerApi.Customer.newBuilder()
        .setCustomerId(state.getCustomerId())
        .setEmail(state.getEmail())
        .setName(state.getName())
        .setAddress(address)
        .build();
  }

  private CustomerDomain.CustomerState convertToDomain(CustomerApi.Customer customer) {
    CustomerDomain.Address address = CustomerDomain.Address.getDefaultInstance();
    if (customer.hasAddress()) {
      address = convertAddressToDomain(customer.getAddress());
    }
    return CustomerDomain.CustomerState.newBuilder()
        .setCustomerId(customer.getCustomerId())
        .setEmail(customer.getEmail())
        .setName(customer.getName())
        .setAddress(address)
        .build();
  }

  private CustomerDomain.Address convertAddressToDomain(CustomerApi.Address address) {
    return CustomerDomain.Address.newBuilder()
        .setStreet(address.getStreet())
        .setCity(address.getCity())
        .build();
  }

  @Override
  public CustomerDomain.CustomerState emptyState() {
    return CustomerDomain.CustomerState.getDefaultInstance();
  }
}
