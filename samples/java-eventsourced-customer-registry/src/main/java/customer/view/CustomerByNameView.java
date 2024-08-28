package customer.view;

// tag::class[]

import akka.javasdk.view.TableUpdater;
import customer.api.CustomerEntity;
import customer.domain.CustomerEvent;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;

@ComponentId("view_customers_by_name") // <1>
public class CustomerByNameView extends View {

  public record QueryParameters(String name) { }

  @Query("SELECT * as customers FROM customers_by_name WHERE name = :name")
  public QueryEffect<CustomersList> getCustomer(QueryParameters params) {
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomersByName extends TableUpdater<CustomerRow> {

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
// end::class[]
