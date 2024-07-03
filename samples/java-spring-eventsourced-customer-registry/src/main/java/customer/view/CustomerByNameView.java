package customer.view;

// tag::class[]

import customer.api.CustomerEntity;
import customer.domain.CustomerEvent;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Consume;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

@ViewId("view_customers_by_name") // <1>
@Table("customers_by_name")
public class CustomerByNameView extends View<CustomerView> {

  public record QueryParameters(String name) {
  }

  @Query("SELECT * as customers FROM customers_by_name WHERE name = :name")
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
// end::class[]
