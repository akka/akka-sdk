package customer.view;

import customer.api.CustomerEntity;
import customer.domain.CustomerEvent;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

@ViewId("view_customers_by_email")
@Table("customers_by_email")
public class CustomerByEmailView extends View<CustomerView> {

  public record QueryParameters(String email) {
  }

  @Query("SELECT * as customers FROM customers_by_email WHERE email = :email")
  public CustomersList getCustomer(QueryParameters params) {
    return null;
  }

  @Subscribe.EventSourcedEntity(CustomerEntity.class)
  public Effect<CustomerView> onEvent(CustomerEvent event) {
    return switch (event) {
      case CustomerEvent.CustomerCreated created ->
        effects().updateState(new CustomerView(created.email(), created.name(), created.address()));

      case CustomerEvent.NameChanged nameChanged ->
        effects().updateState(viewState().withName(nameChanged.newName()));

      case CustomerEvent.AddressChanged addressChanged ->
        effects().updateState(viewState().withAddress(addressChanged.address()));
    };
  }
}
