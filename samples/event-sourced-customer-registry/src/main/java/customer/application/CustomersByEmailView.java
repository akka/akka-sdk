package customer.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.CustomerEvent;
import customer.domain.CustomerEntry;
import customer.domain.CustomerEntries;

@ComponentId("customers-by-email")
public class CustomersByEmailView extends View {

  @Query("SELECT * as customers FROM customers_by_email WHERE email = :email")
  public QueryEffect<CustomerEntries> getCustomers(String email) {
    return queryResult();
  }

  @Query(value = "SELECT * FROM customers_by_email WHERE email = :email", streamUpdates = true)
  public QueryStreamEffect<CustomerEntry> getCustomersStream(String email) {
    return queryStreamResult();
  }

  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomersByEmail extends TableUpdater<CustomerEntry> {

    public Effect<CustomerEntry> onEvent(CustomerEvent event) { // <1>
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
}
