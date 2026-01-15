package customer.application;

// tag::class[]

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.FromSnapshotHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import customer.domain.Customer;
import customer.domain.CustomerEntries;
import customer.domain.CustomerEntry;
import customer.domain.CustomerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "customers-by-name") // <1>
public class CustomersByNameView extends View {
  private static final Logger logger = LoggerFactory.getLogger(CustomersByNameView.class);

  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomersByNameUpdater extends TableUpdater<CustomerEntry> { // <2>

    @FromSnapshotHandler
    public Effect<CustomerEntry> onSnapshot(Customer snapshot) {
      logger.info("onSnapshot [{}]", snapshot);
       return effects()
          .updateRow(new CustomerEntry(snapshot.email(), snapshot.name(), snapshot.address()));
    }

    public Effect<CustomerEntry> onEvent(CustomerEvent event) { // <3>
      logger.info("onEvent [{}]", event);
      return switch (event) {
        case CustomerEvent.CustomerCreated created -> effects()
          .updateRow(new CustomerEntry(created.email(), created.name(), created.address()));
        case CustomerEvent.NameChanged nameChanged -> effects()
          .updateRow(rowState().withName(nameChanged.newName()));
        case CustomerEvent.AddressChanged addressChanged -> effects()
          .updateRow(rowState().withAddress(addressChanged.address()));
      };
    }
  }

  @Query("SELECT * as customers FROM customers_by_name WHERE name = :name")
  public QueryEffect<CustomerEntries> getCustomers(String name) {
    return queryResult();
  }
}
// end::class[]
