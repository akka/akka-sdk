package customer.application;

import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;
import akka.javasdk.view.TableUpdater;
import customer.domain.CustomerEntry;
import customer.domain.CustomerEntries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::view[]
@ComponentId("customers-by-name")
public class CustomersByNameView extends View {
  // end::view[]
  private static final Logger logger = LoggerFactory.getLogger(CustomersByNameView.class);
  // tag::view[]

  @Consume.FromServiceStream( // <1>
      service = "customer-registry", // <2>
      id = "customer_events", // <3>
      consumerGroup = "customer-by-name-view" // <4>
  )
  public static class CustomersByNameUpdater extends TableUpdater<CustomerEntry> {

    public Effect<CustomerEntry> onEvent( // <5>
                                          CustomerPublicEvent.Created created) {
      // end::view[]
      logger.info("Received: {}", created);
      // tag::view[]
      var id = updateContext().eventSubject().get();
      return effects().updateRow(
          new CustomerEntry(id, created.email(), created.name()));
    }

    public Effect<CustomerEntry> onEvent(
        CustomerPublicEvent.NameChanged nameChanged) {
      // end::view[]
      logger.info("Received: {}", nameChanged);
      // tag::view[]
      var updated = rowState().withName(nameChanged.newName());
      return effects().updateRow(updated);
    }
  }

  @Query("SELECT * as customers FROM customers_by_name WHERE name = :name")
  public QueryEffect<CustomerEntries> findByName(String name) {
    return queryResult();
  }

}
// end::view[]
