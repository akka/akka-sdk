package customer.application;

// tag::class[]

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.CustomerEvent;
import customer.domain.CustomerRow;
import customer.domain.CustomersList;

@ComponentId("view_customers_by_name") // <1>
public class CustomerByNameView extends View {

  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomersByName extends TableUpdater<CustomerRow> { // <2>

    public Effect<CustomerRow> onEvent(CustomerEvent event) { // <3>
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

  @Query("SELECT * as customers FROM customers_by_name WHERE name = :name")
  public QueryEffect<CustomersList> getCustomers(String name) {
    return queryResult();
  }

}
// end::class[]
