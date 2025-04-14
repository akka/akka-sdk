package customer.application;

// tag::class[]

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.CustomerEvent;
import customer.domain.CustomerEntry;
import customer.domain.CustomerEntries;

@ComponentId("customers-by-name") // <1>
public class CustomersByNameView extends View {

  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomersByNameUpdater extends TableUpdater<CustomerEntry> { // <2>

    public Effect<CustomerEntry> onEvent(CustomerEvent event) { // <3>
      return switch (event) {
        case CustomerEvent.CustomerCreated created ->
            effects().updateRow(new CustomerEntry(created.email(), created.name(), created.address()));

        case CustomerEvent.NameChanged nameChanged ->
            effects().updateRow(rowState().withName(nameChanged.newName()));

        case CustomerEvent.AddressChanged addressChanged ->
            effects().updateRow(rowState().withAddress(addressChanged.address()));
      };
    }
  }

  @Query("SELECT * as customers FROM customers_by_name WHERE name = :name")
  public QueryEffect<CustomerEntries> getCustomers(String name) {
    return queryResult();
  }

}
// end::class[]
