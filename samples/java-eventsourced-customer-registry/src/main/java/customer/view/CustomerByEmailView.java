package customer.view;

import akka.platform.javasdk.view.TableUpdater;
import customer.api.CustomerEntity;
import customer.domain.CustomerEvent;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;

@ComponentId("view_customers_by_email")
public class CustomerByEmailView extends View {

  public record QueryParameters(String email) {
  }

  @Query("SELECT * as customers FROM customers_by_email WHERE email = :email")
  public QueryEffect<CustomersList> getCustomer(QueryParameters params) {
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomersByEmail extends TableUpdater<CustomerRow> {

    public Effect<CustomerRow> onEvent(CustomerEvent event) {
      return switch (event) {
        case CustomerEvent.CustomerCreated created ->
            effects().updateRow(new CustomerRow(created.email(), created.name(), created.address()));

        case CustomerEvent.NameChanged nameChanged ->
            effects().updateRow(rowState().withName(nameChanged.newName()));

        case CustomerEvent.AddressChanged addressChanged ->
            effects().updateRow(rowState().withAddress(addressChanged.address()));
      };
    }
  }
}
