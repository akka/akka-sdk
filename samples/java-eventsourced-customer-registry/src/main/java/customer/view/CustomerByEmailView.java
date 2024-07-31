package customer.view;

import customer.api.CustomerEntity;
import customer.domain.CustomerEvent;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;

@ComponentId("view_customers_by_email")
public class CustomerByEmailView extends View<CustomerView> {

  public record QueryParameters(String email) {
  }

  @Query("SELECT * as customers FROM customers_by_email WHERE email = :email")
  public CustomersList getCustomer(QueryParameters params) {
    return null;
  }

  @Consume.FromEventSourcedEntity(CustomerEntity.class)
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
